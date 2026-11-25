package app.openhealth.android.activity

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import app.openhealth.core.activity.BleRetry
import app.openhealth.core.activity.BleWritePolicy
import app.openhealth.core.activity.DeviceCapabilities
import app.openhealth.core.activity.DeviceKind
import app.openhealth.core.activity.DeviceLinkState
import app.openhealth.core.activity.GattCodec
import app.openhealth.core.activity.GattMeasurement
import app.openhealth.core.activity.MeasurementBuffer
import app.openhealth.core.activity.PairedDevice
import app.openhealth.core.activity.normaliseUuid
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.source.SourcePermission
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.LocalStore
import app.openhealth.data.ingestion.PayloadHash
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone

/**
 * Standard-GATT Bluetooth (`Tech-Stack.md` §8, `pr.md` §6.18): heart-rate straps,
 * scales and blood-pressure cuffs, all through the three Bluetooth SIG profiles, so any
 * conforming device works and no vendor app is involved.
 *
 * Nothing is lost on a disconnect: a decoded measurement is written to the database the
 * moment it arrives, and only a write that fails is buffered and flushed on reconnect.
 * The reconnect itself follows [BleRetry]. A workout's sets are already persisted by the
 * training repository, so a strap dropping out costs heart-rate coverage, never a set.
 */
