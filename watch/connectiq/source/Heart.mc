using Toybox.Sensor;

module OpenHealth {
    module Heart {
        function start() {
            Sensor.setEnabledSensors([Sensor.SENSOR_HEARTRATE]);
            Sensor.enableSensorEvents(method(:onSensor));
        }

        function onSensor(info) {
            if (info.heartRate != null) {
                Model.noteHeartRate(info.heartRate);
                Toybox.WatchUi.requestUpdate();
            }
        }
    }
}
