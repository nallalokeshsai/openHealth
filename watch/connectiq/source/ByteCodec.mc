module OpenHealth {
    module ByteCodec {
        const MAGIC = 0xA7;
        const VERSION = 1;
        const COMMAND_SIZE = 16;
        const SNAPSHOT_SIZE = 20;

        function encodeCommand(type, id, weightKg, reps, bpm, happenedSeconds, warmup) {
            if (bpm > 255) {
                bpm = 255; // one byte on the wire, never a wrap to 0..4
            }
            var out = new [COMMAND_SIZE] as Lang.ByteArray;
            out[0] = MAGIC;
            out[1] = VERSION;
            out[2] = type;
            out[3] = warmup ? 1 : 0;
            put16(out, 4, (weightKg * 10).toNumber());
            out[6] = reps;
            out[7] = bpm;
            put32(out, 8, id);
            put32(out, 12, happenedSeconds);
            return out;
        }

        function decodeSnapshot(bytes) {
            if (bytes == null || bytes.size() < SNAPSHOT_SIZE) {
                return null;
            }
            if (bytes[0] != MAGIC || bytes[1] != VERSION) {
                return null;
            }
            var flags = bytes[2];
            return {
                :sessionOpen => (flags & 1) != 0,
                :restRunning => (flags & 2) != 0,
                :connected => (flags & 4) != 0,
                :weightUnit => (flags & 8) != 0 ? "lb" : "kg",
                :weightKg => u16(bytes, 3) / 10.0,
                :reps => bytes[5],
                :doneSets => bytes[6],
                :targetSets => bytes[7],
                :restRemaining => u16(bytes, 8),
                :restTotal => u16(bytes, 10),
                :incrementKg => u16(bytes, 12) / 100.0,
                :ackId => u32(bytes, 14)
            };
        }

        function u16(bytes, index) {
            return (bytes[index] << 8) + bytes[index + 1];
        }

        function u32(bytes, index) {
            return (bytes[index] << 24) + (bytes[index + 1] << 16) +
                (bytes[index + 2] << 8) + bytes[index + 3];
        }

        function put16(bytes, index, value) {
            if (value < 0) {
                value = 0;
            }
            if (value > 65535) {
                value = 65535;
            }
            bytes[index] = (value >> 8) & 0xFF;
            bytes[index + 1] = value & 0xFF;
        }

        function put32(bytes, index, value) {
            bytes[index] = (value >> 24) & 0xFF;
            bytes[index + 1] = (value >> 16) & 0xFF;
            bytes[index + 2] = (value >> 8) & 0xFF;
            bytes[index + 3] = value & 0xFF;
        }
    }
}
