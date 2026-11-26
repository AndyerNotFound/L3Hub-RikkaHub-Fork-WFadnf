package me.rerere.rikkahub.data.fingerprint

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fixtures are generated in-test: no reference bank or detector blob is bundled with the app, so
 * the maths, the binary codec and the small synthetic detectors below stand in for them.
 */
class FingerprintDetectorTest {
    // ---------------------------------------------------------------- parser and features

    @Test
    fun `parser keeps the longest run and drops out of range values`() {
        assertEquals(listOf(12, 300), FingerprintMath.parseNumbers("12, 300 0 400").toList())
        assertEquals(emptyList<Int>(), FingerprintMath.parseNumbers("0 600 999").toList())
        assertEquals(listOf(1, 355), FingerprintMath.parseNumbers("[1, 355]").toList())
    }

    @Test
    fun `alphabetic separators split runs`() {
        assertEquals(listOf(3, 4, 5), FingerprintMath.parseNumbers("1 2 alpha 3 4 5").toList())
        assertEquals(listOf(7, 8), FingerprintMath.parseNumbers("7, 8 nine ten").toList())
    }

    @Test
    fun `parser recognises letters split across UTF-16 chunks`() {
        val parser = FingerprintNumberParser()
        parser.append("1, 2 \uD801")
        parser.append("\uDC00 3, 4, 5")
        assertEquals(listOf(1, 2), parser.numbers(includeTrailingNumber = false).toList())
        assertEquals(listOf(3, 4, 5), parser.numbers(includeTrailingNumber = true).toList())
    }

    @Test
    fun `raw diagnostics count signed values outside the range`() {
        assertEquals(2, FingerprintMath.countRawOutOfRange("-5 1 355 400"))
        assertEquals(0, FingerprintMath.countRawOutOfRange("1 100 355"))
    }

    @Test
    fun `hellinger feature is smoothed and keeps the reference dimension`() {
        val feature = FingerprintMath.hellingerFeature(FingerprintMath.countNumbers(IntArray(0)))
        assertEquals(FingerprintProtocol.DIMENSION, feature.size)
        val expected = sqrt(FingerprintProtocol.ALPHA / (FingerprintProtocol.ALPHA * FingerprintProtocol.DIMENSION))
        assertEquals(expected, feature[0], 1e-12)
        assertEquals(expected, feature[354], 1e-12)
    }

    @Test
    fun `feature blocks keep the v2 dimensions`() {
        val numbers = IntArray(120) { 1 + (it * 37) % 355 }
        assertEquals(74, FingerprintMath.orderedBlockFeature(numbers).size)
        val features = FingerprintMath.positionalFeatures(numbers)
        assertEquals(355, features[0].size)
        assertEquals(74, features[1].size)
        assertEquals(102, features[2].size)
        assertEquals(220, features[3].size)
        assertEquals(429, FingerprintProtocol.COMBINED_DIMENSION)
        assertEquals(751, FingerprintProtocol.POSITIONAL_DIMENSION)
        assertTrue(features[3].all { it.isFinite() })
    }

    @Test
    fun `ordered block halves are smoothed distributions`() {
        val feature = FingerprintMath.orderedBlockFeature(IntArray(120) { 1 + (it * 11) % 355 })
        var blockSum = 0.0
        for (index in 0 until 64) blockSum += feature[index]
        var digitSum = 0.0
        for (index in 64 until 74) digitSum += feature[index]
        assertEquals(1.0, blockSum, 1e-9)
        assertEquals(1.0, digitSum, 1e-9)
    }

    // ---------------------------------------------------------------- binary codec

