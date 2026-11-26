package app.openhealth.data

import app.openhealth.core.ids.TombstoneId
import app.openhealth.core.sync.ChangeOperation
import app.openhealth.data.db.Note
import app.openhealth.data.db.Profile
import app.openhealth.data.repo.SettingKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncRepositoryTest {
    private fun store(): LocalStore = inMemoryStore().let { LocalStore(it.driver, it.db) }

    private fun profile(name: String, revision: Long) = Profile(
        id = "profile",
        name = name,
        date_of_birth = "1990-04-12",
        sex_at_birth = "FEMALE",
        gender = null,
        height_cm = 170.0,
        weight_kg = null,
        units = "metric",
        zone_id = TEST_ZONE.id,
        language = "en",
        goal = null,
        activity_level = null,
        training_experience = null,
        weight_neutral = false,
        intake_tier = 1L,
        created_at = 0L,
        updated_at = 0L,
        source_id = "manual",
        revision = revision,
    )

    @Test
    fun aProfileWriteAppendsOneChangeWithItsWholeRow() {
        val store = store()
        assertEquals(0, store.sync.pendingCount())
        store.profile.upsert(profile("Sam", 1))
        assertEquals(1, store.sync.pendingCount())
        val change = store.sync.pendingChanges(10).single()
        assertEquals("profile", change.entity)
        assertEquals("profile", change.entityId)
        assertEquals(ChangeOperation.UPSERT, change.operation)
        assertEquals("Sam", change.fields["name"])
        assertTrue(change.idempotencyKey.isNotBlank())
        assertTrue(change.clock().wallMillis > 0)
    }

    @Test
    fun aSecondDeviceAppliesTheRowAndConverges() {
        val a = store()
        val b = store()
        a.profile.upsert(profile("Sam", 1))
        val change = a.sync.pendingChanges(10)

        val outcome = b.sync.applyRemote(change)
        assertEquals(1, outcome.applied)
        assertTrue(outcome.conflicts.isEmpty())
        assertEquals("Sam", b.db.profileQueries.profileById("profile").executeAsOne().name)
        assertEquals(a.sync.pendingChanges(10).single().fields, change.single().fields)
    }

    @Test
    fun settingsSyncOneKeyAtATimeAndDeviceStateIsNeverCaptured() {
        val store = store()
        store.settings.put(SettingKeys.SYNC_SERVER_URL, "https://health.example.com")
        store.settings.put(SettingKeys.SYNC_DEVICE_ID, "another")
        // Bookkeeping about this phone: when each source last reached it, and which
        // exercise catalogue this install has seeded. Neither is the person's record,
        // and a copy from another device would state something untrue here.
        store.settings.put(SettingKeys.SYNC_BOOK, """{"sources":[]}""")
        store.settings.put(SettingKeys.EXERCISE_CATALOGUE, "2026-11-01")
        store.settings.put("appearance.theme", "light")
        val pending = store.sync.pendingChanges(20)
        assertEquals(1, pending.size)
        assertEquals("settings", pending.single().entity)
        assertEquals("appearance.theme", pending.single().entityId)
    }

    @Test
    fun deviceLocalBookkeepingIsRefusedWhenItArrivesFromAnotherDevice() {
        val a = store()
        val b = store()
        b.settings.put(SettingKeys.SYNC_BOOK, """{"sources":[{"sourceId":"mine"}]}""")
        // Forge the change another device would have pushed before the policy named
        // these keys, and prove the receiver refuses rather than applies it.
        val forged = listOf(
            app.openhealth.core.sync.ChangeRecord(
                entity = "settings",
                entityId = SettingKeys.SYNC_BOOK,
                operation = app.openhealth.core.sync.ChangeOperation.UPSERT,
                fields = mapOf(
                    "key" to SettingKeys.SYNC_BOOK,
                    "value" to """{"sources":[{"sourceId":"theirs"}]}""",
                    "updated_at" to "99",
                    "schema_version" to "11",
                ),
                idempotencyKey = "other:sync-book",
                hlc = "0000000000000099-00000000",
                deviceId = "other",
                schemaVersion = 11,
                recordedAt = 99,
            ),
        )
        val outcome = b.sync.applyRemote(forged)
        assertEquals(0, outcome.applied)
        assertEquals(1, outcome.refused.size)
        assertTrue(
            b.settings.get(SettingKeys.SYNC_BOOK)!!.contains("mine"),
            "another device's sync book must not overwrite this one",
        )
        assertEquals(0, a.sync.pendingCount())
    }

    @Test
    fun twoEditsFromTheSameRevisionConflictAndTheUserResolvesThem() {
        val a = store()
        val b = store()
        a.profile.upsert(profile("Alex", 2))
        b.profile.upsert(profile("Sam", 2))
        val fromA = a.sync.pendingChanges(10)
        val fromB = b.sync.pendingChanges(10)

        b.sync.applyRemote(fromA)
        a.sync.applyRemote(fromB)
        assertEquals(1, a.sync.openConflictCount())
        assertEquals(1, b.sync.openConflictCount())
        val conflict = a.sync.conflicts().single()
        assertEquals("Alex", conflict.local.fields["name"])
        assertEquals("Sam", conflict.remote.fields["name"])
        assertEquals("sync.conflict.same_revision", conflict.reason)

        a.sync.resolveConflict(conflict.id, keepLocal = false, atMillis = 1_000)
        assertEquals(0, a.sync.openConflictCount())
        assertEquals("Sam", a.db.profileQueries.profileById("profile").executeAsOne().name)
        assertTrue(a.sync.pendingChanges(10).any { it.entity == "profile" })
    }

    @Test
    fun aDeletedRowIsTombstonedAndTheDeleteTravels() {
        val a = store()
        val b = store()
        val happenedAt = dayStart(kotlinx.datetime.LocalDate(2026, 11, 26))
        val note = Note(
            id = "note-1",
            body = "Morning check-in",
            local_date = "2026-11-26",
            zone_id = TEST_ZONE.id,
            happened_at = happenedAt.toEpochMilliseconds(),
            recorded_at = happenedAt.toEpochMilliseconds(),
            edited_at = null,
            source_id = "manual",
            raw_record_id = null,
            idempotency_key = "note-1",
            device = null,
            app_version = null,
            revision = 1L,
        )
        a.notes.insert(note)
        b.sync.applyRemote(a.sync.pendingChanges(10))
        assertNotNull(b.notes.byId(app.openhealth.core.ids.NoteId("note-1")))

        val tombstone: TombstoneId? = a.trash.deleteNote(app.openhealth.core.ids.NoteId("note-1"))
        assertNotNull(tombstone)
        val deletes = a.sync.pendingChanges(20).filter { it.operation == ChangeOperation.DELETE }
        assertEquals(1, deletes.size)
        b.sync.applyRemote(deletes)
        assertEquals(null, b.notes.byId(app.openhealth.core.ids.NoteId("note-1")))
    }

    @Test
    fun anObservationAndItsUnitSurviveTheRoundTrip() {
        val a = store()
        val b = store()
        val observation = testObservation(
            id = "obs-1",
            type = app.openhealth.core.observation.ObservationType.WEIGHT,
            value = 82.4,
            unit = app.openhealth.core.units.UcumUnit.KILOGRAM,
            start = dayStart(kotlinx.datetime.LocalDate(2026, 11, 26)),
        )
        a.observations.record(observation)
        b.sync.applyRemote(a.sync.pendingChanges(10))
        val restored = b.observations.byId(app.openhealth.core.ids.ObservationId("obs-1"))
        assertNotNull(restored)
        assertEquals(82.4, restored.value.value)
        assertEquals("kg", restored.value.unit.ucum)
    }

    @Test
    fun sampleDataIsNeverCaptured() {
        val store = store()
        store.sync.pauseCapture()
        store.profile.upsert(profile("Demo", 1))
        assertEquals(0, store.sync.pendingCount())
        store.sync.resumeCapture()
        store.profile.upsert(profile("Sam", 2))
        assertEquals(1, store.sync.pendingCount())
    }

    @Test
    fun aRemoteObservationRebuildsTheDaysRollup() {
        val a = store()
        val b = store()
        val date = kotlinx.datetime.LocalDate(2026, 11, 26)
        a.observations.record(
            testObservation(
                id = "steps-1",
                type = app.openhealth.core.observation.ObservationType.STEPS,
                value = 4_321.0,
                unit = app.openhealth.core.units.UcumUnit.STEP,
                start = dayStart(date),
            ),
        )
        b.sync.applyRemote(a.sync.pendingChanges(10))
        val rollup = b.rollups.forDayAndType(
            date,
            app.openhealth.core.observation.ObservationType.STEPS,
        )
        assertNotNull(rollup)
        assertEquals(4_321.0, rollup.value.value)
    }
}
