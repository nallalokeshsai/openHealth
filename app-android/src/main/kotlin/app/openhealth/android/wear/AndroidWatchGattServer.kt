package app.openhealth.android.wear

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import app.openhealth.core.watch.WatchBleCommand
import app.openhealth.core.watch.WatchBlePacket
import java.util.UUID

/**
 * The phone end of the Connect IQ direct link (`docs/adr/0019`).
 *
 * It is a standard Android GATT server: the watch scans for [SERVICE_UUID], writes a
 * command to [COMMAND_UUID] and subscribes to [SNAPSHOT_UUID]. No Garmin SDK, account or
 * cloud is involved; the advertisement is local and stops when the workout ends. The
 * packet is [app.openhealth.core.watch.WatchBlePacket]: a Connect IQ write is limited to
 * 20 bytes, so JSON cannot cross this link.
 */
@SuppressLint("MissingPermission")
class AndroidWatchGattServer(
    private val context: Context,
    private val onCommand: (app.openhealth.core.watch.WatchBleCommand) -> Unit,
    private val snapshot: () -> ByteArray,
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private var server: BluetoothGattServer? = null
    private var advertiser = manager?.adapter?.bluetoothLeAdvertiser
    private var snapshotCharacteristic: BluetoothGattCharacteristic? = null

    // Mutated on the GATT binder thread and read from whichever thread asks for a
    // publish, so it is synchronised and iterated over a copy; a plain LinkedHashSet
    // threw ConcurrentModificationException when a watch connected mid-publish.
    private val connected: MutableSet<BluetoothDevice> =
        java.util.Collections.synchronizedSet(linkedSetOf())

    @Suppress("ReturnCount")
    fun start(): Boolean {
        if (!hasPermissions()) return false
        if (server != null) return true
        val gatt = manager?.openGattServer(context, callback) ?: return false
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val command = BluetoothGattCharacteristic(
            COMMAND_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val state = BluetoothGattCharacteristic(
            SNAPSHOT_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        state.addDescriptor(
            BluetoothGattDescriptor(
                CLIENT_CONFIG_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or
                    BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(command)
        service.addCharacteristic(state)
        if (!gatt.addService(service)) {
            gatt.close()
            return false
        }
        server = gatt
        snapshotCharacteristic = state
        advertise()
        return true
    }

    fun publish() {
        val gatt = server
        val characteristic = snapshotCharacteristic
        if (gatt == null || characteristic == null || !hasConnectPermission()) return
        characteristic.value = snapshot()
        synchronized(connected) { connected.toList() }.forEach { device ->
            runCatching { gatt.notifyCharacteristicChanged(device, characteristic, false) }
        }
    }

    fun stop() {
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        runCatching { server?.close() }
        server = null
        snapshotCharacteristic = null
        connected.clear()
    }

    private fun advertise() {
        if (!hasPermissions()) return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        // The phone's Bluetooth name is the person's ("Sam's Pixel"), so it never goes on
        // the air: the watch matches the 128-bit service UUID instead (`BleLink.mc`). It
        // also would not fit — a 128-bit UUID plus the flags leaves 10 bytes of the 31-byte
        // advertisement, and a longer name fails the whole advertisement silently.
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        runCatching { advertiser?.startAdvertising(settings, data, advertiseCallback) }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) = Unit
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connected += device
                // One watch at a time; stop advertising so a nearby central cannot
                // discover the session while it is live.
                runCatching { advertiser?.stopAdvertising(advertiseCallback) }
                publish()
            } else {
                connected -= device
                if (connected.isEmpty()) advertise()
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid != SNAPSHOT_UUID) {
                respond(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                return
            }
            val bytes = snapshot()
            respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic.uuid == COMMAND_UUID && !preparedWrite && value != null) {
                val command = app.openhealth.core.watch.WatchBlePacket.decodeCommand(value)
                if (command != null) {
                    onCommand(command)
                    respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                    return
                }
            }
            respond(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            if (descriptor.uuid == CLIENT_CONFIG_UUID) publish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun respond(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        runCatching { server?.sendResponse(device, requestId, status, offset, value) }
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val connect = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        val advertise = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
        ) == PackageManager.PERMISSION_GRANTED
        return connect && advertise
    }

    companion object {
        // One source for the three UUIDs, shared with the Connect IQ app (`WatchBlePacket`).
        val SERVICE_UUID: UUID = UUID.fromString(WatchBlePacket.SERVICE_UUID)
        val COMMAND_UUID: UUID = UUID.fromString(WatchBlePacket.COMMAND_UUID)
        val SNAPSHOT_UUID: UUID = UUID.fromString(WatchBlePacket.SNAPSHOT_UUID)
        val CLIENT_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
