using Toybox.Application;
using Toybox.System;
using Toybox.Time;

module OpenHealth {
    module Model {
        var connected = false;
        var sessionOpen = false;
        var weightKg = 0.0;
        var reps = 8;
        var weightUnit = "kg";
        var incrementKg = 2.5;
        var doneSets = 0;
        var targetSets = 0;
        var restEndsAt = 0;
        var restTotal = 0;
        var heartRate = 0;
        var pending = [];
        var lastHeartRateSent = 0;
        var nextCommandId = 1;
        var writing = false;

        function load() {
            nextCommandId = (Time.now().value() % 1000000).toNumber() + 1;
            var last = Application.Storage.getValue("snapshot");
            if (last != null) {
                applyValues(last);
            }
            var queued = Application.Storage.getValue("pending");
            if (queued != null) {
                pending = queued;
            }
        }

        function savePending() {
            Application.Storage.setValue("pending", pending);
        }

        function saveSnapshot() {
            Application.Storage.setValue("snapshot", {
                :sessionOpen => sessionOpen,
                :weightKg => weightKg,
                :reps => reps,
                :weightUnit => weightUnit,
                :incrementKg => incrementKg,
                :doneSets => doneSets,
                :targetSets => targetSets,
                :restRemaining => restSeconds(),
                :restTotal => restTotal
            });
        }

        function applyPacket(packet) {
            if (packet == null) {
                return;
            }
            applyValues(packet);
            saveSnapshot();
            connected = true;
            removeAcknowledged(packet[:ackId]);
        }

        function applyValues(values) {
            sessionOpen = values[:sessionOpen];
            weightKg = values[:weightKg];
            reps = values[:reps];
            weightUnit = values[:weightUnit];
            incrementKg = values[:incrementKg];
            doneSets = values[:doneSets];
            targetSets = values[:targetSets];
            restTotal = values[:restTotal];
            var rest = values[:restRemaining];
            restEndsAt = rest > 0 ? System.getTimer() + rest * 1000 : 0;
        }

        function removeAcknowledged(ackId) {
            var kept = [];
            for (var i = 0; i < pending.size(); i += 1) {
                if (pending[i][:id] > ackId) {
                    kept.add(pending[i]);
                }
            }
            pending = kept;
            savePending();
        }

        function hasSession() {
            return sessionOpen;
        }

        function restSeconds() {
            if (restEndsAt <= 0) {
                return 0;
            }
            var left = restEndsAt - System.getTimer();
            if (left <= 0) {
                restEndsAt = 0;
                return 0;
            }
            return (left / 1000).toNumber();
        }

        function adjustWeight(steps) {
            weightKg = weightKg + steps * incrementKg;
            if (weightKg < 0) {
                weightKg = 0;
            }
            if (weightKg > 500) {
                weightKg = 500;
            }
            saveSnapshot();
        }

        function adjustReps(steps) {
            reps = reps + steps;
            if (reps < 0) {
                reps = 0;
            }
            if (reps > 100) {
                reps = 100;
            }
            saveSnapshot();
        }

        function logSet() {
            if (!hasSession()) {
                return false;
            }
            queue(1, 0, 0);
            restEndsAt = System.getTimer() + 90 * 1000;
            restTotal = 90;
            saveSnapshot();
            return true;
        }

        function finish() {
            if (!hasSession()) {
                return;
            }
            queue(2, 0, 0);
        }

        function noteHeartRate(bpm) {
            heartRate = bpm;
            if (!hasSession()) {
                return;
            }
            var now = System.getTimer();
            if (now - lastHeartRateSent < 60000) {
                return;
            }
            lastHeartRateSent = now;
            queue(3, 0, bpm);
        }

        function queue(type, warmup, bpm) {
            var id = nextCommandId;
            nextCommandId = nextCommandId + 1;
            var packet = ByteCodec.encodeCommand(
                type,
                id,
                weightKg,
                reps,
                bpm,
                Time.now().value(),
                warmup == 1
            );
            pending.add({ :id => id, :packet => packet });
            savePending();
        }

        function nextPacket() {
            if (writing || pending.size() == 0) {
                return null;
            }
            return pending[0][:packet];
        }

        function wrote() {
            writing = false;
        }
    }
}
