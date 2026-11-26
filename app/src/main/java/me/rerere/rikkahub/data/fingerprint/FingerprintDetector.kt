package me.rerere.rikkahub.data.fingerprint

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Number-fingerprint scoring core for detector v2.
 *
 * A language model asked for a few hundred "random" integers between 1 and 355 produces a
 * distribution that is characteristic of the model family. This file turns three such answers into
 * a ranked list of candidate models with closed-set probabilities.
 *
 * Inputs are supplied by the caller only; nothing is bundled and nothing is fetched:
 *  * the reference bank JSON (model labels and response counts) — [ReferenceBank.decode]
 *  * the frozen detector blob `shared_detector.bin` — [SharedDetector.decode]
 *
 * The blob layout is: u32 little-endian header length, UTF-8 JSON header padded to a 4-byte
 * boundary, then float32 values stored *byte-planar* (all first bytes, then all second bytes, ...)
 * and little-endian. Inside the header every numeric array is written as `{"$f32":offset,"n":len}`
 * and resolved against those floats.
 *
 * The scoring maths mirrors the reference implementation exactly (same parser, same feature
 * blocks, same nuisance projection, same ranker blend, same temperature calibration); see
 * [FingerprintProtocol] for the constants that must stay in sync with a given detector blob.
 */
object FingerprintProtocol {
    const val VALUE_MIN = 1
    const val VALUE_MAX = 355
    const val DIMENSION = VALUE_MAX - VALUE_MIN + 1
    const val ALPHA = 0.5

    /** Answers the protocol asks for; fewer answers only give an uncalibrated partial ranking. */
    const val ANSWER_COUNT = 3

    /** An answer needs this many parsed values, or 55% of the requested count, whichever is larger. */
    const val MINIMUM_VALID_NUMBERS = 80
    const val MINIMUM_VALID_SHARE = 0.55

    const val DETECTOR_SCHEMA = "shared-detector-v2"
    const val CONFIDENCE_SCHEMA = "shared-confidence-v2"
    const val CONFIDENCE_METHOD = "ranking-temperature"
    const val PARTIAL_METHOD = "shared-ranker-partial-v1"

    /** Ordered-block feature: four position blocks of 16 value bins plus ten final-digit bins. */
    const val ORDERED_BLOCK_DIMENSION = 4 * 16 + 10
    const val POSITION_KERNEL_DIMENSION = 34 * 3
    const val SEQUENCE_DIMENSION = 220
    const val POSITIONAL_DIMENSION =
        DIMENSION + ORDERED_BLOCK_DIMENSION + POSITION_KERNEL_DIMENSION + SEQUENCE_DIMENSION
    const val COMBINED_DIMENSION = DIMENSION + ORDERED_BLOCK_DIMENSION

    /** The LDA head is fitted on the first 128 values of an answer. */
    const val PREFILTER_LENGTH = 128

    /**
     * Adjacent-pair limits: 99.5% quantiles over reference three-answer groups. Answers outside all
     * three limits look generated (sorted, shuffled, programmatic) rather than model-written.
     */
    const val SEQUENCE_AUTOCORRELATION_LIMIT = -0.23
    const val SEQUENCE_SMALL_STEP_SHARE_LIMIT = 0.05
    const val SEQUENCE_NEARBY_PAIR_SHARE_LIMIT = 0.07
}

/** One challenge prompt plus the number of integers it asks for. */
data class FingerprintChallenge(
    val id: String,
    val expectedCount: Int,
    val prompt: String,
)

/** One answer text together with the count that was requested for it. */
data class FingerprintOutput(
    val text: String,
    val expectedCount: Int,
)

data class FingerprintCandidate(
    val modelId: String,
    val displayName: String,
    val family: String,
    val familyName: String,
    val score: Double,
    val probability: Double?,
)

data class FingerprintOutputDiagnostic(
    val index: Int,
    val parsedNumbers: Int,
    val minimumNumbers: Int,
    val accepted: Boolean,
    val rawOutOfRangeCount: Int,
)

data class FingerprintSequenceShape(
    val autocorrelation: Double,
    val smallStepShare: Double,
    val modelLike: Boolean,
)

/** Result shape of a scoring run. Mirrors the reference `decision` values where they exist. */
enum class FingerprintDecision {
    /** Three valid answers, ranked. Probabilities are present only when calibration matched. */
    RANKED,

    /** One or two valid answers, ranked without confidence (`partial` upstream). */
    PARTIAL,

    /** Not enough valid answers to rank (`unscorable` upstream). */
    UNSCORABLE,

    /** The answers do not look model-written; no ranking was produced (`not_model` upstream). */
    NOT_LANGUAGE_MODEL,
}

enum class FingerprintProbabilityStatus {
    /** Probabilities are closed-set probabilities over the reference bank only. */
    REFERENCE_CALIBRATED,
    UNAVAILABLE,
}

data class FingerprintCalibration(
    val method: String,
    val run: String?,
    val tau: Double,
    val sha256: String?,
)

data class FingerprintAnalysis(
    val decision: FingerprintDecision,
    val results: List<FingerprintCandidate>,
    val prediction: FingerprintCandidate?,
    val probability: Double?,
    val probabilityStatus: FingerprintProbabilityStatus,
    val usedOutputs: Int,
    val diagnostics: List<FingerprintOutputDiagnostic>,
    val sequence: FingerprintSequenceShape?,
    val method: String,
    val baseSha256: String,
    val calibration: FingerprintCalibration?,
    val evidenceLabel: String,
    val evidenceReason: String,
)

/** The imported bank does not describe the same reference set as the imported detector. */
class FingerprintBankMismatchException(val reasons: List<String>) :
    IllegalStateException("The reference bank does not match the detector: ${reasons.joinToString("; ")}")

/** The detector blob or the bank JSON is malformed, truncated or unsupported. */
class FingerprintFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** Scoring produced non-finite numbers, which means the detector blob is damaged. */
class FingerprintScoringException(message: String) : IllegalStateException(message)

/** One model label from the imported reference bank. */
data class FingerprintBankModel(
    val id: String,
    val displayName: String,
    val family: String,
    val familyName: String,
    val responseCount: Int,
    val validNumberCount: Int,
)

@Serializable
internal data class FingerprintBankJson(
    val schema: String = "",
    val built_at: String = "",
    val reference_sha256: String? = null,
    val models: List<FingerprintBankModelJson> = emptyList(),
)

@Serializable
internal data class FingerprintBankModelJson(
    val id: String = "",
    val display_name: String = "",
    val family: String = "",
    val family_name: String = "",
    val response_count: Int = 0,
    val valid_number_count: Int = 0,
)

/**
 * Reference bank as imported by the user. Only labels and bookkeeping fields are kept; the heavy
 * per-model distributions are ignored because detector v2 scores from the frozen blob.
 */
