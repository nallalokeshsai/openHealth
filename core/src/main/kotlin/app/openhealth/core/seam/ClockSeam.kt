package app.openhealth.core.seam

import kotlin.time.Clock
import kotlin.time.Instant

/** The clock every write reads, so tests and the corpus can control time. */
fun interface ClockSeam {
    fun now(): Instant
}

object SystemClock : ClockSeam {
    override fun now(): Instant = Clock.System.now()
}

/** A clock that only moves when a test moves it. */
class FixedClock(private var instant: Instant) : ClockSeam {
    override fun now(): Instant = instant

    fun set(instant: Instant) {
        this.instant = instant
    }

    fun advance(duration: kotlin.time.Duration) {
        instant += duration
    }
}
