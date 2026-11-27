using Toybox.BluetoothLowEnergy as BLE;
using Toybox.WatchUi;

/**
 * The direct BLE central. The phone advertises the service in
 * AndroidWatchGattServer; the watch writes a 16-byte command and reads a 20-byte
 * snapshot notification. No Garmin Connect Mobile, account or SDK is involved.
 */
class BleLink extends BLE.BleDelegate {
    const SERVICE_UUID = "6f1e2a10-4b7a-4f1e-9d2c-6f0e2b7a1000";
    const COMMAND_UUID = "6f1e2a10-4b7a-4f1e-9d2c-6f0e2b7a1001";
    const SNAPSHOT_UUID = "6f1e2a10-4b7a-4f1e-9d2c-6f0e2b7a1002";

    static var instance = null;

    var command = null;
    var snapshot = null;

    function initialize() {
        BleDelegate.initialize();
        instance = self;
    }

    function start() {
        var profile = {
            :uuid => BLE.stringToUuid(SERVICE_UUID),
            :characteristics => [
                { :uuid => BLE.stringToUuid(COMMAND_UUID), :descriptors => [] },
                { :uuid => BLE.stringToUuid(SNAPSHOT_UUID), :descriptors => [] }
            ]
        };
        BLE.registerProfile(profile);
        BLE.setDelegate(self);
        BLE.setScanState(BLE.SCAN_STATE_SCANNING);
    }

    function stop() {
        BLE.setScanState(BLE.SCAN_STATE_OFF);
        BLE.setDelegate(null);
    }

    /**
     * The phone is matched by its service UUID, never by its Bluetooth name: the name
     * belongs to the person and AndroidWatchGattServer deliberately keeps it off the air.
     */
    function onScanResults(results) {
        var result = results.next();
        while (result != null) {
            if (advertisesOurService(result)) {
                BLE.setScanState(BLE.SCAN_STATE_OFF);
                BLE.pairDevice(result);
                return;
            }
            result = results.next();
        }
    }

    function advertisesOurService(result) {
        var wanted = BLE.stringToUuid(SERVICE_UUID);
        var uuids = result.getServiceUuids();
        if (uuids == null) {
            return false;
        }
        var uuid = uuids.next();
        while (uuid != null) {
            if (uuid.equals(wanted)) {
                return true;
            }
            uuid = uuids.next();
        }
        return false;
    }

    function onConnectedStateChanged(device, state) {
        if (state == BLE.CONNECTION_STATE_CONNECTED) {
            var service = device.getService(BLE.stringToUuid(SERVICE_UUID));
            if (service == null) {
                return;
            }
            command = service.getCharacteristic(BLE.stringToUuid(COMMAND_UUID));
            snapshot = service.getCharacteristic(BLE.stringToUuid(SNAPSHOT_UUID));
            if (snapshot != null) {
                var cccd = snapshot.getDescriptor(BLE.cccdUuid());
                if (cccd != null) {
                    cccd.requestWrite(
                        [0x01, 0x00],
                        { :writeType => BLE.WRITE_TYPE_WITH_RESPONSE }
                    );
                }
            }
            OpenHealth.Model.connected = true;
            WatchUi.requestUpdate();
        } else {
            command = null;
            snapshot = null;
            OpenHealth.Model.connected = false;
            // A write that was in flight when the link dropped never gets its
            // onCharacteristicWrite, and nextPacket() returns null while the flag is
            // set, so the queue would stay wedged until the app was relaunched.
            OpenHealth.Model.wrote();
            // A drop is the case the queue exists for: go back to scanning.
            BLE.setScanState(BLE.SCAN_STATE_SCANNING);
            WatchUi.requestUpdate();
        }
    }

    function onCharacteristicChanged(characteristic, value) {
        if (characteristic.getUuid().equals(BLE.stringToUuid(SNAPSHOT_UUID))) {
            OpenHealth.Model.applyPacket(OpenHealth.ByteCodec.decodeSnapshot(value));
            sendPending();
            WatchUi.requestUpdate();
        }
    }

    function onCharacteristicWrite(characteristic, status) {
        OpenHealth.Model.wrote();
        sendPending();
    }

    function sendPending() {
        if (command == null) {
            return;
        }
        var packet = OpenHealth.Model.nextPacket();
        if (packet == null) {
            return;
        }
        OpenHealth.Model.writing = true;
        try {
            command.requestWrite(packet, { :writeType => BLE.WRITE_TYPE_WITH_RESPONSE });
        } catch (e) {
            // Never wedge the queue on a rejected write.
            OpenHealth.Model.wrote();
        }
    }
}
