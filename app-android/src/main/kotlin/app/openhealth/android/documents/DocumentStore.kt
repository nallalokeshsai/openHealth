package app.openhealth.android.documents

import android.net.Uri

/**
 * The one seam over a user-chosen document. The app never needs a storage permission;
 * tests can supply a temp-file implementation and drive export and restore without the
 * system picker (`BUILD-ROADMAP.md` §B3).
 */
interface DocumentStore {
    fun write(uri: Uri, bytes: ByteArray)

    fun read(uri: Uri): ByteArray
}