@Suppress("TooManyFunctions")
class BleHealthManager(
    private val context: Context,
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
    private val ids: IdGenerator,
    private val appVersion: String,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter

    private val connections = HashMap<String, BluetoothGatt>()
    private val buffer = MeasurementBuffer<BufferedReading>()
    private var scanCallback: ScanCallback? = null
    private var stateListener: ((DeviceLinkState) -> Unit)? = null
    private var foundListener: ((List<FoundDevice>) -> Unit)? = null

    private val _state = MutableStateFlow<DeviceLinkState>(DeviceLinkState.Idle)
    val state: StateFlow<DeviceLinkState> = _state.asStateFlow()

    private val _heartRate = MutableStateFlow<Int?>(null)

    /** The live heart rate, for the workout chip. Null when nothing is connected. */
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    fun onState(listener: (DeviceLinkState) -> Unit) {
        stateListener = listener
    }

    fun onFound(listener: (List<FoundDevice>) -> Unit) {
        foundListener = listener
    }

    fun isAvailable(): Boolean = adapter?.isEnabled == true

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    @Suppress("ReturnCount")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            emit(DeviceLinkState.Unavailable("Bluetooth is off"))
            return
        }
        if (!hasPermissions()) {
            emit(DeviceLinkState.Unavailable("Bluetooth permission is needed"))
            return
        }
        if (scanCallback != null) return
        emit(DeviceLinkState.Scanning())
        val callback = object : ScanCallback() {
            private val seen = LinkedHashMap<String, FoundDevice>()

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val services = result.scanRecord?.serviceUuids?.map {
                    normaliseUuid(it.uuid.toString())
                }
                    .orEmpty()
                val kind = DeviceKind.fromServices(services) ?: return
                val device = FoundDevice(
                    address = result.device.address,
                    name = result.scanRecord?.deviceName ?: result.device.name,
                    kind = kind,
                )
                seen[device.address] = device
                foundListener?.invoke(seen.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                emit(DeviceLinkState.Unavailable("Scan failed ($errorCode)"))
            }
        }
        scanCallback = callback
        runCatching {
            scanner.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                callback,
            )
        }.onFailure { emit(DeviceLinkState.Unavailable(it.message ?: "Scan failed")) }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val callback = scanCallback ?: return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        scanCallback = null
        if (_state.value is DeviceLinkState.Scanning) emit(DeviceLinkState.Idle)
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, kind: DeviceKind, attempt: Int = 1) {
        if (!hasPermissions()) {
            emit(DeviceLinkState.Unavailable("Bluetooth permission is needed"))
            return
        }
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: run {
            emit(DeviceLinkState.Unavailable("Device not found"))
            return
        }
        emit(DeviceLinkState.Connecting(address, attempt))
        val callback = LinkCallback(address, kind, attempt)
        runCatching {
            device.connectGatt(context, false, callback, BLUETOOTH_DEVICE_TRANSPORT_LE)
        }.onFailure { emit(DeviceLinkState.Unavailable(it.message ?: "Could not connect")) }
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        connections.remove(address)?.let { gatt ->
            runCatching {
                gatt.disconnect()
                gatt.close()
            }
        }
        if (_state.value.let { it is DeviceLinkState.Connected && it.address == address }) {
            emit(DeviceLinkState.Idle)
        }
    }

    /**
     * Writes everything the buffer holds as one batch. A batch that cannot be written
     * goes back into the buffer and is tried again on the next tick: a reading is
     * dropped only when the buffer overflows, never because one attempt failed.
     */
    fun flush() {
        val drained = buffer.drain()
        if (drained.isEmpty()) return
        if (!writeAll(drained)) drained.forEach(buffer::add)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        flush()
        stopScan()
        connections.values.forEach { gatt -> runCatching { gatt.close() } }
        connections.clear()
        handler.removeCallbacksAndMessages(null)
    }

    /** Called once when the app starts, so a paired strap reconnects by itself. */
    fun reconnectPaired() {
        store.devices.registry().devices.filter { it.autoConnect }.forEach { device ->
            val kind = device.deviceKind ?: return@forEach
            connect(device.address, kind)
        }
    }

    private inner class LinkCallback(
        private val address: String,
        private val kind: DeviceKind,
        private val attempt: Int,
    ) : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connections[address] = gatt
                emit(DeviceLinkState.Connected(address, kind))
                store.devices.registry().byAddress(address)?.let { paired ->
                    store.devices.touch(paired.id, clock.now())
                }
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                connections.remove(address)
                _heartRate.value = null
                // The link dropping is exactly when the batch must land: whatever the
                // strap sent since the last tick is written before anything else.
                flush()
                val paired = store.devices.registry().byAddress(address)
                val retry = paired != null && BleRetry.shouldRetry(attempt, paired.autoConnect)
                if (retry) {
                    val delay = BleRetry.delayFor(attempt).inWholeMilliseconds
                    emit(DeviceLinkState.Reconnecting(address, attempt + 1, delay))
                    handler.postDelayed({ connect(address, kind, attempt + 1) }, delay)
                } else {
                    emit(DeviceLinkState.Idle)
                }
                runCatching { gatt.close() }
            }
        }

        /** The characteristics still waiting for their CCCD write, in order. */
        private val pendingSubscriptions = ArrayDeque<BluetoothGattCharacteristic>()

        /**
         * Subscribes to every measurement characteristic the kind defines and the
         * device actually exposes. A scale with impedance sends its body composition on
         * a second characteristic, so subscribing only to the first would silently drop
         * body fat (`pr.md` §6.18, ADR 0013 §4).
         *
         * Android allows **one** GATT operation in flight per connection, so the
         * descriptor writes are queued and the next one starts when the previous one
         * reports back. Issuing them in a loop makes every write after the first return
         * false, which is how a second characteristic ends up never subscribed at all.
         */
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(java.util.UUID.fromString(kind.serviceUuid)) ?: return
            pendingSubscriptions.clear()
            kind.measurementUuids.forEach { uuid ->
                service.getCharacteristic(java.util.UUID.fromString(uuid))
                    ?.let(pendingSubscriptions::addLast)
            }
            subscribeNext(gatt)
            flush()
            scheduleFlush()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            subscribeNext(gatt)
        }

        @SuppressLint("MissingPermission")
        private fun subscribeNext(gatt: BluetoothGatt) {
            while (pendingSubscriptions.isNotEmpty()) {
                val characteristic = pendingSubscriptions.removeFirst()
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: continue
                val indication = characteristic.properties and
                    BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                descriptor.value = if (indication) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
                // Accepted: the next one waits for onDescriptorWrite.
                if (runCatching { gatt.writeDescriptor(descriptor) }.getOrDefault(false)) return
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            decode(characteristic.uuid.toString(), value, address)
        }
    }

    /** A batched write tick: heart rate at 1 Hz must not mean 3,600 DB writes an hour. */
    private val flushTick = object : Runnable {
        override fun run() {
            flush()
            handler.postDelayed(this, FLUSH_INTERVAL_MILLIS)
        }
    }

    private fun scheduleFlush() {
        handler.removeCallbacks(flushTick)
        handler.postDelayed(flushTick, FLUSH_INTERVAL_MILLIS)
    }

    /**
     * The decoder is chosen by the characteristic the packet arrived on, never by the
     * kind of device: a scale's two characteristics carry two different layouts.
     *
     * A streaming measurement joins the batch ([BleWritePolicy]); anything else is
     * written straight away and only joins the batch if that write failed.
     */
    private fun decode(characteristicUuid: String, value: ByteArray, address: String) {
        val decoded = runCatching {
            GattCodec.decode(characteristicUuid, value, zone)
        }.getOrNull() ?: return
        if (decoded is GattMeasurement.HeartRate) _heartRate.value = decoded.bpm
        val reading = BufferedReading(address, kind(decoded), decoded, clock.now())
        if (BleWritePolicy.isStreaming(decoded) || !writeAll(listOf(reading))) {
            buffer.add(reading)
        }
        // The batch lands on the next tick rather than retrying in a tight loop on the
        // GATT callback thread.
        scheduleFlush()
    }

    private fun kind(measurement: GattMeasurement): DeviceKind = when (measurement) {
        is GattMeasurement.HeartRate -> DeviceKind.HEART_RATE
        is GattMeasurement.Weight, is GattMeasurement.BodyComposition -> DeviceKind.SCALE
        is GattMeasurement.BloodPressure -> DeviceKind.BLOOD_PRESSURE
    }

    /**
     * Writes a batch of measurements under each device's own source, in one transaction,
     * so a minute of heart rate is one write and one day rebuild rather than sixty of
     * each. Every reading still gets its own layer-1 raw record, referenced from the
     * observation's provenance (`pr.md` §7.1).
     *
     * Returns false if nothing could be written; the caller keeps the batch buffered.
     */
    private fun writeAll(readings: List<BufferedReading>): Boolean = runCatching {
        if (readings.isEmpty()) return@runCatching true
        val observations = mutableListOf<Observation>()
        val raws = mutableListOf<RawRecord>()
        val touched = LinkedHashMap<String, Instant>()
        readings.forEach { reading ->
            val device = store.devices.registry().byAddress(reading.address)
            val sourceId = DeviceCapabilities.sourceIdFor(reading.kind, reading.address)
            val label = device?.name ?: "Bluetooth device"
            ensureSource(sourceId, label, device)
            val recordedAt = reading.at
            val payload = "device=${reading.address};kind=${reading.kind.name};" +
                "at=${reading.at.toEpochMilliseconds()};measurement=${reading.measurement}"
            val rawId = RawRecordId(ids.newId())
            val readingObservations =
                observationsFor(reading, sourceId, recordedAt, label, rawId)
            if (readingObservations.isEmpty()) return@forEach
            raws += RawRecord(
                id = rawId,
                sourceId = sourceId,
                sourceKind = SourceKind.API,
                receivedAt = recordedAt,
                recordedAt = recordedAt,
                happenedAt = reading.at,
                payloadHash = PayloadHash.sha256(payload),
                idempotencyKey = "ble-raw:${reading.address}:" +
                    PayloadHash.sha256(payload).take(RAW_KEY_CHARS),
                schemaVersion = RAW_SCHEMA,
                device = label,
                appVersion = appVersion,
                payload = payload,
            )
            observations += readingObservations
            device?.let { paired -> touched[paired.id] = recordedAt }
        }
        if (observations.isEmpty()) return@runCatching false
        store.observations.recordAll(observations, raws)
        touched.forEach { (id, at) -> store.devices.touch(id, at, reading = true) }
        true
    }.getOrDefault(false)

    private fun ensureSource(sourceId: SourceId, label: String, device: PairedDevice?) {
        if (store.sources.byId(sourceId) != null) return
        store.sources.upsert(
            app.openhealth.core.source.Source(
                id = sourceId,
                kind = app.openhealth.core.observation.SourceKind.API,
                label = label,
                family = "ble",
                device = device?.name,
                permissions = setOf(
                    SourcePermission.READ_HEART_RATE,
                    SourcePermission.READ_WEIGHT,
                    SourcePermission.READ_BODY_COMPOSITION,
                    SourcePermission.READ_BLOOD_PRESSURE,
                ),
            ),
        )
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun observationsFor(
        reading: BufferedReading,
        sourceId: SourceId,
        recordedAt: Instant,
        label: String,
        rawRecordId: RawRecordId,
    ): List<Observation> {
        val at = when (val measurement = reading.measurement) {
            is GattMeasurement.HeartRate -> recordedAt
            is GattMeasurement.Weight -> measurement.at ?: recordedAt
            is GattMeasurement.BodyComposition -> measurement.at ?: recordedAt
            is GattMeasurement.BloodPressure -> measurement.at ?: recordedAt
        }
        val group = "ble:${reading.address}:${at.epochSeconds}"
        fun observation(
            type: ObservationType,
            value: Double,
            unit: UcumUnit,
            method: MeasurementMethod,
            key: String,
        ): Observation = Observation(
            id = ObservationId(ids.newId()),
            type = type,
            value = Measure(value, unit).convertTo(type.canonicalUnit),
            start = at,
            end = null,
            provenance = Provenance(
                sourceId = sourceId,
                time = TimeProvenance.of(at, recordedAt, zone),
                idempotencyKey = "ble:${reading.address}:$key:${at.epochSeconds}",
                device = label,
                appVersion = appVersion,
                method = method,
                rawRecordId = rawRecordId,
            ),
            groupKey = group,
        )
        return when (val measurement = reading.measurement) {
            is GattMeasurement.HeartRate -> listOf(
                observation(
                    ObservationType.HEART_RATE,
                    measurement.bpm.toDouble(),
                    UcumUnit.PER_MINUTE,
                    MeasurementMethod.WATCH_OPTICAL,
                    "hr",
                ),
            )

            is GattMeasurement.Weight -> buildList {
                add(
                    observation(
                        ObservationType.WEIGHT,
                        measurement.kilograms,
                        UcumUnit.KILOGRAM,
                        MeasurementMethod.SCALE,
                        "weight",
                    ),
                )
            }

            is GattMeasurement.BodyComposition -> buildList {
                measurement.kilograms?.let {
                    add(
                        observation(
                            ObservationType.WEIGHT,
                            it,
                            UcumUnit.KILOGRAM,
                            MeasurementMethod.SCALE,
                            "weight",
                        ),
                    )
                }
                measurement.bodyFatPercent?.let {
                    add(
                        observation(
                            ObservationType.BODY_FAT_PERCENT,
                            it,
                            UcumUnit.PERCENT,
                            MeasurementMethod.BIA,
                            "fat",
                        ),
                    )
                }
            }

            is GattMeasurement.BloodPressure -> buildList {
                add(
                    observation(
                        ObservationType.BLOOD_PRESSURE_SYSTOLIC,
                        measurement.systolicMmHg,
                        UcumUnit.MILLIMETRE_MERCURY,
                        MeasurementMethod.CUFF,
                        "systolic",
                    ),
                )
                add(
                    observation(
                        ObservationType.BLOOD_PRESSURE_DIASTOLIC,
                        measurement.diastolicMmHg,
                        UcumUnit.MILLIMETRE_MERCURY,
                        MeasurementMethod.CUFF,
                        "diastolic",
                    ),
                )
                measurement.pulseBpm?.let {
                    add(
                        observation(
                            ObservationType.HEART_RATE,
                            it.toDouble(),
                            UcumUnit.PER_MINUTE,
                            MeasurementMethod.CUFF,
                            "pulse",
                        ),
                    )
                }
            }
        }
    }

    private fun emit(state: DeviceLinkState) {
        _state.value = state
        stateListener?.invoke(state)
    }

    data class FoundDevice(val address: String, val name: String?, val kind: DeviceKind)

    private data class BufferedReading(
        val address: String,
        val kind: DeviceKind,
        val measurement: GattMeasurement,
        val at: Instant,
    )

    private companion object {
        const val RAW_SCHEMA = 1
        const val FLUSH_INTERVAL_MILLIS = 10_000L
        const val RAW_KEY_CHARS = 16

        val CCCD_UUID: java.util.UUID =
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** The transport constant, spelled out so no hidden platform default is assumed. */
        const val BLUETOOTH_DEVICE_TRANSPORT_LE = 2

        fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
    }
}
