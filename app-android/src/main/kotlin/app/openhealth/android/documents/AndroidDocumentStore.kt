package app.openhealth.android.documents

import android.content.Context
import android.net.Uri
import java.io.FileNotFoundException

/**
 * The one way the app reads and writes a user-chosen document (`pr.md` §6.29). The
 * Storage Access Framework owns the location; the app only sees the bytes, so nothing
 * needs a storage permission and the file never lands in app-private storage first.
 */
class AndroidDocumentStore(private val context: Context) {
    fun write(uri: Uri, bytes: ByteArray) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw FileNotFoundException("The chosen file cannot be written")
        stream.use { it.write(bytes) }
    }

    fun read(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("The chosen file cannot be read")
        return stream.use { it.readBytes() }
    }
}