class ReferenceBank internal constructor(
    val schema: String,
    val builtAt: String,
    val referenceSha256: String?,
    val models: List<FingerprintBankModel>,
) {
    companion object {
        private const val MAX_MODELS = 1024
        private const val BANK_SCHEMA = "robust-number-fingerprint-bank"
        private val json = Json { ignoreUnknownKeys = true }

        @OptIn(ExperimentalSerializationApi::class)
        fun decode(bytes: ByteArray): ReferenceBank = decodeBank {
            json.decodeFromStream(FingerprintBankJson.serializer(), bytes.inputStream())
        }

        fun decode(text: String): ReferenceBank = decodeBank {
            json.decodeFromString(FingerprintBankJson.serializer(), text)
        }

        private inline fun decodeBank(read: () -> FingerprintBankJson): ReferenceBank {
            val parsed = try {
                read()
            } catch (error: Exception) {
                throw FingerprintFormatException("Reference bank JSON could not be parsed.", error)
            }
            if (parsed.schema != BANK_SCHEMA || parsed.built_at.isBlank()) {
                throw FingerprintFormatException("Reference bank schema or build metadata is invalid.")
            }
            if (parsed.models.isEmpty()) {
                throw FingerprintFormatException("Reference bank lists no models.")
            }
            if (parsed.models.size > MAX_MODELS) {
                throw FingerprintFormatException(
                    "Reference bank lists ${parsed.models.size} models; at most $MAX_MODELS are supported.",
                )
            }
            val seenIds = HashSet<String>()
            val models = parsed.models.mapIndexed { index, model ->
                val id = model.id.trim()
                if (id.isEmpty()) throw FingerprintFormatException("Bank model #$index has an empty id.")
                if (id.length > 200) throw FingerprintFormatException("Bank model #$index has an oversized id.")
                if (!seenIds.add(id) || model.response_count <= 0) {
                    throw FingerprintFormatException("Bank model #$index has a duplicate id or invalid response count.")
                }
                FingerprintBankModel(
                    id = id,
                    displayName = model.display_name.trim().ifEmpty { id },
                    family = model.family.trim(),
                    familyName = model.family_name.trim(),
                    responseCount = model.response_count,
                    validNumberCount = model.valid_number_count,
                )
            }
            return ReferenceBank(
                schema = parsed.schema,
                builtAt = parsed.built_at,
                referenceSha256 = parsed.reference_sha256,
                models = models,
            )
        }
    }
}

/** Decoded values of the detector header, with float32 placeholders already resolved. */
internal sealed interface BinValue {
    class Number(val value: Double) : BinValue
    class Text(val value: String) : BinValue
    class Vector(val values: DoubleArray) : BinValue
    class Array(val items: List<BinValue>) : BinValue
    class Object(val fields: Map<String, BinValue>) : BinValue
    object Null : BinValue
}

/**
 * Bounded reader for `shared_detector.bin`. Limits the JSON tree and the total expansion of float
 * references, each of which must also lie inside the binary payload.
 */
internal object FingerprintBinCodec {
    private const val MAX_DEPTH = 24
    private const val HEADER_OVERHEAD = 4
    private const val MAX_HEADER_BYTES = 1024 * 1024

    /**
     * Upstream `encodeDetector` writes each array once. Allow limited aliasing of payload ranges,
     * with a small allowance for tiny fixtures and a hard ceiling of 64 MiB for copied vectors.
     */
    private const val RESOLVED_VALUE_SLACK = 4
    private const val RESOLVED_VALUE_FLOOR = 65_536
    private const val MAX_RESOLVED_VALUES = 8_388_608

    fun decode(bytes: ByteArray): BinValue {
        if (bytes.size < HEADER_OVERHEAD + 4) {
            throw FingerprintFormatException("Detector file is truncated (${bytes.size} bytes).")
        }
        val headerLength = readUInt32(bytes, 0)
        if (headerLength < 2L || headerLength > (bytes.size - HEADER_OVERHEAD).toLong()) {
            throw FingerprintFormatException(
                "Detector header length $headerLength does not fit a ${bytes.size}-byte file.",
            )
        }
        if (headerLength > MAX_HEADER_BYTES) {
            throw FingerprintFormatException("Detector JSON header exceeds $MAX_HEADER_BYTES bytes.")
        }
        val start = ((HEADER_OVERHEAD + headerLength + 3L) / 4L) * 4L
        if (start > bytes.size) {
            throw FingerprintFormatException("Detector header padding runs past the end of the file.")
        }
        val payload = bytes.size - start.toInt()
        if (payload % 4 != 0) {
            throw FingerprintFormatException("Detector float payload is not a whole number of float32 values.")
        }
        val table = FloatTable(
            bytes = bytes,
            start = start.toInt(),
            count = payload / 4,
            maxResolved = resolvedValueBudget(payload / 4),
        )
        val header = String(bytes, HEADER_OVERHEAD, headerLength.toInt(), Charsets.UTF_8)
        val element = try {
            Json.parseToJsonElement(header)
        } catch (error: Exception) {
            throw FingerprintFormatException("Detector header is not valid JSON.", error)
        }
        return convert(element, table, 0)
    }

    private fun resolvedValueBudget(count: Int): Long = min(
        MAX_RESOLVED_VALUES.toLong(),
        max(RESOLVED_VALUE_FLOOR.toLong(), count.toLong() * RESOLVED_VALUE_SLACK),
    )

    private fun convert(element: JsonElement, floats: FloatTable, depth: Int): BinValue {
        if (depth > MAX_DEPTH) throw FingerprintFormatException("Detector header nests more than $MAX_DEPTH levels deep.")
        return when (element) {
            is JsonNull -> BinValue.Null
            is JsonObject -> {
                val f32 = element["\$f32"]
                if (f32 != null) {
                    val offset = (f32 as? JsonPrimitive)?.intOrNull
                        ?: throw FingerprintFormatException("Detector float32 reference has a non-integer offset.")
                    val length = (element["n"] as? JsonPrimitive)?.intOrNull
                        ?: throw FingerprintFormatException("Detector float32 reference is missing its length.")
                    BinValue.Vector(floats.read(offset, length))
                } else {
                    BinValue.Object(element.mapValues { convert(it.value, floats, depth + 1) })
                }
            }
            is JsonArray -> BinValue.Array(element.map { convert(it, floats, depth + 1) })
            is JsonPrimitive -> when {
                element.isString -> BinValue.Text(element.content)
                else -> BinValue.Number(
                    element.doubleOrNull
                        ?: throw FingerprintFormatException("Detector header contains a non-numeric scalar."),
                )
            }
        }
    }

