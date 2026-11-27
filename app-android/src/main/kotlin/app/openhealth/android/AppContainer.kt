package app.openhealth.android

import android.content.Context
import app.openhealth.android.data.AndroidDatabaseFactory
import app.openhealth.android.data.AndroidSecretVault
import app.openhealth.android.documents.AndroidDocumentStore
import app.openhealth.android.preferences.PreferencesStore
import app.openhealth.core.exercise.ExerciseCatalogue
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.RandomIdGenerator
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.source.Source
import app.openhealth.core.source.SourcePermission
import app.openhealth.data.LocalStore
import app.openhealth.data.corpus.CorpusGenerator
import app.openhealth.data.corpus.CorpusRequest
import app.openhealth.data.export.Argon2ExportCipher
import app.openhealth.data.export.ExportService
import app.openhealth.data.repo.SettingKeys
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The composition root B3 owns (`BUILD-ROADMAP.md` §B3). It opens the encrypted store
 * once, seeds the one manual source every layer-4 row names, and hands the repositories
 * to the screens through [app.openhealth.android.ui.AppViewModel].
 *
 * Constructed by Koin in [OpenHealthApp]; nothing here performs network I/O.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class AppContainer(
    context: Context,
    documentStore: app.openhealth.android.documents.DocumentStore? = null,
    /**
     * The database and vault this container opens. The production app uses the defaults;
     * an instrumented test names its own, so a run never deletes or decrypts the record
     * on the owner's phone (`AGENTS.md` "Test device").
     */
    databaseName: String = AndroidDatabaseFactory.DEFAULT_DATABASE,
    vaultPreferences: String = DEFAULT_VAULT,
    /** Test seams: the bundled index, the one explicit network client and the health bus. */
    foodIndexOverride: app.openhealth.core.nutrition.BundledFoodIndex? = null,
    offClientOverride: app.openhealth.integrations.food.OpenFoodFactsClient? = null,
    healthBusOverride: app.openhealth.core.seam.HealthBus? = null,
) {
    val context: Context = context.applicationContext
    private val appContext = this.context
    private val vault = AndroidSecretVault(appContext, vaultPreferences)
    private val opened = lazy { AndroidDatabaseFactory(appContext, vault, databaseName).open() }

    /**
     * Opening SQLCipher and running the integrity check is local work, but it is not
     * free: it happens on the first access, on an IO thread, after the first frame, so
     * the splash and the cold-start budget are not held hostage by the database.
     */
    private val storeLazy = lazy {
        val local = LocalStore(opened.value.driver, opened.value.db)
        ensureManualSource(local)
        ensureImportSource(local)
        ensurePhoneSource(local)
        ensureRecordingSource(local)
        ensureStravaSource(local)
        ensureWatchSources(local)
        ensureExerciseLibrary(local)
        local.trash.purgeExpired()
        local
    }
    val store: LocalStore get() = storeLazy.value
    val foodIndex: app.openhealth.core.nutrition.BundledFoodIndex =
        foodIndexOverride ?: app.openhealth.android.nutrition.AssetFoodIndex(appContext)
    val offClient: app.openhealth.integrations.food.OpenFoodFactsClient =
        offClientOverride ?: app.openhealth.integrations.food.OpenFoodFactsClient(
            app.openhealth.android.nutrition.HttpUrlConnectionTransport(),
        )
    val clock: ClockSeam = SystemClock
    val ids: IdGenerator = RandomIdGenerator()

    /** Pairing, sync passes and the conflict list (B13). The server is optional. */
    val sync: app.openhealth.android.sync.SyncPresenter by lazy {
        app.openhealth.android.sync.SyncPresenter(
            context = appContext,
            store = store,
            vault = vault,
            clock = clock,
            // A conflict card shows values; weight-neutral mode hides the same metrics
            // here as on every other surface (`pr.md` §6.25, `DESIGN.md` §7.6).
            policy = { preferences.snapshot().displayPolicy },
        )
    }
    val preferences: PreferencesStore by lazy { PreferencesStore(store.settings, store.profile) }
    val restTimer: app.openhealth.core.seam.RestTimerSeam by lazy {
        app.openhealth.android.training.AndroidRestTimer(appContext)
    }

    /**
     * The one training presenter (`BUILD-ROADMAP.md` §B5). The phone screens and the
     * wrist bridge use the same instance, so a watch command and a thumb on the live
     * table take the same path through personal records, the rest alert and the rollup.
     */
    val training: app.openhealth.android.training.TrainingPresenter by lazy {
        app.openhealth.android.training.TrainingPresenter(
            store = store,
            clock = clock,
            zone = zone,
            restTimer = restTimer,
            ids = ids,
            manualSource = MANUAL_SOURCE,
            appVersion = versionName,
            freestyleTitle = appContext.getString(app.openhealth.R.string.train_freestyle),
            pastTitle = appContext.getString(app.openhealth.R.string.train_past_workout),
            watchLink = { open ->
                app.openhealth.android.wear.WatchGattService.sessionChanged(appContext, open)
            },
        )
    }

    /** The ids the phone already applied; a replayed watch message writes nothing twice. */
    val watchApplied: app.openhealth.android.wear.WatchAppliedStore by lazy {
        app.openhealth.android.wear.WatchAppliedStore(appContext)
    }

    /** What a watch may see: the open session, the routine list, rest and units. */
    val watchSnapshots: app.openhealth.android.wear.WatchSnapshotBuilder by lazy {
        app.openhealth.android.wear.WatchSnapshotBuilder(
            store = store,
            restTimer = restTimer,
            clock = clock,
            applied = watchApplied,
            weightUnit = { profileUnit() },
            weightIncrementKg = { if (profileUnit() == "lb") 5.0 * 0.45359237 else 2.5 },
        )
    }

    /** Applies a Wear OS or Connect IQ command (`docs/adr/0019`). */
    val watchCommands: app.openhealth.android.wear.WatchCommandHandler by lazy {
        app.openhealth.android.wear.WatchCommandHandler(
            store = store,
            training = training,
            clock = clock,
            zone = zone,
            ids = ids,
            applied = watchApplied,
            wearSource = WATCH_SOURCE,
            garminSource = CONNECT_IQ_SOURCE,
        )
    }

    private fun profileUnit(): String =
        if (app.openhealth.core.preferences.UnitSystem.fromKey(store.profile.active()?.units) ==
            app.openhealth.core.preferences.UnitSystem.IMPERIAL
        ) {
            "lb"
        } else {
            "kg"
        }
    val export: ExportService by lazy {
        ExportService(
            driver = store.driver,
            db = store.db,
            cipher = Argon2ExportCipher(),
            clock = clock,
            includePhotos = {
                store.settings.get(SettingKeys.BODY_PHOTOS_IN_EXPORT) == "true"
            },
        )
    }
    val documents = documentStore ?: AndroidDocumentStore(appContext)

    /** The PDF seam (`DESIGN.md` §8.7); reports are rendered on the device, offline. */
    val pdf: app.openhealth.core.seam.PdfSeam =
        app.openhealth.android.reports.AndroidPdfRenderer()

    /** Sleep, the clinical record, reports and the weekly review (`BUILD-ROADMAP.md` §B12). */
    val clinicalPresenter: app.openhealth.android.clinical.ClinicalPresenter by lazy {
        app.openhealth.android.clinical.ClinicalPresenter(
            context = appContext,
            store = store,
            preferences = preferences,
            zone = zone,
            ids = ids,
            clock = clock,
            pdf = pdf,
            appVersion = versionName,
        )
    }

    /** The health-bus seam's Android implementation; read-only (Tech-Stack.md §8). */
    val healthBus: app.openhealth.core.seam.HealthBus =
        healthBusOverride ?: app.openhealth.android.activity.AndroidHealthBus(appContext)

    /** Standard-GATT straps, scales and cuffs (pr.md §6.18). */
    val ble: app.openhealth.android.activity.BleHealthManager by lazy {
        app.openhealth.android.activity.BleHealthManager(
            context = appContext,
            store = store,
            clock = clock,
            zone = zone,
            ids = ids,
            appVersion = versionName,
        )
    }

    /** The Devices surface and its imports. */
    val activity: app.openhealth.android.activity.ActivityPresenter by lazy {
        app.openhealth.android.activity.ActivityPresenter(
            context = appContext,
            store = store,
            clock = clock,
            zone = zone,
            ids = ids,
            appVersion = versionName,
            healthBus = healthBus,
            ble = ble,
            documents = documents,
            manualSource = MANUAL_SOURCE,
        ).also { presenter ->
            presenter.phoneSensorPresent = hasStepCounter()
        }
    }

    /** The PMTiles map packs (`Tech-Stack.md` §8); never downloaded without an action. */
    val mapPacks: app.openhealth.android.activity.MapPackStore by lazy {
        app.openhealth.android.activity.MapPackStore(appContext)
    }

    /** Cardio, GPS and yoga (`pr.md` §6.7, §6.11). */
    val activityLog: app.openhealth.android.activity.ActivityLogPresenter by lazy {
        app.openhealth.android.activity.ActivityLogPresenter(
            context = appContext,
            store = store,
            clock = clock,
            zone = zone,
            ids = ids,
            appVersion = versionName,
            manualSource = MANUAL_SOURCE,
            recordingSource = RECORDING_SOURCE,
            importSource = IMPORT_SOURCE,
            stravaSource = STRAVA_SOURCE,
            garminSource = app.openhealth.android.activity.ActivityPresenter.GARMIN_SOURCE,
        )
    }

    /** A tracker bound to the store, for the foreground service and the cold-start catch-up. */
    fun newStepTracker(): app.openhealth.android.activity.StepTracker =
        app.openhealth.android.activity.StepTracker(
            context = appContext,
            store = store,
            clock = clock,
            zone = zone,
            ids = ids,
            appVersion = versionName,
        )

    private fun hasStepCounter(): Boolean {
        val manager = appContext.getSystemService(Context.SENSOR_SERVICE)
            as? android.hardware.SensorManager ?: return false
        return manager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null
    }

    /**
     * The phone's own step sensor is a source in its own right, so a day reported by
     * both it and a watch is deduplicated like any other pair (pr.md §7.6).
     */
    private fun ensurePhoneSource(local: LocalStore) {
        val id = app.openhealth.android.activity.StepTracker.PHONE_SOURCE
        if (local.sources.byId(id) != null) return
        local.sources.upsert(
            app.openhealth.core.source.Source(
                id = id,
                kind = SourceKind.DEVICE,
                label = appContext.getString(app.openhealth.R.string.devices_phone_source),
                family = "phone",
                device = null,
                permissions = setOf(
                    SourcePermission.READ_STEPS,
                    SourcePermission.READ_DISTANCE,
                ),
            ),
        )
    }
    val zone: TimeZone = TimeZone.currentSystemDefault()
    val today: LocalDate get() = kotlin.time.Clock.System.now()
        .toLocalDateTime(zone).date

    val versionName: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /**
     * The one source a manual entry names. It is written only when absent: re-upserting
     * it on every launch would rebuild every rollup through the source invalidator.
     */
    private fun ensureManualSource(local: LocalStore) {
        if (local.sources.byId(MANUAL_SOURCE) != null) return
        local.sources.upsert(
            Source(
                id = MANUAL_SOURCE,
                kind = SourceKind.MANUAL,
                label = "Manual",
                permissions = SourcePermission.entries.toSet(),
            ),
        )
    }

    /** The source a GPS recording on this phone names (`pr.md` §6.11, §6.18). */
    private fun ensureRecordingSource(local: LocalStore) {
        if (local.sources.byId(RECORDING_SOURCE) != null) return
        local.sources.upsert(
            Source(
                id = RECORDING_SOURCE,
                kind = SourceKind.DEVICE,
                label = "Recorded on this phone",
                family = "phone",
                permissions = setOf(
                    SourcePermission.READ_DISTANCE,
                    SourcePermission.READ_ENERGY,
                    SourcePermission.READ_HEART_RATE,
                ),
            ),
        )
    }

    /** A Strava bulk export is its own source family and never the live API. */
    private fun ensureStravaSource(local: LocalStore) {
        if (local.sources.byId(STRAVA_SOURCE) != null) return
        local.sources.upsert(
            Source(
                id = STRAVA_SOURCE,
                kind = SourceKind.STRAVA,
                label = "Strava export",
                family = "strava",
                permissions = setOf(
                    SourcePermission.READ_DISTANCE,
                    SourcePermission.READ_ENERGY,
                    SourcePermission.READ_HEART_RATE,
                ),
            ),
        )
    }

    /**
     * Two wrist sources with distinct provenance (`docs/adr/0019`). A Wear set is a
     * device source with family `watch`; a Connect IQ set is a Garmin source, so it joins
     * the existing `garmin` family for dedup and is never counted twice against a FIT file.
     */
    private fun ensureWatchSources(local: LocalStore) {
        if (local.sources.byId(WATCH_SOURCE) == null) {
            local.sources.upsert(
                Source(
                    id = WATCH_SOURCE,
                    kind = SourceKind.DEVICE,
                    label = "Wear OS watch",
                    family = "watch",
                    device = "wear",
                    permissions = setOf(
                        SourcePermission.READ_HEART_RATE,
                        SourcePermission.WRITE_EXERCISE,
                    ),
                ),
            )
        }
        if (local.sources.byId(CONNECT_IQ_SOURCE) == null) {
            local.sources.upsert(
                Source(
                    id = CONNECT_IQ_SOURCE,
                    kind = SourceKind.GARMIN,
                    label = "Garmin Connect IQ",
                    family = "garmin",
                    device = "connect-iq",
                    permissions = setOf(
                        SourcePermission.READ_HEART_RATE,
                        SourcePermission.WRITE_EXERCISE,
                    ),
                ),
            )
        }
    }

    /** The source a CSV or file import names, so it is never labelled as a manual entry. */
    private fun ensureImportSource(local: LocalStore) {
        if (local.sources.byId(IMPORT_SOURCE) != null) return
        local.sources.upsert(
            Source(
                id = IMPORT_SOURCE,
                kind = SourceKind.CSV,
                label = "Imported file",
                permissions = setOf(SourcePermission.WRITE_WEIGHT),
            ),
        )
    }

    /**
     * The bundled exercise library and the source it names (`NOTICE.md`), written into the
     * encrypted store once. 871 rows in one transaction is a sub-second local write, and it
     * happens on the same IO path that opened the store, never before the first frame
     * (Tech-Stack.md §12).
     */
    private fun ensureExerciseLibrary(local: LocalStore) {
        if (local.settings.get(SettingKeys.EXERCISE_CATALOGUE) == ExerciseCatalogue.VERSION) return
        if (local.sources.byId(LIBRARY_SOURCE) == null) {
            local.sources.upsert(
                Source(
                    id = LIBRARY_SOURCE,
                    kind = SourceKind.MANUAL,
                    label = ExerciseCatalogue.SOURCE_LABEL,
                    permissions = setOf(SourcePermission.WRITE_EXERCISE),
                ),
            )
        }
        local.training.seedCatalogue(
            exercises = ExerciseCatalogue.bundled,
            sourceId = LIBRARY_SOURCE.value,
            nowMillis = clock.now().toEpochMilliseconds(),
        )
        local.settings.put(SettingKeys.EXERCISE_CATALOGUE, ExerciseCatalogue.VERSION)
    }

    /** Another write path that must name provenance: the manual method for a typed value. */
    fun manualMethod(): MeasurementMethod = MeasurementMethod.MANUAL_ENTRY

    /**
     * Demo mode (`pr.md` §6.1): 90 days of realistic sample data through the same
     * repositories the app uses. It never rewrites the person's profile.
     */
    fun addSampleData(
        days: Int = DEMO_DAYS,
        profileId: String,
        writeProfile: Boolean = false,
    ): Int {
        // A demo corpus is sample data, not the person's record: keep it out of the
        // change log so it can never reach the server or another device.
        store.sync.pauseCapture()
        val stats = try {
            CorpusGenerator(store.db, ids, clock).generate(
                CorpusRequest(
                    days = days,
                    seed = 42L,
                    endDate = today,
                    zone = zone,
                    profileId = profileId,
                    writeProfile = writeProfile,
                ),
            )
        } finally {
            store.sync.resumeCapture()
        }
        store.engine.rebuildAll()
        return stats.observations
    }

    /** True when the last restore left an automatic local snapshot behind. */
    fun hasPreRestoreSnapshot(): Boolean = snapshotFile().exists()

    /**
     * `pr.md` §6.29: a bulk import takes a local snapshot first. It is encrypted with a
     * passphrase held by the Keystore, so it is restorable without asking the person for
     * anything and never sits in clear on disk.
     */
    fun writePreRestoreSnapshot(): Boolean = runCatching {
        val bytes = export.export(snapshotPassphrase())
        snapshotFile().writeBytes(bytes)
        true
    }.getOrDefault(false)

    fun restorePreRestoreSnapshot(): Boolean = runCatching {
        val bytes = snapshotFile().readBytes()
        export.restore(bytes, snapshotPassphrase())
        snapshotFile().delete()
        true
    }.getOrDefault(false)

    private fun snapshotFile() = java.io.File(appContext.filesDir, PRE_RESTORE)

    private fun snapshotPassphrase(): CharArray = android.util.Base64.encodeToString(
        vault.getOrCreate(AndroidDatabaseFactory.DATABASE_PASSPHRASE),
        android.util.Base64.NO_WRAP,
    ).toCharArray()

    fun close() {
        if (storeLazy.isInitialized()) store.close()
    }

    companion object {
        const val DEMO_DAYS = 90
        const val DEFAULT_VAULT = "openhealth.vault"
        val MANUAL_SOURCE = app.openhealth.core.ids.SourceId("manual")
        val IMPORT_SOURCE = app.openhealth.core.ids.SourceId("file-import")
        val LIBRARY_SOURCE = app.openhealth.core.ids.SourceId("exercise-library")
        val RECORDING_SOURCE = app.openhealth.core.ids.SourceId("phone-activity")
        val STRAVA_SOURCE = app.openhealth.core.ids.SourceId("strava-archive")
        val WATCH_SOURCE = app.openhealth.core.ids.SourceId("wear")
        val CONNECT_IQ_SOURCE = app.openhealth.core.ids.SourceId("garmin-connect-iq")
        private const val PRE_RESTORE = "pre-restore.ohe"
    }
}
