package app.openhealth.core.seam

/**
 * Encryption for the export container (`pr.md` §6.29, §7.7). Passphrase-encrypted with
 * Argon2id in the implementation; the interface stays platform-free so the iOS client
 * can supply its own once the gate opens.
 */
interface ExportCipher {
    /** Seals plaintext into one self-describing container: parameters, salt, nonce, tag. */
    fun seal(plaintext: ByteArray, passphrase: CharArray): ByteArray

    /** Opens a container, or throws [WrongPassphrase] when the tag does not verify. */
    fun open(container: ByteArray, passphrase: CharArray): ByteArray
}

class WrongPassphrase(cause: Throwable? = null) :
    Exception("The passphrase does not open this export", cause)

class UnsupportedExportFormat(message: String) : Exception(message)