    private fun readUInt32(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            ((bytes[at + 2].toLong() and 0xFF) shl 16) or
            ((bytes[at + 3].toLong() and 0xFF) shl 24)

    private class FloatTable(
        private val bytes: ByteArray,
        private val start: Int,
        val count: Int,
        private val maxResolved: Long,
    ) {
        private var resolved = 0L

        fun read(offset: Int, length: Int): DoubleArray {
            if (offset < 0 || length < 0 || offset.toLong() > count.toLong() - length.toLong()) {
                throw FingerprintFormatException(
                    "Detector float32 range [$offset, ${offset.toLong() + length}) is outside the $count-value payload.",
                )
            }
            // Overlapping references each copy their range, so the running total needs the bound;
            // checking it before the allocation keeps a hostile header from exhausting memory.
            val total = resolved + length
            if (total > maxResolved) {
                throw FingerprintFormatException(
                    "Detector header resolves more than $maxResolved float32 values from a " +
                        "$count-value payload; the file is malformed.",
                )
            }
            resolved = total
            return DoubleArray(length) { index -> valueAt(offset + index) }
        }

        private fun valueAt(index: Int): Double {
            val b0 = bytes[start + index].toInt() and 0xFF
            val b1 = bytes[start + count + index].toInt() and 0xFF
            val b2 = bytes[start + count * 2 + index].toInt() and 0xFF
            val b3 = bytes[start + count * 3 + index].toInt() and 0xFF
            val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            return Float.fromBits(bits).toDouble()
        }
    }
}

internal class Preprocessing(val mean: DoubleArray, val scale: DoubleArray)

internal class FeatureBank(
    val featureMean: DoubleArray,
    val featureScale: DoubleArray,
    val nuisanceBasis: Array<DoubleArray>,
    val centroids: Array<DoubleArray>,
    val environmentCentroids: Array<Array<DoubleArray>>?,
)

internal class PositionalHead(
    val params: List<Preprocessing>,
    val weights: Array<DoubleArray>,
    val bias: DoubleArray,
)

internal class DetectorCalibration(
    val schema: String,
    val method: String,
    val run: String?,
    val tau: Double,
    val bindingBaseSha256: String,
    val bindingReferenceSha256: String,
    val bindingModelIds: List<String>,
)

/**
 * Frozen detector v2 blob: model order, calibration head and the ranker parameters.
 *
 * The same instance can score any number of answer sets; all heavy parameters are read once.
 */
class SharedDetector internal constructor(
    val schema: String,
    val sourceRun: String,
    val bankBuiltAt: String,
    val baseSha256: String,
    val sourceReferenceSha256: String,
    val modelIds: List<String>,
    val responseCounts: List<Int>,
    val calibration: FingerprintCalibration?,
    internal val ranker: DetectorRanker,
    internal val confidence: DetectorCalibration?,
) {
    /**
     * Closed-set softmax over the ranking scores, or null when the calibration head does not bind
     * to this blob or the scores are not finite.
     */
    internal fun calibrateRanking(ranking: DoubleArray): DoubleArray? {
        val head = confidence ?: return null
        if (head.schema != FingerprintProtocol.CONFIDENCE_SCHEMA) return null
        if (head.method != FingerprintProtocol.CONFIDENCE_METHOD) return null
        if (!head.tau.isFinite() || head.tau < 0.001 || head.tau > 1000.0) return null
        if (head.bindingBaseSha256 != baseSha256) return null
        if (head.bindingReferenceSha256 != sourceReferenceSha256) return null
        if (head.bindingModelIds.size != ranking.size) return null
        if (ranking.size != modelIds.size) return null
        if (ranking.any { !it.isFinite() }) return null
        for (index in modelIds.indices) {
            if (head.bindingModelIds[index] != modelIds[index]) return null
        }
        var maximum = Double.NEGATIVE_INFINITY
        for (score in ranking) maximum = max(maximum, head.tau * score)
        val weights = DoubleArray(ranking.size) { exp(head.tau * ranking[it] - maximum) }
        var total = 0.0
        for (weight in weights) total += weight
        return DoubleArray(weights.size) { weights[it] / total }
    }

    companion object {
        fun decode(bytes: ByteArray): SharedDetector {
            val root = FingerprintBinCodec.decode(bytes).asFields("detector")
            val schema = root.text("schema", "detector")
            if (schema != FingerprintProtocol.DETECTOR_SCHEMA) {
                throw FingerprintFormatException("Unsupported detector schema \"$schema\".")
            }
            val modelIds = root.textList("model_ids", "detector")
            if (modelIds.isEmpty()) throw FingerprintFormatException("Detector lists no models.")
            if (modelIds.size > 1024) throw FingerprintFormatException("Detector lists ${modelIds.size} models; at most 1024 are supported.")
            val responseCounts = root.numberList("response_counts", "detector").map { it.toInt() }
            if (responseCounts.size != modelIds.size) {
                throw FingerprintFormatException("Detector response_counts has ${responseCounts.size} entries for ${modelIds.size} models.")
            }

            val rankerFields = root.field("ranker", "detector").asFields("ranker")
            val headParams = preprocessingList(rankerFields.field("head_params", "ranker"), "head_params")
            val fullParams = preprocessingList(rankerFields.field("full_params", "ranker"), "full_params")
            requirePreprocessing(headParams, "head_params", 2)
            requirePreprocessing(fullParams, "full_params", 2)
            requireDimension(headParams[0], FingerprintProtocol.DIMENSION, "head_params[0]")
            requireDimension(headParams[1], FingerprintProtocol.ORDERED_BLOCK_DIMENSION, "head_params[1]")
            requireDimension(fullParams[0], FingerprintProtocol.DIMENSION, "full_params[0]")
            requireDimension(fullParams[1], FingerprintProtocol.ORDERED_BLOCK_DIMENSION, "full_params[1]")

            val ldaWeights = rankerFields.field("lda_weights", "ranker").asMatrix("lda_weights")
            val ldaBias = rankerFields.field("lda_bias", "ranker").asVector("lda_bias")
            if (ldaWeights.size != modelIds.size) {
                throw FingerprintFormatException("lda_weights has ${ldaWeights.size} rows for ${modelIds.size} models.")
            }
            if (ldaBias.size != modelIds.size) {
                throw FingerprintFormatException("lda_bias has ${ldaBias.size} entries for ${modelIds.size} models.")
            }
            ldaWeights.forEachIndexed { index, row ->
                if (row.size != FingerprintProtocol.COMBINED_DIMENSION) {
                    throw FingerprintFormatException(
                        "lda_weights[$index] has ${row.size} entries; expected ${FingerprintProtocol.COMBINED_DIMENSION}.",
                    )
                }
            }

            val references = rankerFields.field("references", "ranker").asMatrices("references")
            if (references.size != modelIds.size) {
                throw FingerprintFormatException("references has ${references.size} models for ${modelIds.size} model ids.")
            }
            references.forEachIndexed { index, modelReferences ->
                if (modelReferences.isEmpty()) {
                    throw FingerprintFormatException("references[$index] has no reference answers.")
                }
                modelReferences.forEachIndexed { position, reference ->
                    if (reference.size != FingerprintProtocol.COMBINED_DIMENSION) {
                        throw FingerprintFormatException(
                            "references[$index][$position] has ${reference.size} entries; " +
                                "expected ${FingerprintProtocol.COMBINED_DIMENSION}.",
                        )
                    }
                }
            }

            val bankFields = rankerFields.field("bank", "ranker").asFields("ranker.bank")
            val hellinger = featureBank(
                fields = bankFields.field("hellinger", "ranker.bank").asFields("ranker.bank.hellinger"),
                name = "ranker.bank.hellinger",
                dimension = FingerprintProtocol.DIMENSION,
                modelCount = modelIds.size,
                withEnvironments = false,
            )
            val orderedBlocks = featureBank(
                fields = bankFields.field("ordered_blocks", "ranker.bank").asFields("ranker.bank.ordered_blocks"),
                name = "ranker.bank.ordered_blocks",
                dimension = FingerprintProtocol.ORDERED_BLOCK_DIMENSION,
                modelCount = modelIds.size,
                withEnvironments = true,
            )

            val blend = rankerFields.field("blend", "ranker").asFields("ranker.blend")
            val blendFrequency = blend.number("frequency", "ranker.blend")
            val blendPositional = blend.number("positional", "ranker.blend")

            val positionalFields = rankerFields.field("positional", "ranker").asFields("ranker.positional")
            val positionalParams = preprocessingList(positionalFields.field("params", "ranker.positional"), "positional.params")
            requirePreprocessing(positionalParams, "positional.params", 4)
            requireDimension(positionalParams[0], FingerprintProtocol.DIMENSION, "positional.params[0]")
            requireDimension(positionalParams[1], FingerprintProtocol.ORDERED_BLOCK_DIMENSION, "positional.params[1]")
            requireDimension(positionalParams[2], FingerprintProtocol.POSITION_KERNEL_DIMENSION, "positional.params[2]")
            requireDimension(positionalParams[3], FingerprintProtocol.SEQUENCE_DIMENSION, "positional.params[3]")
            val positionalWeights = positionalFields.field("weights", "ranker.positional").asMatrix("positional.weights")
            val positionalBias = positionalFields.field("bias", "ranker.positional").asVector("positional.bias")
            if (positionalWeights.size != modelIds.size) {
                throw FingerprintFormatException(
                    "positional.weights has ${positionalWeights.size} rows for ${modelIds.size} models.",
                )
            }
            positionalWeights.forEachIndexed { index, row ->
                if (row.size != FingerprintProtocol.POSITIONAL_DIMENSION) {
                    throw FingerprintFormatException(
                        "positional.weights[$index] has ${row.size} entries; expected ${FingerprintProtocol.POSITIONAL_DIMENSION}.",
                    )
                }
            }
            if (positionalBias.size != modelIds.size) {
                throw FingerprintFormatException(
                    "positional.bias has ${positionalBias.size} entries for ${modelIds.size} models.",
                )
            }

            val calibrationFields = (root["calibration"] as? BinValue.Object)?.fields
            val confidence = calibrationFields?.let { fields ->
                DetectorCalibration(
                    schema = fields.textOrEmpty("schema"),
                    method = fields.textOrEmpty("method"),
                    run = fields.textOrNull("calibration_run"),
                    tau = fields.numberOrNaN("tau"),
                    bindingBaseSha256 = fields.fieldOrNull("binding")?.asFields("calibration.binding")
                        ?.textOrEmpty("base_sha256").orEmpty(),
                    bindingReferenceSha256 = fields.fieldOrNull("binding")?.asFields("calibration.binding")
                        ?.textOrEmpty("reference_sha256").orEmpty(),
                    bindingModelIds = fields.fieldOrNull("binding")?.asFields("calibration.binding")
                        ?.textListOrEmpty("model_ids").orEmpty(),
                )
            }

            return SharedDetector(
                schema = schema,
                sourceRun = root.textOrEmpty("source_run"),
                bankBuiltAt = root.textOrEmpty("bank_built_at"),
                baseSha256 = root.textOrEmpty("base_sha256"),
                sourceReferenceSha256 = root.textOrEmpty("source_reference_sha256"),
                modelIds = modelIds,
                responseCounts = responseCounts,
                calibration = confidence?.let {
                    FingerprintCalibration(
                        method = it.method,
                        run = it.run,
                        tau = it.tau,
                        sha256 = root.textOrNull("calibration_sha256"),
                    )
                },
                ranker = DetectorRanker(
                    headParams = headParams,
                    fullParams = fullParams,
                    ldaWeights = ldaWeights,
                    ldaBias = ldaBias,
                    references = references,
                    hellinger = hellinger,
                    orderedBlocks = orderedBlocks,
                    blendFrequency = blendFrequency,
                    blendPositional = blendPositional,
                    positional = PositionalHead(positionalParams, positionalWeights, positionalBias),
                ),
                confidence = confidence,
            )
        }

        private fun preprocessingList(element: BinValue, name: String): List<Preprocessing> =
            element.asList(name).mapIndexed { index, item ->
                val fields = item.asFields("$name[$index]")
                Preprocessing(
                    mean = fields.field("mean", "$name[$index]").asVector("$name[$index].mean"),
                    scale = fields.field("scale", "$name[$index]").asVector("$name[$index].scale"),
                )
            }

        private fun requirePreprocessing(params: List<Preprocessing>, name: String, expected: Int) {
            if (params.size != expected) {
                throw FingerprintFormatException("$name has ${params.size} blocks; expected $expected.")
            }
        }

        private fun requireDimension(block: Preprocessing, expected: Int, name: String) {
            if (block.mean.size != expected || block.scale.size != expected) {
                throw FingerprintFormatException(
                    "$name has mean/scale of ${block.mean.size}/${block.scale.size}; expected $expected.",
                )
            }
        }

        private fun featureBank(
            fields: Map<String, BinValue>,
            name: String,
            dimension: Int,
            modelCount: Int,
            withEnvironments: Boolean,
        ): FeatureBank {
            val featureMean = fields.field("feature_mean", name).asVector("$name.feature_mean")
            val featureScale = fields.field("feature_scale", name).asVector("$name.feature_scale")
            if (featureMean.size != dimension || featureScale.size != dimension) {
                throw FingerprintFormatException(
                    "$name has feature_mean/scale of ${featureMean.size}/${featureScale.size}; expected $dimension.",
                )
            }
            val nuisanceBasis = fields.field("nuisance_basis", name).asMatrix("$name.nuisance_basis")
            nuisanceBasis.forEachIndexed { index, row ->
                if (row.size != dimension) {
                    throw FingerprintFormatException("$name.nuisance_basis[$index] has ${row.size} entries; expected $dimension.")
                }
            }
            val centroids = fields.field("centroids", name).asMatrix("$name.centroids")
            if (centroids.size != modelCount) {
                throw FingerprintFormatException("$name.centroids has ${centroids.size} rows for $modelCount models.")
            }
            centroids.forEachIndexed { index, row ->
                if (row.size != dimension) {
                    throw FingerprintFormatException("$name.centroids[$index] has ${row.size} entries; expected $dimension.")
                }
            }
            val environments = if (withEnvironments) {
                val parsed = fields.field("environment_centroids", name).asMatrices("$name.environment_centroids")
                if (parsed.isEmpty()) {
                    throw FingerprintFormatException("$name.environment_centroids is empty.")
                }
                parsed.forEachIndexed { environment, rows ->
                    if (rows.size != modelCount) {
                        throw FingerprintFormatException(
                            "$name.environment_centroids[$environment] has ${rows.size} rows for $modelCount models.",
                        )
                    }
                    rows.forEachIndexed { index, row ->
                        if (row.size != dimension) {
                            throw FingerprintFormatException(
                                "$name.environment_centroids[$environment][$index] has ${row.size} entries; " +
                                    "expected $dimension.",
                            )
                        }
                    }
                }
                parsed
            } else {
                null
            }
            return FeatureBank(featureMean, featureScale, nuisanceBasis, centroids, environments)
        }
    }
}

private fun BinValue.asFields(name: String): Map<String, BinValue> =
    (this as? BinValue.Object)?.fields ?: throw FingerprintFormatException("\"$name\" must be a JSON object.")

private fun Map<String, BinValue>.field(name: String, owner: String): BinValue =
    this[name] ?: throw FingerprintFormatException("\"$owner\" is missing \"$name\".")

private fun Map<String, BinValue>.fieldOrNull(name: String): BinValue? =
    this[name]?.takeIf { it !is BinValue.Null }

private fun Map<String, BinValue>.text(name: String, owner: String): String =
    field(name, owner).asText("$owner.$name")

private fun Map<String, BinValue>.textOrEmpty(name: String): String =
    (this[name] as? BinValue.Text)?.value.orEmpty()

private fun Map<String, BinValue>.textOrNull(name: String): String? =
    (this[name] as? BinValue.Text)?.value

private fun Map<String, BinValue>.textList(name: String, owner: String): List<String> {
    val element = field(name, owner)
    if (element is BinValue.Vector) {
        throw FingerprintFormatException("\"$owner.$name\" must be an array of strings.")
    }
    val items = element.asList("$owner.$name")
    return items.mapIndexed { index, item -> item.asText("$owner.$name[$index]") }
}

private fun Map<String, BinValue>.textListOrEmpty(name: String): List<String> =
    (this[name] as? BinValue.Array)?.items
        ?.mapNotNull { (it as? BinValue.Text)?.value }
        .orEmpty()

private fun Map<String, BinValue>.number(name: String, owner: String): Double =
    field(name, owner).asDouble("$owner.$name")

private fun Map<String, BinValue>.numberOrNaN(name: String): Double =
    (this[name] as? BinValue.Number)?.value ?: Double.NaN

private fun Map<String, BinValue>.numberList(name: String, owner: String): List<Double> =
    field(name, owner).asVector("$owner.$name").toList()

private fun BinValue.asDouble(name: String): Double =
    (this as? BinValue.Number)?.value ?: throw FingerprintFormatException("\"$name\" must be a number.")

private fun BinValue.asText(name: String): String =
    (this as? BinValue.Text)?.value ?: throw FingerprintFormatException("\"$name\" must be a string.")

private fun BinValue.asList(name: String): List<BinValue> =
    (this as? BinValue.Array)?.items ?: throw FingerprintFormatException("\"$name\" must be an array.")

private fun BinValue.asVector(name: String): DoubleArray = when (this) {
    is BinValue.Vector -> values
    is BinValue.Array -> DoubleArray(items.size) { items[it].asDouble("$name[$it]") }
    else -> throw FingerprintFormatException("\"$name\" must be an array of numbers.")
}

private fun BinValue.asMatrix(name: String): Array<DoubleArray> =
    asList(name).mapIndexed { index, row -> row.asVector("$name[$index]") }.toTypedArray()

private fun BinValue.asMatrices(name: String): Array<Array<DoubleArray>> =
    asList(name).mapIndexed { index, rows -> rows.asMatrix("$name[$index]") }.toTypedArray()

/**
 * Frozen ranker: nuisance-projected Hellinger centroids, an ordered-block template comparison, the
 * k-nearest-reference distance and the shrinkage-LDA position discriminant, blended as upstream.
 */
internal class DetectorRanker(
    private val headParams: List<Preprocessing>,
    private val fullParams: List<Preprocessing>,
    private val ldaWeights: Array<DoubleArray>,
    private val ldaBias: DoubleArray,
    private val references: Array<Array<DoubleArray>>,
    private val hellinger: FeatureBank,
    private val orderedBlocks: FeatureBank,
    private val blendFrequency: Double,
    private val blendPositional: Double,
    private val positional: PositionalHead,
) {
    private val referenceNorms: Array<DoubleArray> = Array(references.size) { model ->
        DoubleArray(references[model].size) { index ->
            FingerprintMath.dot(references[model][index], references[model][index])
        }
    }

    fun rank(numbers: List<IntArray>): DoubleArray {
        if (numbers.isEmpty()) throw FingerprintScoringException("No answer to score.")
        val modelCount = ldaWeights.size

        val blocks = numbers.map { number ->
            listOf(FingerprintMath.hellingerFeature(FingerprintMath.countNumbers(number)), FingerprintMath.orderedBlockFeature(number))
        }
        val answers = blocks.map { transform(it, fullParams) }

        val lda = numbers.map { number ->
            val prefix = if (number.size > FingerprintProtocol.PREFILTER_LENGTH) {
                number.copyOf(FingerprintProtocol.PREFILTER_LENGTH)
            } else {
                number
            }
            val features = transform(
                listOf(
                    FingerprintMath.hellingerFeature(FingerprintMath.countNumbers(prefix)),
                    FingerprintMath.orderedBlockFeature(prefix),
                ),
                headParams,
            )
            FingerprintMath.zscore(
                DoubleArray(modelCount) { model ->
                    FingerprintMath.dot(ldaWeights[model], features) + ldaBias[model]
                },
            )
        }
        val frequencyScores = FingerprintMath.zscore(FingerprintMath.columnMean(lda))
        val nearestScores = FingerprintMath.zscore(
            DoubleArray(references.size) { model ->
                FingerprintMath.median(DoubleArray(answers.size) { answer -> -nearest(answers[answer], model) })
            },
        )
        val baselineScores = FingerprintMath.zscore(FingerprintMath.columnMean(blocks.map { baseline(it) }))
        val frequency = FingerprintMath.zscore(
            DoubleArray(references.size) { model ->
                0.5 * frequencyScores[model] + 0.25 * nearestScores[model] + 0.25 * baselineScores[model]
            },
        )
        val positionalScores = FingerprintMath.zscore(
            FingerprintMath.columnMean(
                numbers.map { number ->
                    FingerprintMath.zscore(FingerprintMath.positionalDecision(number, positional))
                },
            ),
        )
        val ranking = DoubleArray(references.size) { model ->
            blendFrequency * frequency[model] + blendPositional * positionalScores[model]
        }
        if (ranking.any { !it.isFinite() }) {
            throw FingerprintScoringException("Ranking produced a non-finite score; the detector file is damaged.")
        }
        return ranking
    }

    /** Mean of the seven smallest squared distances, relative to nothing in particular. */
    private fun nearest(vector: DoubleArray, model: Int): Double {
        val modelReferences = references[model]
        val norms = referenceNorms[model]
        val self = FingerprintMath.dot(vector, vector)
        val distances = DoubleArray(modelReferences.size) { index ->
            max(0.0, self + norms[index] - 2.0 * FingerprintMath.dot(vector, modelReferences[index]))
        }
        distances.sort()
        val take = min(7, distances.size)
        var total = 0.0
        for (index in 0 until take) total += distances[index]
        return total / take
    }

    private fun transform(blocks: List<DoubleArray>, params: List<Preprocessing>): DoubleArray {
        val total = blocks.sumOf { it.size }
        val output = DoubleArray(total)
        var offset = 0
        blocks.forEachIndexed { index, block ->
            val preprocessing = params[index]
            val scaled = DoubleArray(block.size) { position ->
                (block[position] - preprocessing.mean[position]) / preprocessing.scale[position]
            }
            val unit = FingerprintMath.normalize(scaled)
            val weight = sqrt(if (index == 0) 0.75 else 0.25)
            for (position in unit.indices) output[offset + position] = unit[position] * weight
            offset += unit.size
        }
        return output
    }

    /**
     * Per-model baseline from the frozen bank: nuisance-free Hellinger cosine similarity fused with
     * the ordered-block template (best environment) and its nuisance-only variant.
     */
    private fun baseline(blocks: List<DoubleArray>): DoubleArray {
        val hellingerCentered = FingerprintMath.centered(blocks[0], hellinger)
        val hellingerUnit = FingerprintMath.normalize(
            FingerprintMath.subtractBasis(hellingerCentered, hellinger.nuisanceBasis),
        )
        val marginal = DoubleArray(hellinger.centroids.size) { model ->
            FingerprintMath.dot(hellingerUnit, hellinger.centroids[model])
        }

        val orderedCentered = FingerprintMath.centered(blocks[1], orderedBlocks)
        val raw = FingerprintMath.normalize(orderedCentered)
        val projected = FingerprintMath.normalize(
            FingerprintMath.subtractBasis(orderedCentered, orderedBlocks.nuisanceBasis),
        )
        val environments = orderedBlocks.environmentCentroids
            ?: throw FingerprintScoringException("The detector has no ordered-block environment templates.")
        val template = DoubleArray(orderedBlocks.centroids.size) { model ->
            var best = Double.NEGATIVE_INFINITY
            for (environment in environments) {
                best = max(best, FingerprintMath.dot(raw, environment[model]))
            }
            best
        }
        val nuisance = DoubleArray(orderedBlocks.centroids.size) { model ->
            FingerprintMath.dot(projected, orderedBlocks.centroids[model])
        }

        val templateZ = FingerprintMath.zscore(template)
        val nuisanceZ = FingerprintMath.zscore(nuisance)
        val orderedZ = FingerprintMath.zscore(
            DoubleArray(templateZ.size) { 0.5 * templateZ[it] + 0.5 * nuisanceZ[it] },
        )
        val marginalZ = FingerprintMath.zscore(marginal)
        return DoubleArray(marginalZ.size) { 0.5 * marginalZ[it] + 0.5 * orderedZ[it] }
    }
}

/** Longest run of in-range ASCII integers, retaining the first run on ties. */
internal class FingerprintNumberParser {
    private var best: List<Int> = emptyList()
    private var current = ArrayList<Int>()
    private var pendingNumber: Int? = null
    private var letterSinceNumber = false
    private var highSurrogate: Char? = null

