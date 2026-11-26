package app.openhealth.android.activity

import android.content.Context
import android.net.Uri
import app.openhealth.android.documents.DocumentStore
import app.openhealth.core.activity.DeviceCapabilities
import app.openhealth.core.activity.DeviceKindName
import app.openhealth.core.activity.DeviceLinkState
import app.openhealth.core.activity.GarminDetector
import app.openhealth.core.activity.HealthBusImporter
import app.openhealth.core.activity.HealthBusTypes
import app.openhealth.core.activity.HealthImportContext
import app.openhealth.core.activity.PairedDevice
import app.openhealth.core.activity.SourceDay
import app.openhealth.core.activity.SourcePriorityPlan
import app.openhealth.core.activity.SyncedSource
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.HealthBus
import app.openhealth.core.source.DedupReason
import app.openhealth.core.source.Source
import app.openhealth.core.source.SourcePermission
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.LocalStore
import app.openhealth.data.ingestion.PayloadHash
import app.openhealth.integrations.activity.FitActivityReader
import app.openhealth.integrations.fit.FitFormatException
import app.openhealth.integrations.fit.FitImport
import app.openhealth.integrations.fit.FitImportContext
import app.openhealth.integrations.fit.FitImporter
import app.openhealth.integrations.fit.FitObservationMapper
import app.openhealth.integrations.fit.FitStepSource
import app.openhealth.ui.R as UiR
import app.openhealth.ui.screens.devices.BluetoothModel
import app.openhealth.ui.screens.devices.DevicesModel
import app.openhealth.ui.screens.devices.FoundDeviceModel
import app.openhealth.ui.screens.devices.GarminModel
import app.openhealth.ui.screens.devices.HealthConnectModel
import app.openhealth.ui.screens.devices.HealthProvideModel
import app.openhealth.ui.screens.devices.PairedDeviceModel
import app.openhealth.ui.screens.devices.PriorityModel
import app.openhealth.ui.screens.devices.PriorityRowModel
import app.openhealth.ui.screens.devices.ProvenanceLine
import app.openhealth.ui.screens.devices.StepsSourceModel
import app.openhealth.ui.screens.devices.WatchLinkModel
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The Devices surface and every write behind it (`BUILD-ROADMAP.md` §B8): the phone's
 * step source, the Health Connect bus, a Garmin FIT file, the three standard Bluetooth
 * profiles and the source priority list. Every import writes a raw record first and
 * states its provenance, and a priority change goes through the source repository, which
 * rebuilds every rollup because the winner of a day may have changed (`pr.md` §7.3,
 * §7.6).
 */
