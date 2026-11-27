package app.openhealth.android

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.documents.DocumentStore
import app.openhealth.android.ui.AppViewModel
import app.openhealth.core.body.MeasurementSite
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.core.vitals.ArmSide
import app.openhealth.core.vitals.BodyPosition
import app.openhealth.core.vitals.HomeSlot
import app.openhealth.data.db.Measurement
import app.openhealth.data.db.Profile
import app.openhealth.ui.screens.vitals.VitalDraft
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B7's exit scenarios on the phone (`BUILD-ROADMAP.md` §B7): the dynamic body model, the
 * cited BP range, provenance, CSV and the encrypted photo store, all through the real
 * state holder, the real SQLCipher store and the real rollup engine.
 */
@RunWith(AndroidJUnit4::class)
class VitalsExitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: android.content.Context
    private lateinit var container: AppContainer
    private lateinit var viewModel: AppViewModel
    private val documents = FakeDocuments()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        container = AppContainer(
            context,
            documentStore = documents,
            databaseName = TEST_DATABASE,
            vaultPreferences = TEST_VAULT,
        )
        viewModel = AppViewModel(container, UnitSystem.METRIC)
    }

    @After
    fun tearDown() {
        viewModel.stop()
        Thread.sleep(300)
        container.close()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun changingWaistOrBodyFatChangesTheFigureAndOffersABeforeAfter() {
        seedProfile(sex = "MALE", height = 180.0)
        viewModel.saveMeasurement(MeasurementSite.NECK.type, "38")
        viewModel.saveMeasurement(MeasurementSite.WAIST.type, "86")
        waitFor("body model") { viewModel.state.value.vitals?.body?.current?.adipose != null }
        val thin = viewModel.state.value.vitals!!.body!!.current.adipose!!.maxThickness

        viewModel.saveMeasurement(MeasurementSite.WAIST.type, "102")
        waitFor("a thicker layer") {
            (viewModel.state.value.vitals?.body?.current?.adipose?.maxThickness ?: 0.0) > thin
        }
        val thick = viewModel.state.value.vitals!!.body!!.current.adipose!!.maxThickness
        assertTrue(thick > thin, "a larger waist must thicken the layer: $thin -> $thick")

        // A figure from 40 days ago gives the scrub something to move between.
        val oldDate = LocalDate.parse(
            container.clock.now().toLocalDateTime(container.zone).date.toString(),
        ).minus(DatePeriod(days = 40))
        val recorded = container.clock.now()
        container.store.body.upsertMeasurement(
            Measurement(
                id = "old-body-fat",
                type = "body_fat",
                value_ = 28.0,
                unit = "%",
                local_date = oldDate.toString(),
                zone_id = container.zone.id,
                happened_at = oldDate.atTime(LocalTime(8, 0)).toInstant(container.zone)
                    .toEpochMilliseconds(),
                recorded_at = recorded.toEpochMilliseconds(),
                source_id = AppContainer.MANUAL_SOURCE.value,
                method = "BIA",
                notes = null,
                excluded_from_trends = false,
                observation_id = null,
                raw_record_id = null,
                idempotency_key = "old-body-fat",
                device = "android",
                app_version = "test",
                revision = 1L,
            ),
        )
        viewModel.refresh()
        waitFor("before figure") { viewModel.state.value.vitals?.body?.before != null }
        assertEquals(
            oldDate,
            viewModel.state.value.vitals!!.body!!.beforeDate,
        )
    }

    @Test
    fun aBloodPressureReadingIsOnePairWithItsCitedRange() {
        seedProfile()
        viewModel.saveVitalReading(
            VitalDraft(
                type = ObservationType.BLOOD_PRESSURE_SYSTOLIC,
                systolic = "128",
                diastolic = "78",
                pulse = "64",
                arm = ArmSide.LEFT,
                position = BodyPosition.SITTING,
            ),
        )
        waitFor("the pair") {
            val day = container.clock.now().toLocalDateTime(container.zone).date
            val rows = container.store.body.vitalsForDay(day)
            rows.count { it.type == ObservationType.BLOOD_PRESSURE_SYSTOLIC.name } == 1 &&
                rows.count { it.type == ObservationType.HEART_RATE.name } == 1
        }
        val today = container.clock.now().toLocalDateTime(container.zone).date
        val vitals = container.store.body.vitalsForDay(today)
        assertEquals(2, vitals.count { it.type.startsWith("BLOOD_PRESSURE") })
        assertEquals(1, vitals.count { it.type == ObservationType.HEART_RATE.name })

        viewModel.openVital(ObservationType.BLOOD_PRESSURE_SYSTOLIC)
        waitFor("BP detail") { viewModel.state.value.vitalDetail != null }
        val detail = viewModel.state.value.vitalDetail!!
        assertEquals("128/78", detail.latestText)
        assertNotNull(detail.range)
        assertEquals("below 135/85", detail.range!!.statement)
        assertEquals("ESH 2023", detail.range!!.citationLabel.substringBefore(" ·"))
        assertEquals(2, detail.scaleValues.size)
        assertTrue(
            detail.readings.single().contextText!!.contains("Left arm"),
            "the arm context travels with the reading",
        )
    }

    @Test
    fun theHomeProtocolRemembersItsStartAndItsAverageLeavesOutDayOne() {
        seedProfile()
        viewModel.startBpProtocol()
        waitFor("protocol model") {
            viewModel.state.value.vitalDetail?.protocol?.active == true ||
                container.store.settings.get("vitals.bp_protocol_start") != null
        }
        viewModel.openVital(ObservationType.BLOOD_PRESSURE_SYSTOLIC)
        waitFor("BP detail") { viewModel.state.value.vitalDetail != null }
        val start = container.store.settings.get("vitals.bp_protocol_start")!!
        assertEquals(
            LocalDate.parse(start).toString(),
            container.store.settings.get("vitals.bp_protocol_start"),
        )
        // Day one is discarded, so a reading on the start day is outside the average.
        viewModel.saveVitalReading(
            VitalDraft(
                type = ObservationType.BLOOD_PRESSURE_SYSTOLIC,
                systolic = "150",
                diastolic = "95",
                homeSlot = HomeSlot.MORNING,
            ),
        )
        waitFor("reading stored") {
            container.store.body.vitalsForDay(
                container.clock.now().toLocalDateTime(container.zone).date,
            ).any { it.type == ObservationType.BLOOD_PRESSURE_SYSTOLIC.name }
        }
        viewModel.openVital(ObservationType.BLOOD_PRESSURE_SYSTOLIC)
        waitFor("protocol reading recorded") {
            (viewModel.state.value.vitalDetail?.protocol?.state?.recorded ?: 0) >= 1
        }
        val protocol = viewModel.state.value.vitalDetail!!.protocol!!
        assertEquals(1, protocol.state.days.first { it.date == LocalDate.parse(start) }.morning)
        assertEquals(null, protocol.averageSystolic, "day one is not in the average")
    }

    @Test
    fun aWeightCheckInReplacesTheDaysValueAndTheCsvRoundTrips() {
        seedProfile()
        viewModel.saveMeasurement(MeasurementSite.WAIST.type, "84")
        viewModel.saveWeight("81.0")
        waitFor("first weight") {
            container.store.observations.forDayAndType(
                container.clock.now().toLocalDateTime(container.zone).date,
                ObservationType.WEIGHT,
            ).isNotEmpty()
        }
        Thread.sleep(20)
        viewModel.saveWeight("82.4")
        waitFor("two weights") {
            container.store.observations.forDayAndType(
                container.clock.now().toLocalDateTime(container.zone).date,
                ObservationType.WEIGHT,
            ).size == 2
        }
        val today = container.clock.now().toLocalDateTime(container.zone).date
        val rollup = container.store.rollups.forDayAndType(today, ObservationType.WEIGHT)!!
        assertEquals(82.4, rollup.value.value, 1e-9, "the latest check-in sets the day")

        val uri = Uri.parse("content://test/body.csv")
        viewModel.exportVitalsCsv(uri)
        waitFor("csv written") { documents.written[uri] != null }
        val csv = documents.written.getValue(uri).toString(Charsets.UTF_8)
        assertTrue(csv.startsWith("date,type,value,unit,method,notes"))
        assertTrue(csv.contains("weight"))

        // Re-importing the app's own export adds nothing: the same date and value is
        // already on the phone, and a second import is idempotent too.
        viewModel.importVitalsCsv(uri)
        Thread.sleep(900)
        assertEquals(
            2,
            container.store.observations.forDayAndType(today, ObservationType.WEIGHT).size,
            "a repeated CSV import must not duplicate a weight",
        )
        viewModel.importVitalsCsv(uri)
        Thread.sleep(900)
        assertEquals(
            2,
            container.store.observations.forDayAndType(today, ObservationType.WEIGHT).size,
        )
    }

    @Test
    fun aPhotoIsEncryptedInTheStoreAndComesBackFromTrash() {
        seedProfile()
        val jpeg = jpegBytes()
        viewModel.addPhoto(jpeg, "Front")
        waitFor("photo stored") { container.store.body.countPhotos() == 1L }
        val photo = container.store.body.allPhotos().single()
        assertTrue(photo.bytes_b64.isNotBlank())
        assertTrue(photo.thumb_b64 != null)
        val decoded = android.util.Base64.decode(photo.bytes_b64, android.util.Base64.NO_WRAP)
        assertEquals(0xFF.toByte(), decoded[0])
        assertEquals(0xD8.toByte(), decoded[1])
        // The file on disk is SQLCipher, so the photo must not be readable from it.
        // `body_photo.bytes_b64` is a TEXT column, so what a plaintext database would
        // hold is the base64 of the JPEG, not its `FF D8 FF` magic: searching for the
        // magic tested nothing and, against ciphertext, matched three arbitrary bytes
        // roughly once per 16 MB of file, which is how it failed a full suite run on
        // 2026-11-27. The needle is now a long slice of the payload actually stored.
        val dbBytes = context.getDatabasePath(TEST_DATABASE).readBytes()
        val payload = photo.bytes_b64
        assertTrue(payload.length > PAYLOAD_NEEDLE * 2)
        val needle = payload.substring(payload.length / 2, payload.length / 2 + PAYLOAD_NEEDLE)
        assertFalse(
            containsBytes(dbBytes, needle.toByteArray(Charsets.US_ASCII)),
            "the photo's bytes must not be readable from the encrypted database file",
        )
        assertFalse(
            containsBytes(dbBytes, SQLITE_HEADER),
            "an encrypted database never carries the plain SQLite header",
        )

        viewModel.addPhoto(jpeg, "Side")
        waitFor("second photo") { container.store.body.countPhotos() == 2L }
        val newest = container.store.body.allPhotos().maxBy { it.happened_at }
        viewModel.openPhotoCompare(newest.id)
        waitFor("before and after") { viewModel.state.value.photoCompare != null }
        assertTrue(viewModel.state.value.photoCompare!!.daysApart >= 1)

        viewModel.deletePhoto(newest.id)
        waitFor("photo in trash") { container.store.body.countPhotos() == 1L }
        val photoTombstone = container.store.trash.active()
            .firstOrNull { it.entity_table == "body_photo" }
        assertNotNull(photoTombstone)
        runBlocking {
            container.store.trash.undo(app.openhealth.core.ids.TombstoneId(photoTombstone.id))
        }
        waitFor("photo restored") { container.store.body.countPhotos() == 2L }
    }

    private fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        for (index in 0..haystack.size - needle.size) {
            if (needle.indices.all { haystack[index + it] == needle[it] }) return true
        }
        return false
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF285AA0.toInt())
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
        return output.toByteArray()
    }

    private fun seedProfile(sex: String = "FEMALE", height: Double = 170.0) {
        val now = container.clock.now()
        runBlocking {
            container.store.profile.upsert(
                Profile(
                    id = "profile-test",
                    name = "Sam",
                    date_of_birth = "1990-04-12",
                    sex_at_birth = sex,
                    gender = null,
                    height_cm = height,
                    weight_kg = 80.0,
                    units = "metric",
                    zone_id = container.zone.id,
                    language = "en",
                    goal = "just_track",
                    activity_level = "MODERATELY_ACTIVE",
                    training_experience = "BEGINNER",
                    weight_neutral = false,
                    intake_tier = 1L,
                    created_at = now.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                    source_id = AppContainer.MANUAL_SOURCE.value,
                    revision = 1L,
                ),
            )
            container.preferences.setModules(HealthModule.entries.toSet())
            container.preferences.markOnboardingComplete(1)
        }
        viewModel.refresh()
        waitFor("profile") { viewModel.state.value.vitals != null }
    }

    private fun waitFor(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
        val reached = runCatching {
            compose.waitUntil(timeoutMillis = timeoutMillis, condition = condition)
        }.isSuccess
        assertTrue(reached, "timed out waiting for $what")
    }

    private class FakeDocuments : DocumentStore {
        val written = mutableMapOf<Uri, ByteArray>()

        override fun write(uri: Uri, bytes: ByteArray) {
            written[uri] = bytes
        }

        override fun read(uri: Uri): ByteArray = written[uri] ?: ByteArray(0)
    }

    private companion object {
        const val TEST_DATABASE = "vitals-exit.db"
        const val TEST_VAULT = "openhealth.vault.b7-test"

        /** Long enough that a chance match in ciphertext is not a thing that happens. */
        const val PAYLOAD_NEEDLE = 64
        val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