    val completedCount: Int get() = max(best.size, current.size)

    fun append(text: CharSequence) {
        for (char in text) {
            if (char.isAsciiDigit()) {
                val number = pendingNumber ?: 0
                // Once out of range, further digits cannot make this value valid.
                if (number <= FingerprintProtocol.VALUE_MAX) {
                    pendingNumber = number * 10 + (char - '0')
                }
                highSurrogate = null
            } else {
                finishNumber()
                val previousHigh = highSurrogate
                if (char.isLetter() || (previousHigh != null && Character.isLowSurrogate(char) &&
                        Character.isLetter(Character.toCodePoint(previousHigh, char)))
                ) {
                    letterSinceNumber = true
                }
                highSurrogate = char.takeIf { Character.isHighSurrogate(it) }
            }
        }
    }

    /** A trailing number is only complete when the provider ends the reply or a separator follows it. */
    fun numbers(includeTrailingNumber: Boolean): IntArray {
        if (includeTrailingNumber) finishNumber()
        return if (current.size > best.size) current.toIntArray() else best.toIntArray()
    }

    private fun finishNumber() {
        val number = pendingNumber ?: return
        if (letterSinceNumber && current.isNotEmpty()) {
            if (current.size > best.size) best = current
            current = ArrayList()
        }
        if (number in FingerprintProtocol.VALUE_MIN..FingerprintProtocol.VALUE_MAX) {
            current.add(number)
        }
        letterSinceNumber = false
        pendingNumber = null
    }
}

/** Pure feature/statistics helpers; kept separate so the maths can be unit tested directly. */
internal object FingerprintMath {
    private val POSITIONAL_WEIGHTS = doubleArrayOf(0.6, 0.15, 0.125, 0.125)
    private val CENTRES = DoubleArray(24) { 1.0 + it * 354.0 / 23.0 }
    private val LAGS = intArrayOf(1, 2, 3, 5, 8, 13, 21, 34)
    private val COVERAGE_WIDTHS = intArrayOf(4, 8, 16)

