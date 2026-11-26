package me.rerere.rikkahub.data.fingerprint

import android.content.Context
import androidx.core.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import kotlin.uuid.Uuid

data class FingerprintDatabaseStatus(
    val modelCount: Int = 0,
    val builtAt: String? = null,
    val detectorVersion: String? = null,
    val hasBankImport: Boolean = false,
    val hasDetectorImport: Boolean = false,
) {
    val isReady: Boolean get() = modelCount > 0
}

sealed interface FingerprintImportResult {
    data object WaitingForDetector : FingerprintImportResult
    data object WaitingForBank : FingerprintImportResult
    data object Activated : FingerprintImportResult
}

/** Stores private, user-imported reference data without bundling or downloading it. */
class FingerprintDatabaseStore(context: Context) {
    private val root = File(context.noBackupFilesDir, "model_fingerprint")
    private val versions = File(root, "versions")
    private val activePointer = File(root, "active.version")
    private val stagedBank = File(root, "staged-bank.json")
    private val stagedDetector = File(root, "staged-detector.bin")
    private var cached: Pair<String, FingerprintDetector?>? = null

    suspend fun status(): FingerprintDatabaseStatus = withContext(Dispatchers.IO) {
        synchronized(this@FingerprintDatabaseStore) {
            val active = loadActiveLocked()
            FingerprintDatabaseStatus(
                modelCount = active?.bankModelCount ?: 0,
                builtAt = active?.bankBuiltAt?.takeIf { it.isNotBlank() },
                detectorVersion = active?.version?.takeIf { it.isNotBlank() },
                hasBankImport = stagedBank.isFile,
                hasDetectorImport = stagedDetector.isFile,
            )
        }
    }

    suspend fun loadDetector(): FingerprintDetector? = withContext(Dispatchers.IO) {
        synchronized(this@FingerprintDatabaseStore) {
            loadActiveLocked()
        }
    }

    suspend fun importBank(bytes: ByteArray): FingerprintImportResult = withContext(Dispatchers.IO) {
        require(bytes.size in 1..MAX_BANK_BYTES)
        val bank = ReferenceBank.decode(bytes)
        synchronized(this@FingerprintDatabaseStore) {
            writeAtomic(stagedBank, bytes)
            if (!stagedDetector.isFile) {
                FingerprintImportResult.WaitingForDetector
            } else {
                val detectorBytes = readBounded(stagedDetector, MAX_DETECTOR_BYTES)
                activatePairLocked(bytes, detectorBytes, FingerprintDetector(bank, SharedDetector.decode(detectorBytes)))
            }
        }
    }

    suspend fun importDetector(bytes: ByteArray): FingerprintImportResult = withContext(Dispatchers.IO) {
        require(bytes.size in 1..MAX_DETECTOR_BYTES)
        val detector = SharedDetector.decode(bytes)
        synchronized(this@FingerprintDatabaseStore) {
            writeAtomic(stagedDetector, bytes)
            if (!stagedBank.isFile) {
                FingerprintImportResult.WaitingForBank
            } else {
                val bankBytes = readBounded(stagedBank, MAX_BANK_BYTES)
                activatePairLocked(bankBytes, bytes, FingerprintDetector(ReferenceBank.decode(bankBytes), detector))
            }
        }
    }

    private fun activatePairLocked(
        bankBytes: ByteArray,
        detectorBytes: ByteArray,
        detector: FingerprintDetector,
    ): FingerprintImportResult {
        if (!detector.compatibility.supported) {
            throw FingerprintBankMismatchException(detector.compatibility.reasons)
        }

        val version = Uuid.random().toString()
        val versionDir = File(versions, version)
        check(versionDir.mkdirs()) { "Could not create a fingerprint database directory." }
        try {
            writeAtomic(File(versionDir, BANK_FILE), bankBytes)
            writeAtomic(File(versionDir, DETECTOR_FILE), detectorBytes)
        } catch (error: Exception) {
            deleteVersion(version)
            throw error
        }
        // Once the pointer may have switched, keep both versions if verification fails.
        writeAtomic(activePointer, version.toByteArray(Charsets.US_ASCII))

        cached = version to detector
        AtomicFile(stagedBank).delete()
        AtomicFile(stagedDetector).delete()
        // Collect leftovers from an interrupted import only after verifying the new active pointer.
        versions.list().orEmpty().forEach { oldVersion ->
            if (oldVersion != version && VERSION_PATTERN.matches(oldVersion)) deleteVersion(oldVersion)
        }
        return FingerprintImportResult.Activated
    }

    private fun readActiveVersion(): String? = try {
        DataInputStream(AtomicFile(activePointer).openRead()).use { input ->
            val bytes = ByteArray(VERSION_LENGTH)
            input.readFully(bytes)
            if (input.read() != -1) null else String(bytes, Charsets.US_ASCII).takeIf(VERSION_PATTERN::matches)
        }
    } catch (_: IOException) {
        null
    }

    private fun deleteVersion(version: String) {
        val directory = File(versions, version)
        AtomicFile(File(directory, BANK_FILE)).delete()
        AtomicFile(File(directory, DETECTOR_FILE)).delete()
        directory.delete()
    }

    private fun loadActiveLocked(): FingerprintDetector? {
        val version = readActiveVersion()
        cached?.let { (cachedVersion, detector) ->
            if (cachedVersion == version) return detector
        }
        cached = null
        if (version == null) return null
        val directory = File(versions, version)
        val bankFile = File(directory, BANK_FILE)
        val detectorFile = File(directory, DETECTOR_FILE)
        if (!bankFile.isFile || !detectorFile.isFile) return null
        return try {
            val detector = FingerprintDetector.load(
                readBounded(bankFile, MAX_BANK_BYTES),
                readBounded(detectorFile, MAX_DETECTOR_BYTES),
            )
            val compatible = detector.takeIf { it.compatibility.supported }
            cached = version to compatible
            compatible
        } catch (_: FingerprintFormatException) {
            cached = version to null
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun readBounded(file: File, maxBytes: Int): ByteArray {
        require(file.length() in 1..maxBytes.toLong()) { "${file.name} has an invalid size." }
        return file.readBytes()
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val atomicFile = AtomicFile(target)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
        // AtomicFile can log a failed rename without throwing. A prior file can have the same size.
        DataInputStream(atomicFile.openRead()).use { input ->
            val buffer = ByteArray(minOf(32 * 1024, bytes.size))
            var offset = 0
            while (offset < bytes.size) {
                val count = minOf(buffer.size, bytes.size - offset)
                input.readFully(buffer, 0, count)
                for (index in 0 until count) {
                    if (buffer[index] != bytes[offset + index]) throw IOException("Could not write ${target.name}.")
                }
                offset += count
            }
            if (input.read() != -1) throw IOException("Could not write ${target.name}.")
        }
    }

    companion object {
        private const val BANK_FILE = "bank.json"
        private const val DETECTOR_FILE = "shared_detector.bin"
        const val MAX_BANK_BYTES = 32 * 1024 * 1024
        const val MAX_DETECTOR_BYTES = 20 * 1024 * 1024
        private const val VERSION_LENGTH = 36
        private val VERSION_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
