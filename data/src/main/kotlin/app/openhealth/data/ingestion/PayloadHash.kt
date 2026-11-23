package app.openhealth.data.ingestion

import java.security.MessageDigest

/** SHA-256 of a raw payload, stored so a compacted record stays auditable. */
object PayloadHash {
    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte)
        }
}