    @Test
    fun `binary codec resolves byte planar float32 values`() {
        val header = """{"schema":"test","a":{"${'$'}f32":0,"n":3},"b":{"${'$'}f32":3,"n":2},"s":"x","nul":null}"""
        val decoded = FingerprintBinCodec.decode(encodeBin(header, doubleArrayOf(1.5, -2.25, 3.0, 0.5, 4.75)))
        val fields = (decoded as BinValue.Object).fields
        assertEquals(listOf(1.5, -2.25, 3.0), (fields["a"] as BinValue.Vector).values.toList())
        assertEquals(listOf(0.5, 4.75), (fields["b"] as BinValue.Vector).values.toList())
        assertEquals("x", (fields["s"] as BinValue.Text).value)
        assertTrue(fields["nul"] is BinValue.Null)
    }

    @Test
    fun `binary codec rejects truncated, misbounded and non-json input`() {
        expectFormatFailure { FingerprintBinCodec.decode(ByteArray(6)) }

        val oversizedHeader = ByteArray(12)
        oversizedHeader[0] = 0x40
        expectFormatFailure { FingerprintBinCodec.decode(oversizedHeader) }

        expectFormatFailure {
            FingerprintBinCodec.decode(encodeBin("""{"a":{"${'$'}f32":2,"n":5}}""", doubleArrayOf(1.0, 2.0)))
        }
        expectFormatFailure { FingerprintBinCodec.decode(encodeBin("not json", doubleArrayOf())) }
    }

    @Test
    fun `binary codec limits copies from overlapping references`() {
        val vector = """{"${'$'}f32":0,"n":1024}"""
        val payload = DoubleArray(1024)
        val inBudget = List(64) { vector }.joinToString(",", prefix = "[", postfix = "]")
        val overBudget = List(65) { vector }.joinToString(",", prefix = "[", postfix = "]")
        assertEquals(64, (FingerprintBinCodec.decode(encodeBin(inBudget, payload)) as BinValue.Array).items.size)
        expectFormatFailure { FingerprintBinCodec.decode(encodeBin(overBudget, payload)) }
    }

    // ---------------------------------------------------------------- bank and compatibility

    @Test
    fun `bank json keeps labels and ignores the heavy distributions`() {
        val bank = ReferenceBank.decode(bankJson().toByteArray(Charsets.UTF_8))
        assertEquals(BUILT_AT, bank.builtAt)
        assertEquals(REFERENCE_SHA, bank.referenceSha256)
        assertEquals(listOf("model-a", "model-b"), bank.models.map { it.id })
        assertEquals("Model A", bank.models[0].displayName)
        assertEquals("Family X", bank.models[0].familyName)
    }

    @Test
    fun `bank without models is rejected`() {
        expectFormatFailure { ReferenceBank.decode("""{"schema":"robust-number-fingerprint-bank","models":[]}""") }
        expectFormatFailure { ReferenceBank.decode("{") }
    }

    @Test
    fun `bank and detector mismatch is reported instead of scored`() {
        val detector = decodeSyntheticDetector()
        val mismatchedBank = ReferenceBank.decode(bankJson(builtAt = "2020-01-01T00:00:00Z"))
        val fingerprint = FingerprintDetector(mismatchedBank, detector)
        assertFalse(fingerprint.compatibility.supported)
        assertTrue(fingerprint.compatibility.reasons.isNotEmpty())
        expectMismatch { fingerprint.analyze(threeReplies(replyValues())) }
    }

    // ---------------------------------------------------------------- scoring

    @Test
    fun `matching centroid is ranked first with calibrated probabilities`() {
        val numbers = replyValues()
        val centroid = FingerprintMath.normalize(
            FingerprintMath.hellingerFeature(FingerprintMath.countNumbers(numbers)),
        )
        val detector = decodeSyntheticDetector(hellingerCentroids = listOf(DoubleArray(355), centroid))
        val fingerprint = FingerprintDetector(ReferenceBank.decode(bankJson()), detector)

        val analysis = fingerprint.analyze(threeReplies(numbers))

        assertEquals(FingerprintDecision.RANKED, analysis.decision)
        assertEquals(FingerprintProbabilityStatus.REFERENCE_CALIBRATED, analysis.probabilityStatus)
        assertEquals(3, analysis.usedOutputs)
        assertEquals("model-b", analysis.prediction?.modelId)
        assertEquals("Model B", analysis.prediction?.displayName)
        assertEquals(2, analysis.results.size)
        assertEquals(0.6225, analysis.prediction?.probability ?: 0.0, 1e-3)
        assertEquals(1.0, analysis.results.sumOf { it.probability ?: 0.0 }, 1e-9)
        assertTrue(analysis.results.zipWithNext().all { (left, right) -> left.score >= right.score })
        assertTrue(analysis.diagnostics.all { it.accepted })
        assertEquals(FingerprintProtocol.DETECTOR_SCHEMA, analysis.method)
    }

