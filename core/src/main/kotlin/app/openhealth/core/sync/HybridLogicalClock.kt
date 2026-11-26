package app.openhealth.core.sync

/**
 * A hybrid logical clock (HLC), the ordering key every change carries (`Tech-Stack.md`
 * §6). It combines physical time with a logical counter so two edits made in the same
 * millisecond still order, and a device whose wall clock is behind another's cannot
 * invent a newer revision than it has seen.
 *
 * The encoding is fixed width and lexicographically sortable, which is what the server
 * and PostgreSQL sort on. Parse it back with [parse] rather than comparing raw strings
 * across time ranges that could outgrow the width.
 */
data class HybridLogicalClock(val wallMillis: Long, val counter: Long) :
    Comparable<HybridLogicalClock> {
    init {
        require(wallMillis >= 0) { "wallMillis must not be negative" }
        require(counter >= 0) { "counter must not be negative" }
    }

    override fun compareTo(other: HybridLogicalClock): Int =
        compareValuesBy(this, other, { it.wallMillis }, { it.counter })

    fun encode(): String = buildString(ENCODED_LENGTH) {
        append(wallMillis.toString().padStart(WALL_DIGITS, '0'))
        append('-')
        append(counter.toString().padStart(COUNTER_DIGITS, '0'))
    }

    override fun toString(): String = encode()

    companion object {
        private const val WALL_DIGITS = 16
        private const val COUNTER_DIGITS = 8
        private const val ENCODED_LENGTH = WALL_DIGITS + 1 + COUNTER_DIGITS

        val ZERO = HybridLogicalClock(0, 0)

        /** A local event: advance the wall clock even when it moves backwards. */
        fun tick(previous: HybridLogicalClock?, wallMillis: Long): HybridLogicalClock {
            require(wallMillis >= 0) { "wallMillis must not be negative" }
            return if (previous != null && wallMillis <= previous.wallMillis) {
                HybridLogicalClock(previous.wallMillis, previous.counter + 1)
            } else {
                HybridLogicalClock(wallMillis, 0)
            }
        }

        /**
         * Receive a remote event: the clock jumps to the greatest physical time seen
         * (local, remote or now) and the counter breaks the tie, so the next local event
         * sorts after both.
         */
        fun receive(
            local: HybridLogicalClock,
            remote: HybridLogicalClock,
            wallMillis: Long,
        ): HybridLogicalClock {
            val maxWall = maxOf(local.wallMillis, remote.wallMillis, wallMillis)
            val counter = when {
                maxWall == local.wallMillis && maxWall == remote.wallMillis ->
                    maxOf(local.counter, remote.counter) + 1
                maxWall == local.wallMillis -> local.counter + 1
                maxWall == remote.wallMillis -> remote.counter + 1
                else -> 0
            }
            return HybridLogicalClock(maxWall, counter)
        }

        fun parse(encoded: String): HybridLogicalClock {
            require(encoded.length == ENCODED_LENGTH) {
                "hybrid logical clock must be $ENCODED_LENGTH characters: $encoded"
            }
            require(encoded[WALL_DIGITS] == '-') {
                "hybrid logical clock is missing its separator: $encoded"
            }
            val wall = encoded.substring(0, WALL_DIGITS).toLongOrNull()
            val counter = encoded.substring(WALL_DIGITS + 1).toLongOrNull()
            require(wall != null && counter != null) {
                "hybrid logical clock is not numeric: $encoded"
            }
            return HybridLogicalClock(wall, counter)
        }

        fun max(a: HybridLogicalClock, b: HybridLogicalClock): HybridLogicalClock =
            if (a >= b) a else b
    }
}

/** Mutable holder around one device's logical clock; the change log owns one of these. */
class HybridClock(private val wallMillis: () -> Long) {
    @Volatile
    private var current: HybridLogicalClock = HybridLogicalClock.ZERO

    fun current(): HybridLogicalClock = current

    @Synchronized
    fun next(): HybridLogicalClock {
        current = HybridLogicalClock.tick(current, wallMillis())
        return current
    }

    @Synchronized
    fun observe(remote: HybridLogicalClock): HybridLogicalClock {
        current = HybridLogicalClock.receive(current, remote, wallMillis())
        return current
    }
}
