package app.openhealth.data.repo

import app.openhealth.core.ids.NoteId
import app.openhealth.data.db.Note
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.mapping.requireConsistentDay
import kotlinx.datetime.LocalDate

/**
 * Free-text notes (`pr.md` §6.27). A note is layer 4: it carries provenance and a local
 * day but no unit and no rollup. Writes are append-only (`INSERT OR IGNORE`), so a
 * replayed note never rewrites its `recorded_at`; removal is [TrashService]'s job.
 */
class NoteRepository(private val db: OpenHealthDb) {
    private var sync: app.openhealth.data.sync.SyncRepository? = null

    fun recordChangesWith(recorder: app.openhealth.data.sync.SyncRepository?) {
        sync = recorder
    }

    fun insert(note: Note): NoteId {
        requireConsistentDay(note.local_date, note.zone_id, note.happened_at)
        db.noteQueries.insertNote(
            id = note.id,
            body = note.body,
            localDate = note.local_date,
            zoneId = note.zone_id,
            happenedAt = note.happened_at,
            recordedAt = note.recorded_at,
            editedAt = note.edited_at,
            sourceId = note.source_id,
            rawRecordId = note.raw_record_id,
            idempotencyKey = note.idempotency_key,
            device = note.device,
            appVersion = note.app_version,
            revision = note.revision,
        )
        sync?.capture("note", note.id, note.local_date)
        return NoteId(note.id)
    }

    fun byId(id: NoteId): Note? = db.noteQueries.noteById(id.value).executeAsOneOrNull()

    fun forDay(date: LocalDate): List<Note> =
        db.noteQueries.notesForDay(date.toString()).executeAsList()

    fun latest(limit: Long): List<Note> = db.noteQueries.latestNotes(limit).executeAsList()

    fun all(): List<Note> = db.noteQueries.allNotes().executeAsList()

    fun count(): Long = db.noteQueries.countNotes().executeAsOne()
}