    @Test
    fun `calibration that does not bind falls back to an uncalibrated ranking`() {
        val numbers = replyValues()
        val detector = decodeSyntheticDetector(
            hellingerCentroids = listOf(DoubleArray(355), replyCentroid(numbers)),
            bindingBaseSha = "other-sha",
        )
        val fingerprint = FingerprintDetector(ReferenceBank.decode(bankJson()), detector)

        val analysis = fingerprint.analyze(threeReplies(numbers))

        assertEquals(FingerprintDecision.RANKED, analysis.decision)
        assertEquals(FingerprintProbabilityStatus.UNAVAILABLE, analysis.probabilityStatus)
        assertNull(analysis.probability)
        assertTrue(analysis.results.all { it.probability == null })
        assertEquals("model-b", analysis.prediction?.modelId)
        assertNull(analysis.calibration)
    }

    @Test
    fun `two answers give a partial ranking without confidence`() {
        val numbers = replyValues()
        val detector = decodeSyntheticDetector(
            hellingerCentroids = listOf(DoubleArray(355), replyCentroid(numbers)),
        )
        val fingerprint = FingerprintDetector(ReferenceBank.decode(bankJson()), detector)

        val partial = fingerprint.analyze(threeReplies(numbers).take(2), allowPartial = true)

        assertEquals(FingerprintDecision.PARTIAL, partial.decision)
        assertEquals(2, partial.usedOutputs)
        assertEquals(FingerprintProbabilityStatus.UNAVAILABLE, partial.probabilityStatus)
        assertNull(partial.probability)
        assertEquals(2, partial.results.size)
        assertEquals(FingerprintProtocol.PARTIAL_METHOD, partial.method)

        val unscorable = fingerprint.analyze(threeReplies(numbers).take(2))
        assertEquals(FingerprintDecision.UNSCORABLE, unscorable.decision)
        assertTrue(unscorable.results.isEmpty())
    }

    @Test
    fun `relaxed scoring uses only the requested prefix and treats short answers as missing`() {
        val fingerprint = FingerprintDetector(ReferenceBank.decode(bankJson()), decodeSyntheticDetector())
        val numbers = replyValues() + replyValues().reversedArray()
        val fullReplies = threeReplies(numbers)
        val prefixReplies = threeReplies(numbers.copyOf(300))
        val strict = fingerprint.analyze(fullReplies)
        val relaxed = fingerprint.analyze(fullReplies, allowPartial = true)

        assertEquals(listOf(400, 400, 400), strict.diagnostics.map { it.parsedNumbers })
        assertEquals(listOf(300, 300, 300), relaxed.diagnostics.map { it.parsedNumbers })
        assertEquals(fingerprint.analyze(prefixReplies), relaxed)

        val withShortAnswer = fullReplies.take(2) + FingerprintOutput("1 2 3 400", 300)
        val partial = fingerprint.analyze(withShortAnswer, allowPartial = true)
        val missing = fingerprint.analyze(withShortAnswer.take(2) + FingerprintOutput("", 300), allowPartial = true)
        assertEquals(FingerprintDecision.PARTIAL, partial.decision)
        assertEquals(missing, partial)
        assertNull(partial.probability)
        assertEquals(FingerprintDecision.UNSCORABLE, fingerprint.analyze(withShortAnswer).decision)
    }