    fun parseNumbers(text: String): IntArray = FingerprintNumberParser()
        .apply { append(text) }
        .numbers(includeTrailingNumber = true)

    /** Counts signed integers outside 1..355, as reported in the per-answer diagnostics. */
    fun countRawOutOfRange(text: String): Int {
        var outOfRange = 0
        var index = 0
        while (index < text.length) {
            val start = if (text[index] == '-' && index + 1 < text.length && text[index + 1].isAsciiDigit()) {
                index.also { index += 1 }
            } else {
                index.takeIf { text[it].isAsciiDigit() }
            }
            if (start == null) {
                index += 1
                continue
            }
            while (index < text.length && text[index].isAsciiDigit()) index += 1
            val value = text.substring(start, index).toDoubleOrNull() ?: Double.POSITIVE_INFINITY
            if (value < FingerprintProtocol.VALUE_MIN || value > FingerprintProtocol.VALUE_MAX) outOfRange += 1
        }
        return outOfRange
    }

    fun countNumbers(numbers: IntArray): IntArray {
        val counts = IntArray(FingerprintProtocol.DIMENSION)
        for (number in numbers) {
            counts[number - FingerprintProtocol.VALUE_MIN] += 1
        }
        return counts
    }

    /** Additive smoothing then square root, so the block is a Hellinger embedding. */
    fun hellingerFeature(counts: IntArray): DoubleArray {
        var total = FingerprintProtocol.ALPHA * FingerprintProtocol.DIMENSION
        for (count in counts) total += count
        return DoubleArray(counts.size) { sqrt((counts[it] + FingerprintProtocol.ALPHA) / total) }
    }

