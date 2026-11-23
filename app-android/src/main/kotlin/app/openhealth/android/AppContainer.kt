package app.openhealth.android

import android.content.Context
import app.openhealth.android.data.AndroidDatabaseFactory
import app.openhealth.android.data.AndroidSecretVault
import app.openhealth.android.documents.AndroidDocumentStore
import app.openhealth.android.preferences.PreferencesStore
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
class AppContainer(context: Context) {
    val context: Context = context.applicationContext
    private val appContext = this.context
    private val vault = AndroidSecretVault(appContext)
    private val opened = AndroidDatabaseFactory(appContext, vault).open()

    val store: LocalStore = LocalStore(opened.driver, opened.db)
    val clock: ClockSeam = SystemClock
    val ids: IdGenerator = RandomIdGenerator()
    val preferences = PreferencesStore(store.settings, store.profile)
    val export = ExportService(store.driver, store.db, Argon2ExportCipher(), clock)
    val documents = AndroidDocumentStore(appContext)
    val zone: TimeZone = TimeZone.currentSystemDefault()
    val today: LocalDate get() = kotlin.time.Clock.System.now()
        .toLocalDateTime(zone).date

    val versionName: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull().orEmpty()

    init {
        ensureManualSource()
    }

    /**
     * The one source a manual entry names. It is written only when absent: re-upserting
     * it on every launch would rebuild every rollup through the source invalidator.
     */
    private fun ensureManualSource() {
        if (store.sources.byId(MANUAL_SOURCE) != null) return
        store.sources.upsert(
            Source(
                id = MANUAL_SOURCE,
                kind = SourceKind.MANUAL,
                label = "Manual",
                permissions = SourcePermission.entries.toSet(),
            ),
        )
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
        val stats = CorpusGenerator(store.db, ids, clock).generate(
            CorpusRequest(
                days = days,
                seed = 42L,
                endDate = today,
                zone = zone,
                profileId = profileId,
                writeProfile = writeProfile,
            ),
        )
        store.engine.rebuildAll()
        return stats.observations
    }

    fun close() {
        store.close()
    }

    companion object {
        const val DEMO_DAYS = 90
        val MANUAL_SOURCE = app.openhealth.core.ids.SourceId("manual")
    }
}