    @Test
    fun `sequential answers are not treated as model output`() {
        val detector = decodeSyntheticDetector()
        val fingerprint = FingerprintDetector(ReferenceBank.decode(bankJson()), detector)

        val analysis = fingerprint.analyze(threeReplies(IntArray(300) { it + 1 }))

        assertEquals(FingerprintDecision.NOT_LANGUAGE_MODEL, analysis.decision)
        assertTrue(analysis.results.isEmpty())
        assertFalse(analysis.sequence?.modelLike ?: true)
    }

    @Test
    fun `short answers are reported as insufficient`() {
        val detector = decodeSyntheticDetector()
        val fingerprint = FingerprintDetector(ReferenceBank.decode(bankJson()), detector)
        val shortAnswers = List(3) { FingerprintOutput(text = "1 2 3", expectedCount = 300) }

        val analysis = fingerprint.analyze(shortAnswers)

        assertEquals(FingerprintDecision.UNSCORABLE, analysis.decision)
        assertEquals(0, analysis.usedOutputs)
        assertEquals(listOf(165, 165, 165), analysis.diagnostics.map { it.minimumNumbers })
        assertTrue(analysis.diagnostics.none { it.accepted })
    }

    // ---------------------------------------------------------------- challenge protocol

    @Test
    fun `generated challenges draw three distinct counts from the upstream range`() {
        val challenges = FingerprintChallengeProtocol.generate(Random(26))
        assertEquals(FingerprintProtocol.ANSWER_COUNT, challenges.size)
        assertEquals(FingerprintProtocol.ANSWER_COUNT, challenges.map { it.expectedCount }.toSet().size)
        challenges.forEachIndexed { index, challenge ->
            assertTrue(challenge.expectedCount in 292..332)
            assertTrue(challenge.id.startsWith("probe-${index + 1}-"))
            assertTrue(challenge.prompt.contains(challenge.expectedCount.toString()))
            assertTrue(challenge.prompt.contains("355"))
        }
    }

    /** One upstream request sentence per language, plus the join that has to follow it. */
    @Test
    fun `every prompt is one upstream language variant with its own join spacing`() {
        val variants = listOf(
            "1 到 355（含端点）" to "（含端点）的整数。每个位置",
            "1 to 355, inclusive" to "inclusive. Choose separately",
            "1 から 355 まで" to "個選んでください。各位置",
            "1부터 355까지" to "고르세요. 각 위치",
            "1 et 355, bornes incluses" to "bornes incluses. Choisissez séparément",
        )
        val prompts = (1..20).flatMap { seed ->
            FingerprintChallengeProtocol.generate(Random(seed)).map { it.prompt }
        }
        prompts.forEach { prompt ->
            assertEquals(1, variants.count { it.first in prompt })
            val join = variants.first { it.first in prompt }.second
            assertTrue(join, prompt.contains(join))
        }
    }

    @Test
    fun `each run samples its own counts and prompts`() {
        val first = FingerprintChallengeProtocol.generate(Random(1)).map { it.expectedCount to it.prompt }
        val second = FingerprintChallengeProtocol.generate(Random(2)).map { it.expectedCount to it.prompt }
        assertNotEquals(first, second)
    }

    // ---------------------------------------------------------------- fixtures

    /** 200 values: above the 165-value floor for a 300-value challenge, so answers are accepted. */
    private fun replyValues(): IntArray = IntArray(200) { index -> 1 + (index * 53 + 17) % 355 }

    private fun replyCentroid(numbers: IntArray): DoubleArray {
        val feature = FingerprintMath.hellingerFeature(FingerprintMath.countNumbers(numbers))
        return FingerprintMath.normalize(feature)
    }

    private fun threeReplies(numbers: IntArray): List<FingerprintOutput> =
        List(FingerprintProtocol.ANSWER_COUNT) {
            FingerprintOutput(text = numbers.joinToString(" "), expectedCount = 300)
        }

