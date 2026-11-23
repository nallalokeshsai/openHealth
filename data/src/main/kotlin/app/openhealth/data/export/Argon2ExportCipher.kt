package app.openhealth.data.export

import app.openhealth.core.seam.ExportCipher
import app.openhealth.core.seam.UnsupportedExportFormat
import app.openhealth.core.seam.WrongPassphrase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Passphrase encryption for the export container (`pr.md` §6.29). Argon2id derives a
 * 256-bit key; AES-256-GCM encrypts and authenticates. The container is self-describing
 * so a future app can read an old export: magic, format version, KDF parameters, salt,
 * nonce, then ciphertext and tag.
 *
 * Defaults follow the OWASP minimum for Argon2id with room to spare (64 MiB, 3 passes,
 * one lane). Parameters are capped on read so a hostile file cannot exhaust memory.
 */
class Argon2ExportCipher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val memoryKiB: Int = DEFAULT_MEMORY_KIB,
    private val parallelism: Int = DEFAULT_PARALLELISM,
    private val random: SecureRandom = SecureRandom(),
) : ExportCipher {
    override fun seal(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, iterations, memoryKiB, parallelism)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, KEY_ALGORITHM),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            val ciphertext = cipher.doFinal(plaintext)
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { data ->
                data.write(MAGIC)
                data.writeInt(FORMAT_VERSION)
                data.writeInt(iterations)
                data.writeInt(memoryKiB)
                data.writeInt(parallelism)
                data.writeByte(salt.size)
                data.write(salt)
                data.writeByte(nonce.size)
                data.write(nonce)
                data.writeInt(ciphertext.size)
                data.write(ciphertext)
                data.flush()
            }
            return out.toByteArray()
        } finally {
            key.fill(0)
        }
    }

    override fun open(container: ByteArray, passphrase: CharArray): ByteArray = try {
        openContainer(container, passphrase)
    } catch (failure: java.io.IOException) {
        throw UnsupportedExportFormat("The export file is truncated or unreadable", failure)
    }

    private fun openContainer(container: ByteArray, passphrase: CharArray): ByteArray {
        val input = DataInputStream(ByteArrayInputStream(container))
        val magic = ByteArray(MAGIC.size)
        input.readFully(magic)
        requireHeader(magic.contentEquals(MAGIC), "Not an openHealth export")
        val formatVersion = input.readInt()
        requireHeader(
            formatVersion == FORMAT_VERSION,
            "Export format $formatVersion is not supported",
        )
        val readIterations = input.readInt()
        val readMemoryKiB = input.readInt()
        val readParallelism = input.readInt()
        requireHeader(
            readIterations in 1..MAX_ITERATIONS &&
                readMemoryKiB in MIN_MEMORY_KIB..MAX_MEMORY_KIB &&
                readParallelism in 1..MAX_PARALLELISM,
            "Export key-derivation parameters are out of range",
        )
        val salt = ByteArray(input.readUnsignedByte())
        input.readFully(salt)
        val nonce = ByteArray(input.readUnsignedByte())
        input.readFully(nonce)
        requireHeader(
            salt.size >= MIN_SALT_BYTES && nonce.size == NONCE_BYTES,
            "Export salt or nonce has the wrong length",
        )
        val length = input.readInt()
        requireHeader(length in 0..container.size, "Export ciphertext length is invalid")
        val ciphertext = ByteArray(length)
        input.readFully(ciphertext)
        val key = deriveKey(passphrase, salt, readIterations, readMemoryKiB, readParallelism)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, KEY_ALGORITHM),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            return cipher.doFinal(ciphertext)
        } catch (failure: AEADBadTagException) {
            throw WrongPassphrase(failure)
        } catch (failure: java.security.GeneralSecurityException) {
            throw WrongPassphrase(failure)
        } finally {
            key.fill(0)
        }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 3
        const val DEFAULT_MEMORY_KIB = 65_536
        const val DEFAULT_PARALLELISM = 1
        private const val MAX_ITERATIONS = 10
        private const val MIN_MEMORY_KIB = 8_192
        private const val MAX_MEMORY_KIB = 262_144
        private const val MAX_PARALLELISM = 8
        private const val SALT_BYTES = 16
        private const val MIN_SALT_BYTES = 8
        private const val NONCE_BYTES = 12
        private const val KEY_BITS = 256
        private const val BITS_PER_BYTE = 8
        private const val TAG_BITS = 128
        private const val KEY_ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION = 1
        private val MAGIC = byteArrayOf(0x4F, 0x48, 0x58, 0x31) // "OHX1"
    }

    private fun requireHeader(condition: Boolean, message: String) {
        if (!condition) throw UnsupportedExportFormat(message)
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
        memoryKiB: Int,
        parallelism: Int,
    ): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKiB)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        val key = ByteArray(KEY_BITS / BITS_PER_BYTE)
        generator.generateBytes(passphrase, key)
        return key
    }
}