    /** Four equal position blocks of 16 value bins plus ten final-digit bins, smoothed and rooted. */
    fun orderedBlockFeature(numbers: IntArray): DoubleArray {
        val output = DoubleArray(FingerprintProtocol.ORDERED_BLOCK_DIMENSION)
        val chunks = splitIntoFour(numbers)
        var offset = 0
        for (chunk in chunks) {
            val bins = DoubleArray(16) { 0.5 }
            for (number in chunk) {
                val index = min(15, ((number - 1) * 16) / FingerprintProtocol.VALUE_MAX)
                bins[index] += 1.0
            }
            var total = 0.0
            for (bin in bins) total += bin
            for (bin in bins) {
                output[offset] = sqrt(bin / total)
                offset += 1
            }
        }
        val lastDigits = DoubleArray(10) { 0.5 }
        for (number in numbers) lastDigits[number % 10] += 1.0
        var lastTotal = 0.0
        for (digit in lastDigits) lastTotal += digit
        for (digit in lastDigits) {
            output[offset] = sqrt(digit / lastTotal)
            offset += 1
        }
        return output
    }

    fun positionalFeatures(numbers: IntArray): List<DoubleArray> = listOf(
        hellingerFeature(countNumbers(numbers)),
        orderedBlockFeature(numbers),
        positionKernel(numbers),
        sequenceFeatures(numbers),
    )

    /** Per-answer discriminant values over the candidate models. */
    fun positionalDecision(numbers: IntArray, head: PositionalHead): DoubleArray {
        val parts = positionalFeatures(numbers)
        val features = DoubleArray(head.weights.firstOrNull()?.size ?: FingerprintProtocol.POSITIONAL_DIMENSION)
        var offset = 0
        parts.forEachIndexed { index, block ->
            val preprocessing = head.params[index]
            val scaled = DoubleArray(block.size) { position ->
                (block[position] - preprocessing.mean[position]) / preprocessing.scale[position]
            }
            var sumOfSquares = 0.0
            for (value in scaled) sumOfSquares += value * value
            val length = max(sqrt(sumOfSquares), 1e-12)
            val weight = sqrt(POSITIONAL_WEIGHTS[index])
            for (position in scaled.indices) features[offset + position] = scaled[position] / length * weight
            offset += scaled.size
        }
        return DoubleArray(head.weights.size) { model ->
            dot(head.weights[model], features) + head.bias[model]
        }
    }

    fun sequenceShape(numbers: List<IntArray>): FingerprintSequenceShape {
        val autocorrelations = DoubleArray(numbers.size)
        val smallSteps = DoubleArray(numbers.size)
        numbers.forEachIndexed { index, answer ->
            val center = mean(answer)
            val variance = meanSquaredDeviation(answer, center)
            val steps = max(0, answer.size - 1)
            var correlation = 1.0
            if (variance != 0.0 && steps > 0) {
                var sum = 0.0
                for (position in 0 until steps) {
                    sum += (answer[position + 1] - center) * (answer[position] - center)
                }
                correlation = sum / steps / variance
            }
            autocorrelations[index] = correlation
            var small = 0
            for (position in 0 until steps) {
                if (abs(answer[position + 1] - answer[position]) <= 20) small += 1
            }
            smallSteps[index] = if (steps == 0) Double.NaN else small.toDouble() / steps
        }
        val autocorrelation = median(autocorrelations)
        val smallStepShare = median(smallSteps)
        val nearbyShare = median(DoubleArray(numbers.size) { nearbyPairShare(numbers[it]) })
        val modelLike = autocorrelation <= FingerprintProtocol.SEQUENCE_AUTOCORRELATION_LIMIT ||
            smallStepShare <= FingerprintProtocol.SEQUENCE_SMALL_STEP_SHARE_LIMIT ||
            nearbyShare <= FingerprintProtocol.SEQUENCE_NEARBY_PAIR_SHARE_LIMIT
        return FingerprintSequenceShape(
            autocorrelation = autocorrelation,
            smallStepShare = smallStepShare,
            modelLike = modelLike,
        )
    }

    fun nearbyPairShare(numbers: IntArray): Double {
        var close = 0
        var pairs = 0
        for (lag in 1..3) {
            for (index in lag until numbers.size) {
                if (abs(numbers[index] - numbers[index - lag]) <= 20) close += 1
                pairs += 1
            }
        }
        return if (pairs == 0) Double.NaN else close.toDouble() / pairs
    }

    fun zscore(values: DoubleArray): DoubleArray {
        val center = mean(values)
        val scale = max(sqrt(meanSquaredDeviation(values, center)), 1e-12)
        return DoubleArray(values.size) { (values[it] - center) / scale }
    }

    fun normalize(values: DoubleArray): DoubleArray {
        var sumOfSquares = 0.0
        for (value in values) sumOfSquares += value * value
        val scale = max(sqrt(sumOfSquares), 1e-12)
        return DoubleArray(values.size) { values[it] / scale }
    }

    fun centered(values: DoubleArray, bank: FeatureBank): DoubleArray =
        DoubleArray(values.size) { (values[it] - bank.featureMean[it]) / bank.featureScale[it] }

    fun subtractBasis(values: DoubleArray, basis: Array<DoubleArray>): DoubleArray {
        if (basis.isEmpty()) return values.copyOf()
        // Sequential subtraction: each projection uses the already-projected vector.
        val output = values.copyOf()
        for (row in basis) {
            val projection = dot(output, row)
            for (index in output.indices) output[index] -= projection * row[index]
        }
        return output
    }