    private fun decodeSyntheticDetector(
        hellingerCentroids: List<DoubleArray> = listOf(DoubleArray(355), DoubleArray(355)),
        bindingBaseSha: String = BASE_SHA,
    ): SharedDetector = SharedDetector.decode(
        encodeBin(
            syntheticDetectorJson(hellingerCentroids = hellingerCentroids, bindingBaseSha = bindingBaseSha),
            doubleArrayOf(),
        ),
    )

    private fun bankJson(
        builtAt: String = BUILT_AT,
        models: List<Pair<String, String>> = listOf("model-a" to "Model A", "model-b" to "Model B"),
    ): String = buildJsonObject {
        put("schema", "robust-number-fingerprint-bank")
        put("built_at", builtAt)
        put("reference_sha256", REFERENCE_SHA)
        put("models", buildJsonArray {
            models.forEach { (id, displayName) ->
                add(
                    buildJsonObject {
                        put("id", id)
                        put("display_name", displayName)
                        put("family", "family-x")
                        put("family_name", "Family X")
                        put("response_count", 10)
                        put("valid_number_count", 360)
                        // Unknown (and large) fields must be skipped, not materialised.
                        put("counts", zeroVector(355))
                        put("frequency_references", buildJsonArray { add(zeroVector(429)) })
                    },
                )
            }
        })
    }.toString()

    private fun syntheticDetectorJson(
        hellingerCentroids: List<DoubleArray>,
        bindingBaseSha: String,
        modelIds: List<String> = listOf("model-a", "model-b"),
        tau: Double = 0.5,
    ): String {
        val modelCount = modelIds.size
        val ranker = buildJsonObject {
            put("head_params", blocks(FingerprintProtocol.DIMENSION, FingerprintProtocol.ORDERED_BLOCK_DIMENSION))
            put("full_params", blocks(FingerprintProtocol.DIMENSION, FingerprintProtocol.ORDERED_BLOCK_DIMENSION))
            put("lda_weights", zeroMatrix(modelCount, FingerprintProtocol.COMBINED_DIMENSION))
            put("lda_bias", zeroVector(modelCount))
            put(
                "references",
                buildJsonArray {
                    repeat(modelCount) {
                        add(buildJsonArray { add(zeroVector(FingerprintProtocol.COMBINED_DIMENSION)) })
                    }
                },
            )
            put(
                "bank",
                buildJsonObject {
                    put(
                        "hellinger",
                        buildJsonObject {
                            put("feature_mean", zeroVector(FingerprintProtocol.DIMENSION))
                            put("feature_scale", onesVector(FingerprintProtocol.DIMENSION))
                            put("nuisance_basis", emptyVectorList())
                            put(
                                "centroids",
                                buildJsonArray { hellingerCentroids.forEach { add(vector(it)) } },
                            )
                        },
                    )
                    put(
                        "ordered_blocks",
                        buildJsonObject {
                            put("feature_mean", zeroVector(FingerprintProtocol.ORDERED_BLOCK_DIMENSION))
                            put("feature_scale", onesVector(FingerprintProtocol.ORDERED_BLOCK_DIMENSION))
                            put("nuisance_basis", emptyVectorList())
                            put("centroids", zeroMatrix(modelCount, FingerprintProtocol.ORDERED_BLOCK_DIMENSION))
                            put(
                                "environment_centroids",
                                buildJsonArray { add(zeroMatrix(modelCount, FingerprintProtocol.ORDERED_BLOCK_DIMENSION)) },
                            )
                            put("weight", 0.25)
                        },
                    )
                },
            )
            put(
                "positional",
                buildJsonObject {
                    put(
                        "params",
                        blocks(
                            FingerprintProtocol.DIMENSION,
                            FingerprintProtocol.ORDERED_BLOCK_DIMENSION,
                            FingerprintProtocol.POSITION_KERNEL_DIMENSION,
                            FingerprintProtocol.SEQUENCE_DIMENSION,
                        ),
                    )
                    put("weights", zeroMatrix(modelCount, FingerprintProtocol.POSITIONAL_DIMENSION))
                    put("bias", zeroVector(modelCount))
                },
            )
            put(
                "blend",
                buildJsonObject {
                    put("frequency", 0.5)
                    put("positional", 0.5)
                },
            )
        }
        return buildJsonObject {
            put("schema", FingerprintProtocol.DETECTOR_SCHEMA)
            put("source_run", "test-run")
            put("bank_built_at", BUILT_AT)
            put("base_sha256", BASE_SHA)
            put("source_reference_sha256", REFERENCE_SHA)
            put("model_ids", buildJsonArray { modelIds.forEach { add(it) } })
            put("response_counts", buildJsonArray { repeat(modelCount) { add(10) } })
            put(
                "calibration",
                buildJsonObject {
                    put("schema", FingerprintProtocol.CONFIDENCE_SCHEMA)
                    put("method", FingerprintProtocol.CONFIDENCE_METHOD)
                    put("tau", tau)
                    put(
                        "binding",
                        buildJsonObject {
                            put("base_sha256", bindingBaseSha)
                            put("reference_sha256", REFERENCE_SHA)
                            put("model_ids", buildJsonArray { modelIds.forEach { add(it) } })
                        },
                    )
                    put("calibration_run", "test-calibration")
                },
            )
            put("ranker", ranker)
        }.toString()
    }