@Suppress(
    "TooManyFunctions",
    "CyclomaticComplexMethod",
    "LargeClass",
    "LongMethod",
    "MagicNumber",
    "LongParameterList",
)
class ActivityPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
    private val ids: IdGenerator,
    private val appVersion: String,
    private val healthBus: HealthBus,
    private val ble: BleHealthManager,
    private val documents: DocumentStore,
    private val manualSource: SourceId,
) {
    private fun now(): Instant = clock.now()
    private fun today(): LocalDate = now().toLocalDateTime(zone).date

    // ------------------------------------------------------------------ read model

    fun model(
        stepsModuleOn: Boolean = true,
        message: String? = null,
        found: List<FoundDeviceModel> = emptyList(),
    ): DevicesModel = DevicesModel(
        steps = stepsModel(stepsModuleOn),
        healthConnect = healthConnectModel(),
        garmin = garminModel(),
        bluetooth = bluetoothModel(found),
        watchLink = watchLinkModel(),
        priority = priorityModel(),
        note = context.getString(UiR.string.devices_note),
        message = message,
    )

    private fun stepsModel(stepsModuleOn: Boolean): StepsSourceModel {
        val date = today()
        val rollup = store.rollups.forDayAndType(date, ObservationType.STEPS)
        val observations = store.observations.forDayAndType(date, ObservationType.STEPS)
        val breakdown = SourceDay.breakdown(
            ObservationType.STEPS,
            date,
            observations,
            store.sources.registry(),
        )
        val tracking = store.devices.stepTrackingEnabled()
        val available = phoneSensorPresent
        val total = rollup?.value?.value ?: 0.0
        val sourceLine = when {
            rollup == null -> null
            breakdown.winningSource == manualSource ||
                breakdown.reason == DedupReason.MANUAL_OVERRIDE ->
                context.getString(UiR.string.devices_steps_manual)

            breakdown.winningSource != null -> context.getString(
                UiR.string.devices_steps_won,
                label(breakdown.winningSource!!),
            )

            else -> null
        }
        val losers = breakdown.losers.map { loser ->
            context.getString(
                UiR.string.devices_steps_other,
                label(loser.sourceId),
                formatSteps(loser.total),
            )
        }
        val last = observations.filterNot { it.excludedFromTrends }
            .maxByOrNull { it.provenance.recordedAt }
        val reasonNote = when (breakdown.reason) {
            DedupReason.GAP_FALLBACK -> breakdown.winningSource?.let { winner ->
                context.getString(
                    UiR.string.devices_steps_gap,
                    label(breakdown.contributions.first().sourceId),
                    label(winner),
                )
            }

            DedupReason.INCOMPLETE_DAY -> breakdown.winningSource?.let { winner ->
                context.getString(UiR.string.devices_steps_incomplete, label(winner))
            }

            else -> null
        }
        return StepsSourceModel(
            available = available,
            tracking = tracking,
            moduleOff = !stepsModuleOn,
            todayText = when {
                !stepsModuleOn -> context.getString(UiR.string.devices_steps_module_off)
                rollup == null -> context.getString(UiR.string.today_steps_empty_title)
                else -> context.getString(
                    UiR.string.devices_steps_today_number,
                    formatSteps(total),
                )
            },
            sourceLine = if (stepsModuleOn) sourceLine else null,
            reasonNote = if (stepsModuleOn) reasonNote else null,
            otherSources = if (stepsModuleOn) losers else emptyList(),
            lastReadingText = last?.let {
                context.getString(UiR.string.devices_steps_last, time(it.provenance.recordedAt))
            },
            provenance = breakdown.contributions.map { contribution ->
                val observation = observations.filter {
                    it.provenance.sourceId == contribution.sourceId
                }.maxByOrNull { it.provenance.recordedAt }
                val detail = observation?.let {
                    context.getString(
                        UiR.string.devices_provenance_detail,
                        sourceKindLabel(it),
                        time(it.provenance.recordedAt),
                        it.localDate.toString(),
                    )
                }.orEmpty()
                ProvenanceLine(
                    label = context.getString(
                        if (contribution.sourceId == breakdown.winningSource) {
                            UiR.string.devices_provenance_winner
                        } else {
                            UiR.string.devices_provenance_other
                        },
                        label(contribution.sourceId),
                    ),
                    detail = detail,
                    value = formatSteps(contribution.total),
                )
            },
            canCorrect = stepsModuleOn,
            permissionNote = if (!stepsModuleOn) {
                context.getString(UiR.string.devices_steps_module_off_body)
            } else if (available) {
                context.getString(UiR.string.devices_steps_permission)
            } else {
                context.getString(UiR.string.devices_steps_unavailable)
            },
        )
    }

    private fun healthConnectModel(): HealthConnectModel {
        val available = healthBus.isAvailable()
        val enabled = store.devices.healthConnectEnabled()
        val granted = store.devices.healthConnectGranted()
        val last = store.devices.syncBook().latest(HEALTH_CONNECT_SOURCE.value)
        val provides = listOf(
            HealthBusTypes.STEPS to UiR.string.devices_type_steps,
            HealthBusTypes.DISTANCE to UiR.string.devices_type_distance,
            HealthBusTypes.ACTIVE_CALORIES to UiR.string.devices_type_calories,
            HealthBusTypes.FLOORS to UiR.string.devices_type_floors,
            HealthBusTypes.HEART_RATE to UiR.string.devices_type_heart_rate,
            HealthBusTypes.RESTING_HEART_RATE to UiR.string.devices_type_resting_heart_rate,
            HealthBusTypes.SLEEP to UiR.string.devices_type_sleep,
            HealthBusTypes.EXERCISE to UiR.string.devices_type_exercise,
            HealthBusTypes.WEIGHT to UiR.string.devices_type_weight,
            HealthBusTypes.BODY_FAT to UiR.string.devices_type_body_fat,
        ).map { (key, labelRes) ->
            val permission = AndroidHealthBus.permissionFor(key)
            HealthProvideModel(
                label = context.getString(labelRes),
                granted = permission != null && permission in granted,
            )
        }
        val status = when {
            !available -> context.getString(UiR.string.devices_health_connect_unavailable)
            !enabled -> context.getString(UiR.string.devices_health_connect_off)
            else -> context.getString(
                UiR.string.devices_health_connect_on,
                provides.count { it.granted },
                provides.size,
            )
        }
        return HealthConnectModel(
            available = available,
            enabled = enabled,
            grantedCount = provides.count { it.granted },
            totalCount = provides.size,
            statusText = status,
            lastSyncText = last?.let {
                context.getString(UiR.string.devices_health_connect_last, time(it.at))
            } ?: context.getString(UiR.string.devices_health_connect_never),
            provides = provides,
            historyImported = store.devices.healthConnectBackfilled(),
            note = context.getString(UiR.string.devices_health_connect_body),
            vendorNote = context.getString(UiR.string.devices_health_connect_vendor),
        )
    }

    private fun garminModel(): GarminModel {
        val last = store.devices.syncBook().latest(GARMIN_SOURCE.value)
        return GarminModel(
            lastImportText = last?.let {
                context.getString(UiR.string.devices_garmin_last, time(it.at))
            },
            lastImportDetail = last?.detail,
            stepsFrom = when (last?.stepsFrom) {
                FitStepSource.MONITORING.name ->
                    context.getString(UiR.string.devices_garmin_steps_monitoring)

                FitStepSource.ACTIVITY.name ->
                    context.getString(UiR.string.devices_garmin_steps_activity)

                else -> null
            },
            note = context.getString(UiR.string.devices_garmin_body),
            readOnlyNote = context.getString(UiR.string.devices_garmin_readonly),
        )
    }

    private fun bluetoothModel(found: List<FoundDeviceModel>): BluetoothModel {
        val registry = store.devices.registry()
        val state = ble.state.value
        val devices = registry.devices.map { device ->
            PairedDeviceModel(
                id = device.id,
                name = device.name ?: context.getString(UiR.string.devices_bluetooth),
                kind = device.kind,
                kindLabel = kindLabel(device.kind),
                statusText = providesText(device.kind) + " · " + when {
                    state is DeviceLinkState.Connected && state.address == device.address ->
                        context.getString(UiR.string.devices_status_connected)

                    state is DeviceLinkState.Connecting && state.address == device.address ->
                        context.getString(UiR.string.devices_status_connecting)

                    state is DeviceLinkState.Reconnecting && state.address == device.address ->
                        context.getString(UiR.string.devices_status_reconnecting)

                    else -> context.getString(UiR.string.devices_status_idle)
                },
                lastSeenText = device.lastSeen()?.let {
                    context.getString(UiR.string.devices_last_seen, time(it))
                },
                providesText = providesText(device.kind),
                autoConnect = device.autoConnect,
            )
        }
        return BluetoothModel(
            available = ble.isAvailable(),
            needsPermission = !ble.hasPermissions(),
            scanning = state is DeviceLinkState.Scanning,
            devices = devices,
            found = found,
            liveHeartRate = ble.heartRate.value,
            note = context.getString(UiR.string.devices_bluetooth_note),
            offNote = if (ble.isAvailable()) {
                null
            } else {
                context.getString(UiR.string.devices_bluetooth_off)
            },
        )
    }

    private fun watchLinkModel(): WatchLinkModel = WatchLinkModel(
        enabled = store.settings.get(app.openhealth.data.repo.SettingKeys.WATCH_LINK) == "true",
        available = bluetoothAdvertiseAvailable(),
        note = context.getString(UiR.string.devices_watch_link_note),
    )

    private fun bluetoothAdvertiseAvailable(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        val advertise = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val connect = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return advertise && connect
    }

    /** One line for the Settings row, so it never says "None connected" when a source is. */
    fun summary(): String {
        val parts = buildList {
            if (store.devices.stepTrackingEnabled()) {
                add(
                    context.getString(UiR.string.devices_priority_phone),
                )
            }
            if (store.devices.healthConnectEnabled() &&
                store.devices.healthConnectGranted().isNotEmpty()
            ) {
                add(context.getString(UiR.string.devices_health_connect))
            }
            if (store.devices.registry().devices.isNotEmpty()) {
                add(
                    context.resources.getQuantityString(
                        UiR.plurals.devices_summary_bluetooth,
                        store.devices.registry().devices.size,
                        store.devices.registry().devices.size,
                    ),
                )
            }
            if (store.devices.syncBook().latest(GARMIN_SOURCE.value) != null) {
                add(context.getString(UiR.string.devices_garmin))
            }
        }
        return parts.joinToString(" · ").ifEmpty {
            context.getString(UiR.string.settings_devices_detail)
        }
    }

    private fun priorityModel(): PriorityModel {
        val sources = stepSources()
        val order = SourcePriorityPlan.order(sources, ObservationType.STEPS)
        val rows = order.mapIndexed { index, id ->
            val source = sources.single { it.id == id }
            PriorityRowModel(
                sourceId = id.value,
                label = source.label,
                detail = when (source.kind) {
                    SourceKind.MANUAL -> context.getString(UiR.string.devices_priority_manual)
                    SourceKind.GARMIN -> context.getString(UiR.string.devices_priority_family)
                    SourceKind.HEALTH_CONNECT ->
                        context.getString(UiR.string.devices_health_connect)
                    SourceKind.DEVICE -> context.getString(UiR.string.devices_priority_phone)
                    SourceKind.FITBIT -> context.getString(UiR.string.devices_priority_fitbit)
                    SourceKind.CSV -> context.getString(UiR.string.devices_priority_file)
                    else -> source.device ?: source.label
                },
                isFirst = index == 0,
                isLast = index == order.lastIndex,
                isManual = source.kind == SourceKind.MANUAL,
            )
        }
        return PriorityModel(
            rows = rows,
            typeLabel = context.getString(UiR.string.devices_priority_type),
            note = context.getString(UiR.string.devices_priority_note),
        )
    }

    private fun stepSources(): List<Source> {
        val known = setOf(StepTracker.PHONE_SOURCE, GARMIN_SOURCE, HEALTH_CONNECT_SOURCE)
        val withSteps = store.observations.forTypeBetween(
            ObservationType.STEPS,
            now() - 365.days,
            now().plus(1.days),
        ).map { it.provenance.sourceId }.toSet()
        return store.sources.all().filter { it.id in known || it.id in withSteps }
    }

    // ------------------------------------------------------------------ steps

    fun setStepTracking(enabled: Boolean) {
        store.devices.setStepTracking(enabled)
    }

    /** Today's steps as the person counted them. A manual entry wins the day. */
    fun correctToday(steps: Int) {
        val date = today()
        val at = now()
        val provenance = Provenance(
            sourceId = manualSource,
            time = TimeProvenance.of(at, at, zone),
            idempotencyKey = "manual-steps:$date:$steps:${at.toEpochMilliseconds()}",
            method = MeasurementMethod.MANUAL_ENTRY,
            device = null,
            appVersion = appVersion,
        )
        val observation = Observation(
            id = app.openhealth.core.ids.ObservationId(ids.newId()),
            type = ObservationType.STEPS,
            value = Measure(steps.toDouble(), UcumUnit.STEP),
            start = at,
            end = null,
            provenance = provenance,
            // A correction is a new manual figure, not an edit of the sensor's; the
            // sensor's rows stay untouched beside it, exactly as §7.6 asks.
            userVerified = true,
        )
        store.observations.record(observation)
    }

    /** "Leave this day out" without deleting it (`pr.md` §6.6, §6.28). */
    fun toggleExcludeToday(): Boolean {
        val date = today()
        val observations = store.observations.forDayAndType(date, ObservationType.STEPS)
        if (observations.isEmpty()) return false
        val excluding = observations.any { !it.excludedFromTrends }
        observations.forEach { store.observations.excludeFromTrends(it.id, excluding) }
        store.engine.rebuildDay(date)
        return excluding
    }

    // ------------------------------------------------------------------ health connect

    @Suppress("ReturnCount")
    suspend fun syncHealthConnect(backfill: Boolean): HealthSyncOutcome {
        if (!healthBus.isAvailable()) return HealthSyncOutcome(error = NOT_AVAILABLE)
        val granted = runCatching { healthBus.grantedPermissions() }.getOrDefault(emptySet())
        store.devices.setHealthConnectGranted(granted)
        if (granted.isEmpty()) return HealthSyncOutcome(error = NO_PERMISSION)
        ensureSource(
            HEALTH_CONNECT_SOURCE,
            SourceKind.HEALTH_CONNECT,
            context.getString(UiR.string.devices_health_connect),
            setOf(
                SourcePermission.READ_STEPS,
                SourcePermission.READ_DISTANCE,
                SourcePermission.READ_ENERGY,
                SourcePermission.READ_HEART_RATE,
                SourcePermission.READ_SLEEP,
                SourcePermission.READ_WEIGHT,
                SourcePermission.READ_BODY_COMPOSITION,
            ),
            family = "health-connect",
        )
        val to = now()
        val last = store.devices.syncBook().latest(HEALTH_CONNECT_SOURCE.value)
        // An incremental sync always re-reads the last few days rather than starting at
        // the previous sync. A watch that uploads a backlog writes records whose own
        // start times are older than the sync that ran in between, and a window that
        // began at the last sync would step over them for good. Re-reading costs
        // nothing: every reading carries an idempotency key, so one already stored
        // inserts nothing (`pr.md` §7.1, §7.7).
        val from = if (backfill) {
            to - BACKFILL_DAYS.days
        } else {
            minOf(last?.at ?: to, to) - INCREMENTAL_DAYS.days
        }
        var imported = 0
        val skipped = mutableSetOf<String>()
        HealthBusTypes.all.forEach { key ->
            val readings = runCatching {
                healthBus.read(key, from.toEpochMilliseconds(), to.toEpochMilliseconds())
            }.getOrDefault(emptyList())
            if (readings.isEmpty()) return@forEach
            // `pr.md` §6.18: Garmin over the bus and Garmin over a FIT file are one
            // family. Health Connect does not name the writing app, but it does carry
            // the device, so a Garmin device's readings get the Garmin family and
            // collapse with a FIT import of the same watch.
            val groups = readings.groupBy { reading ->
                GarminDetector.isGarmin(reading.metadata["device"])
            }
            groups.forEach { (garmin, groupReadings) ->
                if (garmin) {
                    // Only a phone that actually carries Garmin data gains the row.
                    ensureSource(
                        GARMIN_HC_SOURCE,
                        SourceKind.GARMIN,
                        context.getString(UiR.string.devices_garmin_health_connect),
                        setOf(
                            SourcePermission.READ_STEPS,
                            SourcePermission.READ_DISTANCE,
                            SourcePermission.READ_ENERGY,
                            SourcePermission.READ_HEART_RATE,
                            SourcePermission.READ_SLEEP,
                            SourcePermission.READ_WEIGHT,
                            SourcePermission.READ_BODY_COMPOSITION,
                        ),
                        family = "garmin",
                    )
                }
                val sourceId = if (garmin) GARMIN_HC_SOURCE else HEALTH_CONNECT_SOURCE
                val sourceKind = if (garmin) SourceKind.GARMIN else SourceKind.HEALTH_CONNECT
                val payload = groupReadings.joinToString(separator = "\n") {
                    "${it.type}:${it.startEpochMillis}:${it.endEpochMillis}:${it.value}:" +
                        "${it.unit}:${it.metadata["device"]}:${it.metadata["uid"]}"
                }
                val rawId = RawRecordId(ids.newId())
                val raw = RawRecord(
                    id = rawId,
                    sourceId = sourceId,
                    sourceKind = sourceKind,
                    receivedAt = to,
                    recordedAt = to,
                    happenedAt = null,
                    payloadHash = PayloadHash.sha256(payload),
                    idempotencyKey = "hc:${sourceId.value}:$key:" +
                        from.toEpochMilliseconds() + ":" + to.toEpochMilliseconds(),
                    schemaVersion = RAW_SCHEMA,
                    device = groupReadings.firstNotNullOfOrNull { it.metadata["device"] },
                    appVersion = appVersion,
                    payload = if (payload.length <= MAX_RAW_PAYLOAD) payload else null,
                )
                val result = HealthBusImporter.toObservations(
                    groupReadings,
                    HealthImportContext(
                        sourceId = sourceId,
                        zone = zone,
                        recordedAt = to,
                        appVersion = appVersion,
                        rawRecordId = rawId,
                        ids = ids,
                    ),
                )
                skipped += result.skipped
                val inserted = store.observations.recordAll(result.observations, listOf(raw))
                imported += inserted
                if (inserted > 0 || backfill) {
                    store.devices.recordSync(
                        SyncedSource(
                            sourceId = sourceId.value,
                            atEpochMillis = to.toEpochMilliseconds(),
                            records = inserted,
                        ),
                    )
                    store.sources.markSynced(sourceId, to)
                }
            }
        }
        if (backfill) store.devices.setHealthConnectBackfilled(true)
        store.devices.setHealthConnectEnabled(true)
        return HealthSyncOutcome(imported = imported, skipped = skipped.toList())
    }

    fun setHealthConnectEnabled(enabled: Boolean) {
        store.devices.setHealthConnectEnabled(enabled)
    }

    // ------------------------------------------------------------------ Garmin FIT

    @Suppress("ReturnCount")
    fun importFit(uri: Uri): FitOutcome {
        val bytes = runCatching { documents.read(uri) }
            .getOrElse { return FitOutcome(error = UNREADABLE) }
        return importFitFromBytes(bytes)
    }

    /**
     * The import itself, over bytes already in hand. The document picker path and the
     * instrumented test both call it, so a device test cannot exercise a different path
     * from the one a person uses.
     */
    @Suppress("ReturnCount")
    fun importFitFromBytes(bytes: ByteArray): FitOutcome {
        val hash = PayloadHash.sha256(bytes)
        // A file already in layer 1 is not parsed again, and the screen does not claim a
        // fresh import it did not make.
        store.raw.byHash(hash)?.let { existing ->
            return FitOutcome(
                imported = 0,
                error = null,
                alreadyImported = true,
                importedAt = existing.recordedAt,
            )
        }
        val imported = runCatching { FitImporter.import(bytes, zone) }
            .getOrElse { error ->
                return FitOutcome(
                    error = if (error is FitFormatException) INVALID_FILE else UNREADABLE,
                )
            }
        if (imported.isEmpty) return FitOutcome(error = NOTHING_IN_FILE)
        ensureSource(
            GARMIN_SOURCE,
            SourceKind.GARMIN,
            context.getString(UiR.string.devices_garmin),
            setOf(
                SourcePermission.READ_STEPS,
                SourcePermission.READ_HEART_RATE,
                SourcePermission.READ_DISTANCE,
                SourcePermission.READ_ENERGY,
            ),
            family = "garmin",
        )
        val at = now()
        val rawId = RawRecordId(ids.newId())
        val summary = summarize(imported)
        val raw = RawRecord(
            id = rawId,
            sourceId = GARMIN_SOURCE,
            sourceKind = SourceKind.GARMIN,
            receivedAt = at,
            recordedAt = at,
            happenedAt = imported.steps.firstOrNull()?.start,
            payloadHash = hash,
            idempotencyKey = "fit:$hash",
            schemaVersion = RAW_SCHEMA,
            device = imported.device,
            appVersion = appVersion,
            payload = summary,
        )
        val observations = FitObservationMapper.toObservations(
            imported,
            FitImportContext(
                sourceId = GARMIN_SOURCE,
                zone = zone,
                recordedAt = at,
                device = imported.device,
                appVersion = appVersion,
                rawRecordId = rawId,
                ids = ids,
            ),
        )
        val inserted = store.observations.recordAll(observations, listOf(raw))
        runCatching {
            FitActivityReader.from(imported).forEachIndexed { index, parsed ->
                val weight = store.profile.active()?.weight_kg
                val record = app.openhealth.core.activity.ActivityEnergy.withEstimate(
                    app.openhealth.core.activity.ActivityRecord(
                        id = ids.newId(),
                        type = parsed.type,
                        source = app.openhealth.core.activity.ActivitySource.IMPORTED,
                        startedAt = parsed.startedAt,
                        endedAt = parsed.endedAt,
                        localDate = parsed.startedAt.toLocalDateTime(zone).date,
                        zoneId = zone.id,
                        distanceM = parsed.distanceM,
                        averageHeartRate = parsed.averageHeartRate,
                        maxHeartRate = parsed.maximumHeartRate,
                        averageCadence = parsed.averageCadence,
                        averagePowerWatts = parsed.averagePowerWatts,
                        energyKcal = parsed.calories,
                        name = parsed.name,
                        points = parsed.points,
                    ),
                    weight,
                )
                store.activities.record(
                    activity = record,
                    sourceId = GARMIN_SOURCE.value,
                    idempotencyKey = "fit-activity:$hash:$index",
                    rawRecordId = rawId.value,
                    device = imported.device,
                    appVersion = appVersion,
                    now = at,
                )
            }
        }
        val detail = context.getString(
            UiR.string.devices_garmin_detail,
            formatSteps(imported.stepTotal.toDouble()),
            imported.activities.firstOrNull()?.let { activity ->
                context.getString(
                    UiR.string.devices_garmin_activity,
                    sportLabel(activity.sport),
                    activity.durationMinutes.toInt(),
                )
            } ?: context.getString(UiR.string.devices_garmin_no_activity),
            imported.heartRates.size,
        )
        store.devices.recordSync(
            SyncedSource(
                sourceId = GARMIN_SOURCE.value,
                atEpochMillis = at.toEpochMilliseconds(),
                records = inserted,
                steps = imported.stepTotal,
                activities = imported.activities.size,
                detail = detail,
                stepsFrom = imported.stepSource.name,
            ),
        )
        store.sources.markSynced(GARMIN_SOURCE, at)
        return FitOutcome(
            imported = inserted,
            activities = imported.activities.size,
            steps = imported.stepTotal,
            activityMinutes = imported.activities.sumOf { it.durationMinutes }.toInt(),
            heartRates = imported.heartRates.size,
        )
    }

    private fun summarize(import: FitImport): String = buildString {
        append("messages=").append(import.messageCount)
        append(";device=").append(import.device.orEmpty())
        append(";activities=").append(import.activities.size)
        append(";steps=").append(import.stepTotal)
        append(";stepsFrom=").append(import.stepSource.name)
        append(";heartRates=").append(import.heartRates.size)
        append(";profileVersion=").append(import.profileVersion)
    }

    // ------------------------------------------------------------------ Bluetooth

    /** Pairs a discovered device and registers its source before the first reading. */
    fun pair(found: FoundDeviceModel): Boolean {
        val kind = found.kind
        val sourceId = DeviceCapabilities.sourceIdFor(DeviceKindName.toKind(kind), found.address)
        // One device is one row. Re-pairing a strap that was forgotten must not leave two
        // entries fighting to reconnect to the same address.
        store.devices.registry().byAddress(found.address)?.let { existing ->
            store.devices.unpair(existing.id)
        }
        val device = PairedDevice(
            id = ids.newId(),
            address = found.address,
            name = found.name,
            kind = kind,
            sourceId = sourceId.value,
            lastSeenAt = now().toEpochMilliseconds(),
        )
        store.devices.pair(device)
        store.sources.upsert(DeviceCapabilities.source(device))
        return true
    }

    fun unpair(id: String) {
        val device = store.devices.registry().byId(id) ?: return
        ble.disconnect(device.address)
        store.devices.unpair(id)
    }

    fun connect(id: String) {
        val device = store.devices.registry().byId(id) ?: return
        device.deviceKind?.let { ble.connect(device.address, it) }
    }

    // ------------------------------------------------------------------ priority

    fun movePriority(sourceId: String, up: Boolean) {
        val sources = store.sources.all()
        val order = SourcePriorityPlan.order(stepSources(), ObservationType.STEPS)
        val moved = SourcePriorityPlan.move(order, SourceId(sourceId), up)
        if (moved == order) return
        val updated = SourcePriorityPlan.priorities(
            moved,
            ObservationType.STEPS,
            sources.associateBy(Source::id),
        )
        // One transaction, one rebuild: the winner of a day may have changed (§7.3, §7.6).
        store.sources.upsertAll(updated.values.toList())
    }

    // ------------------------------------------------------------------ helpers

    private fun ensureSource(
        id: SourceId,
        kind: SourceKind,
        label: String,
        permissions: Set<SourcePermission>,
        family: String,
    ) {
        if (store.sources.byId(id) != null) return
        store.sources.upsert(
            Source(
                id = id,
                kind = kind,
                label = label,
                family = family,
                permissions = permissions,
            ),
        )
    }

    private fun label(sourceId: SourceId): String =
        store.sources.byId(sourceId)?.label ?: sourceId.value

    private fun sourceKindLabel(observation: Observation): String = when {
        observation.provenance.method == MeasurementMethod.MANUAL_ENTRY ->
            context.getString(UiR.string.devices_priority_manual)

        observation.provenance.method == MeasurementMethod.FILE_IMPORT ->
            context.getString(UiR.string.devices_garmin)

        observation.provenance.device != null -> observation.provenance.device!!
        else -> context.getString(UiR.string.devices_type_steps)
    }

    private fun kindLabel(kind: DeviceKindName): String = when (kind) {
        DeviceKindName.HEART_RATE -> context.getString(UiR.string.devices_kind_heart_rate)
        DeviceKindName.SCALE -> context.getString(UiR.string.devices_kind_scale)
        DeviceKindName.BLOOD_PRESSURE -> context.getString(UiR.string.devices_kind_pressure)
    }

    private fun providesText(kind: DeviceKindName): String = when (kind) {
        DeviceKindName.HEART_RATE -> context.getString(UiR.string.devices_provides_heart)
        DeviceKindName.SCALE -> context.getString(UiR.string.devices_provides_weight)
        DeviceKindName.BLOOD_PRESSURE -> context.getString(UiR.string.devices_provides_pressure)
    }

    private fun sportLabel(sport: Long?): String = when (sport) {
        1L -> context.getString(UiR.string.devices_sport_run)
        11L -> context.getString(UiR.string.devices_sport_walk)
        17L -> context.getString(UiR.string.devices_sport_hike)
        2L -> context.getString(UiR.string.devices_sport_ride)
        else -> context.getString(UiR.string.devices_sport_workout)
    }

    private fun formatSteps(value: Double): String =
        java.text.NumberFormat.getIntegerInstance().format(value.toLong())

    private fun time(at: Instant): String {
        val formatter = android.text.format.DateFormat.getTimeFormat(context)
        formatter.timeZone = java.util.TimeZone.getTimeZone(zone.id)
        return formatter.format(java.util.Date(at.toEpochMilliseconds()))
    }

    /** True when the phone advertises a hardware counter; set by the container at start. */
    var phoneSensorPresent: Boolean = false

    companion object {
        val GARMIN_SOURCE = SourceId("garmin-fit")
        val HEALTH_CONNECT_SOURCE = SourceId("health-connect")
        val GARMIN_HC_SOURCE = SourceId("garmin-hc")
        private const val BACKFILL_DAYS = 365
        private const val INCREMENTAL_DAYS = 7
        private const val RAW_SCHEMA = 1
        private const val MAX_RAW_PAYLOAD = 512 * 1024

        const val NOT_AVAILABLE = "not_available"
        const val NO_PERMISSION = "no_permission"
        const val UNREADABLE = "unreadable"
        const val INVALID_FILE = "invalid_file"
        const val NOTHING_IN_FILE = "nothing_in_file"
    }
}

data class HealthSyncOutcome(
    val imported: Int = 0,
    val skipped: List<String> = emptyList(),
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

data class FitOutcome(
    val imported: Int = 0,
    val activities: Int = 0,
    val steps: Long = 0,
    val activityMinutes: Int = 0,
    val heartRates: Int = 0,
    val error: String? = null,
    val alreadyImported: Boolean = false,
    val importedAt: Instant? = null,
) {
    val ok: Boolean get() = error == null
}