    fun dot(left: DoubleArray, right: DoubleArray): Double {
        var total = 0.0
        val length = min(left.size, right.size)
        for (index in 0 until length) total += left[index] * right[index]
        return total
    }

    fun columnMean(rows: List<DoubleArray>): DoubleArray {
        if (rows.isEmpty()) return DoubleArray(0)
        val width = rows[0].size
        val output = DoubleArray(width)
        for (row in rows) {
            for (index in 0 until width) output[index] += row[index]
        }
        for (index in 0 until width) output[index] /= rows.size
        return output
    }

    fun mean(values: DoubleArray): Double {
        if (values.isEmpty()) return Double.NaN
        var total = 0.0
        for (value in values) total += value
        return total / values.size
    }

    fun mean(values: IntArray): Double {
        if (values.isEmpty()) return Double.NaN
        var total = 0.0
        for (value in values) total += value
        return total / values.size
    }

    fun meanSquaredDeviation(values: DoubleArray, center: Double): Double {
        if (values.isEmpty()) return Double.NaN
        var total = 0.0
        for (value in values) {
            val deviation = value - center
            total += deviation * deviation
        }
        return total / values.size
    }

    fun meanSquaredDeviation(values: IntArray, center: Double): Double {
        if (values.isEmpty()) return Double.NaN
        var total = 0.0
        for (value in values) {
            val deviation = value - center
            total += deviation * deviation
        }
        return total / values.size
    }

    fun median(values: DoubleArray): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.copyOf()
        sorted.sort()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private fun splitIntoFour(numbers: IntArray): List<IntArray> {
        val base = numbers.size / 4
        val remainder = numbers.size % 4
        val chunks = ArrayList<IntArray>(4)
        var start = 0
        for (index in 0 until 4) {
            val size = base + if (index < remainder) 1 else 0
            chunks.add(numbers.copyOfRange(start, start + size))
            start += size
        }
        return chunks
    }

    /** Cubic position basis times 24 value kernels and 10 final-digit indicators. */
    private fun positionKernel(numbers: IntArray): DoubleArray {
        val output = DoubleArray(FingerprintProtocol.POSITION_KERNEL_DIMENSION)
        val count = numbers.size
        if (count == 0) return output
        numbers.forEachIndexed { position, raw ->
            val value = raw.toDouble()
            val t = (position + 0.5) * 2.0 / count - 1.0
            val basis = doubleArrayOf(t, 0.5 * (3.0 * t * t - 1.0), 0.5 * (5.0 * t * t * t - 3.0 * t))
            for (k in CENTRES.indices) {
                val deviation = (value - CENTRES[k]) / 20.0
                val weight = exp(-0.5 * deviation * deviation)
                for (j in 0 until 3) output[k * 3 + j] += weight * basis[j] / count
            }
            val digit = raw % 10
            for (d in 0 until 10) {
                if (d != digit) continue
                for (j in 0 until 3) output[(CENTRES.size + d) * 3 + j] += basis[j] / count
            }
        }
        return output
    }

    /** Correlation, step size and equality rates at eight lags, transitions, persistence, coverage. */
    private fun sequenceFeatures(numbers: IntArray): DoubleArray {
        val count = numbers.size
        val center = mean(numbers)
        val scale = max(sqrt(meanSquaredDeviation(numbers, center)), 1.0)
        val standardized = DoubleArray(count) { (numbers[it] - center) / scale }
        val output = ArrayList<Double>(FingerprintProtocol.SEQUENCE_DIMENSION)

        for (lag in LAGS) {
            val pairs = count - lag
            if (pairs <= 0) {
                repeat(6) { output.add(Double.NaN) }
                continue
            }
            var correlation = 0.0
            var absoluteSum = 0.0
            var positive = 0
            var equal = 0
            var small = 0
            var nearby = 0
            for (position in 0 until pairs) {
                val step = numbers[position + lag] - numbers[position]
                correlation += standardized[position + lag] * standardized[position]
                absoluteSum += abs(step).toDouble()
                if (step > 0) positive += 1
                if (step == 0) equal += 1
                if (abs(step) <= 5) small += 1
                if (abs(step) <= 20) nearby += 1
            }
            output.add(correlation / pairs)
            output.add(absoluteSum / pairs / 355.0)
            output.add(positive.toDouble() / pairs)
            output.add(equal.toDouble() / pairs)
            output.add(small.toDouble() / pairs)
            output.add(nearby.toDouble() / pairs)
        }

        val valueBins = IntArray(count) { min(7, (numbers[it] - 1) * 8 / FingerprintProtocol.VALUE_MAX) }
        for (value in centredTransitions(valueBins, 8)) output.add(value)
        val digitBins = IntArray(count) { numbers[it] % 10 }
        for (value in centredTransitions(digitBins, 10)) output.add(value)

        val steps = IntArray(max(0, count - 1)) { numbers[it + 1] - numbers[it] }
        val standardizedSteps = DoubleArray(steps.size) { abs(standardized[it + 1] - standardized[it]) }
        output.add(fraction(max(0, count - 2)) { index -> steps[index + 1].compareTo(0) == steps[index].compareTo(0) })
        output.add(fraction(max(0, count - 2)) { index -> steps[index + 1] == steps[index] })
        output.add(fraction(max(0, count - 1)) { index -> numbers[index] % 10 == numbers[index + 1] % 10 })
        output.add(fraction(max(0, count - 1)) { index -> standardizedSteps[index] > 1.0 })
        output.add(fraction(max(0, count - 1)) { index -> standardizedSteps[index] > 2.0 })

        val buckets = IntArray(count) { min(15, (numbers[it] - 1) * 16 / FingerprintProtocol.VALUE_MAX) }
        val seen = BooleanArray(16)
        for (width in COVERAGE_WIDTHS) {
            if (count < width) {
                output.add(Double.NaN)
                continue
            }
            var total = 0.0
            for (start in 0..(count - width)) {
                seen.fill(false)
                var distinct = 0
                for (offset in 0 until width) {
                    val bucket = buckets[start + offset]
                    if (!seen[bucket]) {
                        seen[bucket] = true
                        distinct += 1
                    }
                }
                total += distinct.toDouble() / width
            }
            output.add(total / (count - width + 1))
        }
        return output.toDoubleArray()
    }

    /** Joint transitions with row and column marginals removed. */
    private fun centredTransitions(symbols: IntArray, bins: Int): DoubleArray {
        val joint = DoubleArray(bins * bins) { 0.5 }
        val total = (symbols.size - 1).toDouble() + 0.5 * bins * bins
        for (position in 1 until symbols.size) {
            joint[symbols[position - 1] * bins + symbols[position]] += 1.0
        }
        for (index in joint.indices) joint[index] /= total
        val rows = DoubleArray(bins) { row ->
            var sum = 0.0
            for (column in 0 until bins) sum += joint[row * bins + column]
            sum
        }
        val columns = DoubleArray(bins) { column ->
            var sum = 0.0
            for (row in 0 until bins) sum += joint[row * bins + column]
            sum
        }
        return DoubleArray(joint.size) { index ->
            joint[index] - rows[index / bins] * columns[index % bins]
        }
    }

