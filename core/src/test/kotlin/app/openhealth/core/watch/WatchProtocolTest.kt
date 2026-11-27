package app.openhealth.core.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchProtocolTest {
    private val start = 1_700_000_000_000L

    private fun command(
        type: WatchCommandType = WatchCommandType.LOG_SET,
        id: String = "c1",
        weight: Double? = 80.0,
        reps: Int? = 5,
    ) = WatchCommand(
        id = id,
        type = type,
        happenedAtMillis = start,
        sessionId = "s1",
        exerciseId = "e1",
        weightKg = weight,
        reps = reps,
    )

    private fun snapshot() = WatchSnapshot(
        generatedAtMillis = start,
        session = WatchSessionSummary(
            id = "s1",
            title = "Push",
            startedAtMillis = start - 60_000,
            exercises = listOf(
                WatchExercise(
                    id = "e1",
                    name = "Bench press",
                    targetSets = 3,
                    targetReps = 8,
                    targetWeightKg = 80.0,
                ),
            ),
            currentExerciseIndex = 0,
            completedSetCount = 0,
        ),
        weightUnit = "kg",
        weightIncrementKg = 2.5,
    )

    @Test
    fun aCommandRoundTripsThroughTheVersionedEnvelope() {
        val encoded = WatchProtocol.encode(WatchProtocol.command(command()))
        val decoded = WatchProtocol.decodeCommand(encoded)
        assertNotNull(decoded)
        assertEquals("c1", decoded.id)
        assertEquals(WatchCommandType.LOG_SET, decoded.type)
        assertEquals(80.0, decoded.weightKg)
        assertEquals(5, decoded.reps)
    }

    @Test
    fun aSnapshotRoundTripsAndKeepsTheUnitAndStep() {
        val encoded = WatchProtocol.encode(WatchProtocol.snapshot(snapshot()))
        val decoded = WatchProtocol.decodeSnapshot(encoded)
        assertNotNull(decoded)
        assertEquals("s1", decoded.session?.id)
        assertEquals("kg", decoded.weightUnit)
        assertEquals(2.5, decoded.weightIncrementKg)
    }

    @Test
    fun aDifferentProtocolVersionIsRefusedRatherThanMisread() {
        val encoded = WatchProtocol.encode(
            WatchProtocol.command(command()).copy(protocol = WatchProtocol.VERSION + 1),
        )
        assertNull(WatchProtocol.decodeCommand(encoded))
    }

    @Test
    fun aMalformedCommandNeverReachesTheDomain() {
        assertNull(WatchProtocol.decodeCommand("not json"))
        assertEquals("command-id", WatchProtocol.validate(command(id = " ")))
        assertEquals("weight", WatchProtocol.validate(command(weight = Double.NaN)))
        assertEquals("weight", WatchProtocol.validate(command(weight = -1.0)))
        assertEquals("reps", WatchProtocol.validate(command(reps = -1)))
        assertEquals("reps", WatchProtocol.validate(command(reps = null)))
        assertEquals("session-id", WatchProtocol.validate(command().copy(sessionId = null)))
        assertEquals("exercise-id", WatchProtocol.validate(command().copy(exerciseId = " ")))
        assertEquals("rir", WatchProtocol.validate(command().copy(rir = 11.0)))
    }

    @Test
    fun aHeartRateOutsideAHumanRangeIsRefused() {
        val low = WatchCommand(
            id = "hr",
            type = WatchCommandType.HEART_RATE,
            happenedAtMillis = start,
            heartRateBpm = 10,
        )
        val high = low.copy(heartRateBpm = 400)
        assertNull(WatchProtocol.validate(low.copy(heartRateBpm = 60)))
        assertEquals("heart-rate", WatchProtocol.validate(low))
        assertEquals("heart-rate", WatchProtocol.validate(high))
    }

    @Test
    fun aSnapshotWithAnImpossibleExerciseIndexIsRefused() {
        val bad = snapshot().copy(
            session = snapshot().session!!.copy(currentExerciseIndex = 3),
        )
        assertEquals("current-exercise", WatchProtocol.validate(bad))
    }

    @Test
    fun aSetCannotCarryANegativeValue() {
        val bad = snapshot().copy(
            session = snapshot().session!!.copy(
                exercises = listOf(
                    snapshot().session!!.exercises.first().copy(
                        sets = listOf(
                            WatchSet(
                                weightKg = 80.0,
                                reps = -2,
                                completedAtMillis = start,
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("set", WatchProtocol.validate(bad))
    }

    @Test
    fun anUnknownFieldFromANewerWatchIsIgnored() {
        val json = WatchProtocol.encode(WatchProtocol.command(command()))
            .dropLast(1) + ",\"future\":true}"
        val decoded = WatchProtocol.decodeCommand(json)
        assertNotNull(decoded)
        assertEquals("c1", decoded.id)
    }

    @Test
    fun aSnapshotRequestCarriesNoCommand() {
        val envelope = WatchProtocol.decode(WatchProtocol.encode(WatchProtocol.request("r1")))
        assertNotNull(envelope)
        assertEquals(WatchMessageType.SNAPSHOT_REQUEST, envelope.type)
        assertEquals("r1", envelope.requestId)
        assertTrue(envelope.command == null)
    }
}