    private fun emptyVectorList(): JsonArray = buildJsonArray { }

    private fun zeroVector(size: Int): JsonArray = buildJsonArray { repeat(size) { add(0.0) } }

    private fun onesVector(size: Int): JsonArray = buildJsonArray { repeat(size) { add(1.0) } }

    private fun vector(values: DoubleArray): JsonArray = buildJsonArray { values.forEach { add(it) } }

    private fun zeroMatrix(rows: Int, columns: Int): JsonArray =
        buildJsonArray { repeat(rows) { add(zeroVector(columns)) } }

    private fun block(dimension: Int): JsonObject = buildJsonObject {
        put("mean", zeroVector(dimension))
        put("scale", onesVector(dimension))
    }

    private fun blocks(vararg dimensions: Int): JsonArray =
        buildJsonArray { dimensions.forEach { add(block(it)) } }

    /** Mirrors the detector blob layout: u32 length, padded JSON header, byte-planar float32s. */
    private fun encodeBin(header: String, values: DoubleArray): ByteArray {
        val headerBytes = header.toByteArray(Charsets.UTF_8)
        val start = ((4 + headerBytes.size + 3) / 4) * 4
        val output = ByteArray(start + values.size * 4)
        for (byte in 0 until 4) {
            output[byte] = ((headerBytes.size shr (byte * 8)) and 0xFF).toByte()
        }
        headerBytes.copyInto(output, 4)
        values.forEachIndexed { index, value ->
            val bits = value.toFloat().toRawBits()
            for (byte in 0 until 4) {
                output[start + byte * values.size + index] = ((bits shr (byte * 8)) and 0xFF).toByte()
            }
        }
        return output
    }

    private fun expectFormatFailure(block: () -> Unit) {
        try {
            block()
            fail("expected FingerprintFormatException")
        } catch (expected: FingerprintFormatException) {
            assertTrue(expected.message?.isNotBlank() ?: false)
        }
    }

    private fun expectMismatch(block: () -> Unit) {
        try {
            block()
            fail("expected FingerprintBankMismatchException")
        } catch (expected: FingerprintBankMismatchException) {
            assertTrue(expected.reasons.isNotEmpty())
        }
    }

    private companion object {
        const val BUILT_AT = "2026-11-23T06:25:02Z"
        const val BASE_SHA = "base-sha"
        const val REFERENCE_SHA = "reference-sha"
    }
}