    private fun fraction(length: Int, predicate: (Int) -> Boolean): Double {
        if (length <= 0) return Double.NaN
        var hits = 0
        for (index in 0 until length) if (predicate(index)) hits += 1
        return hits.toDouble() / length
    }
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

/** Whether the imported bank and detector describe the same reference set. */
data class FingerprintCompatibility(
    val supported: Boolean,
    val reasons: List<String>,
)

/**
 * Scores three model answers against an imported bank and detector.
 *
 * Nothing is fetched or persisted here: build one instance per imported pair and call [analyze]
 * whenever the three answers are ready.
 */
class FingerprintDetector(
    private val bank: ReferenceBank,
    private val detector: SharedDetector,
) {
    val bankModelCount: Int get() = bank.models.size
    val bankBuiltAt: String get() = bank.builtAt
    val version: String get() = detector.baseSha256

    val compatibility: FingerprintCompatibility = checkCompatibility()

    fun analyze(outputs: List<FingerprintOutput>, allowPartial: Boolean = false): FingerprintAnalysis {
        if (!compatibility.supported) throw FingerprintBankMismatchException(compatibility.reasons)
        require(outputs.size in 1..FingerprintProtocol.ANSWER_COUNT && outputs.all { it.expectedCount > 0 }) {
            "Expected one to three answers with positive requested counts."
        }

        val prepared = outputs.mapIndexed { index, output ->
            val minimum = max(
                FingerprintProtocol.MINIMUM_VALID_NUMBERS,
                ceil(output.expectedCount * FingerprintProtocol.MINIMUM_VALID_SHARE).toInt(),
            )
            val raw = FingerprintMath.parseNumbers(output.text)
            val tooShort = raw.size < minimum
            val overLimit = allowPartial && raw.size > output.expectedCount
            // Follow the upstream SDK preparation step before computing sequence features.
            val retained = when {
                tooShort -> IntArray(0)
                overLimit -> raw.copyOf(output.expectedCount)
                else -> raw
            }
            val diagnostic = FingerprintOutputDiagnostic(
                index = index,
                parsedNumbers = retained.size,
                minimumNumbers = minimum,
                accepted = retained.size >= minimum,
                rawOutOfRangeCount = if (tooShort || overLimit) 0 else FingerprintMath.countRawOutOfRange(output.text),
            )
            retained to diagnostic
        }
        val diagnostics = prepared.map { it.second }
        val accepted = prepared.mapNotNull { (numbers, diagnostic) -> numbers.takeIf { diagnostic.accepted } }
        val used = accepted.size
        val sequence = if (used > 0) FingerprintMath.sequenceShape(accepted) else null

        if (allowPartial && used in 1 until FingerprintProtocol.ANSWER_COUNT) {
            if (sequence != null && !sequence.modelLike) {
                return notLanguageModel(used, diagnostics, sequence)
            }
            val ranking = detector.ranker.rank(accepted)
            val results = orderedResults(ranking, probabilities = null)
            return FingerprintAnalysis(
                decision = FingerprintDecision.PARTIAL,
                results = results,
                prediction = results.firstOrNull(),
                probability = null,
                probabilityStatus = FingerprintProbabilityStatus.UNAVAILABLE,
                usedOutputs = used,
                diagnostics = diagnostics,
                sequence = sequence,
                method = FingerprintProtocol.PARTIAL_METHOD,
                baseSha256 = detector.baseSha256,
                calibration = null,
                evidenceLabel = "部分样本排名",
                evidenceReason = "使用 $used/${FingerprintProtocol.ANSWER_COUNT} 条有效回答生成排名；补齐三条后才能给出置信度。",
            )
        }

        if (outputs.size != FingerprintProtocol.ANSWER_COUNT || used != FingerprintProtocol.ANSWER_COUNT) {
            return FingerprintAnalysis(
                decision = FingerprintDecision.UNSCORABLE,
                results = emptyList(),
                prediction = null,
                probability = null,
                probabilityStatus = FingerprintProbabilityStatus.UNAVAILABLE,
                usedOutputs = used,
                diagnostics = diagnostics,
                sequence = sequence,
                method = FingerprintProtocol.DETECTOR_SCHEMA,
                baseSha256 = detector.baseSha256,
                calibration = null,
                evidenceLabel = "需要三条完整回答",
                evidenceReason = "当前有 $used/${outputs.size} 条有效回答，请补齐原来的三条回答后重新检测。",
            )
        }

        if (sequence == null || !sequence.modelLike) {
            return notLanguageModel(used, diagnostics, sequence)
        }

        val ranking = detector.ranker.rank(accepted)
        val probabilities = detector.calibrateRanking(ranking)
        val results = orderedResults(ranking, probabilities)
        val calibrated = probabilities != null
        return FingerprintAnalysis(
            decision = FingerprintDecision.RANKED,
            results = results,
            prediction = results.firstOrNull(),
            probability = results.firstOrNull()?.probability,
            probabilityStatus = if (calibrated) {
                FingerprintProbabilityStatus.REFERENCE_CALIBRATED
            } else {
                FingerprintProbabilityStatus.UNAVAILABLE
            },
            usedOutputs = used,
            diagnostics = diagnostics,
            sequence = sequence,
            method = FingerprintProtocol.DETECTOR_SCHEMA,
            baseSha256 = detector.baseSha256,
            calibration = if (calibrated) detector.calibration else null,
            evidenceLabel = if (calibrated) "库内校准排名" else "未校准排名",
            evidenceReason = if (calibrated) {
                "置信度是参考库内的闭集相对概率，不包含库外模型。"
            } else {
                "校准参数与当前检测器不匹配，置信度不可用；候选顺序由排名分数决定。"
            },
        )
    }

    private fun orderedResults(ranking: DoubleArray, probabilities: DoubleArray?): List<FingerprintCandidate> =
        detector.modelIds.indices
            .sortedByDescending { ranking[it] }
            .map { index ->
                val model = bank.models.getOrNull(index)
                FingerprintCandidate(
                    modelId = detector.modelIds[index],
                    displayName = model?.displayName ?: detector.modelIds[index],
                    family = model?.family.orEmpty(),
                    familyName = model?.familyName.orEmpty(),
                    score = ranking[index],
                    probability = probabilities?.get(index),
                )
            }

    private fun notLanguageModel(
        used: Int,
        diagnostics: List<FingerprintOutputDiagnostic>,
        sequence: FingerprintSequenceShape?,
    ) = FingerprintAnalysis(
        decision = FingerprintDecision.NOT_LANGUAGE_MODEL,
        results = emptyList(),
        prediction = null,
        probability = null,
        probabilityStatus = FingerprintProbabilityStatus.UNAVAILABLE,
        usedOutputs = used,
        diagnostics = diagnostics,
        sequence = sequence,
        method = FingerprintProtocol.DETECTOR_SCHEMA,
        baseSha256 = detector.baseSha256,
        calibration = null,
        evidenceLabel = "不像语言模型回答",
        evidenceReason = "相邻数字的变化方式与参考库中的语言模型回答不同，常见于程序生成、排序或打乱顺序的数字。未生成排名。",
    )

    private fun checkCompatibility(): FingerprintCompatibility {
        val reasons = ArrayList<String>()
        if (detector.schema != FingerprintProtocol.DETECTOR_SCHEMA) {
            reasons.add("检测器版本 ${detector.schema} 不受支持")
        }
        if (bank.builtAt != detector.bankBuiltAt) {
            reasons.add("参考库构建于 ${bank.builtAt}，检测器要求 ${detector.bankBuiltAt}")
        }
        if (bank.referenceSha256 != null && bank.referenceSha256 != detector.sourceReferenceSha256) {
            reasons.add("参考库 SHA-256 与检测器绑定的参考版本不一致")
        }
        if (bank.models.size != detector.modelIds.size) {
            reasons.add("参考库有 ${bank.models.size} 个模型，检测器有 ${detector.modelIds.size} 个")
        } else {
            bank.models.forEachIndexed { index, model ->
                if (model.id != detector.modelIds[index]) {
                    reasons.add("第 ${index + 1} 个模型为 ${model.id}，检测器要求 ${detector.modelIds[index]}")
                } else if (model.responseCount != detector.responseCounts[index]) {
                    reasons.add("模型 ${model.id} 有 ${model.responseCount} 条参考回答，检测器要求 ${detector.responseCounts[index]}")
                }
            }
        }
        return FingerprintCompatibility(supported = reasons.isEmpty(), reasons = reasons)
    }

    companion object {
        /** Decodes both imported files and returns a ready detector. */
        fun load(bankJson: ByteArray, detectorBin: ByteArray): FingerprintDetector =
            FingerprintDetector(ReferenceBank.decode(bankJson), SharedDetector.decode(detectorBin))
    }
}
