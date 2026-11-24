package app.openhealth.core.body

import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.MuscleRecovery
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.core.formula.RecoveryThresholds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private fun <T> FormulaOutcome<T>.value(): T = (this as FormulaOutcome.Computed).value

class MuscleTaxonomyTest {
    @Test
    fun everyWholeMuscleHasAUniqueKeyAndLabel() {
        assertEquals(19, Muscle.entries.size)
        assertEquals(Muscle.entries.size, Muscle.entries.map { it.key }.toSet().size)
        assertEquals(Muscle.entries.size, Muscle.entries.map { it.labelKey }.toSet().size)
        assertTrue(Muscle.entries.none { it.key in BODY_INERT_PARTS })
    }

    @Test
    fun storedKeysRoundTrip() {
        Muscle.entries.forEach { assertEquals(it, Muscle.fromKey(it.key)) }
        assertNull(Muscle.fromKey("head"))
        assertNull(Muscle.fromKey("lats"))
    }

    @Test
    fun theBodyPartIsTheMajorityOfThePrimaryMuscles() {
        assertEquals(BodyPart.CHEST, BodyPart.of(listOf(Muscle.CHEST)))
        assertEquals(BodyPart.LEGS, BodyPart.of(listOf(Muscle.QUADS, Muscle.HAMSTRINGS)))
        assertEquals(BodyPart.BACK, BodyPart.of(listOf(Muscle.UPPER_BACK, Muscle.BICEPS)))
        assertNull(BodyPart.of(emptyList()))
    }
}

class RecoveryStepsTest {
    /**
     * The exit test of BUILD-ROADMAP.md §B4: the default 24 / 48 / 72 hour thresholds at
     * the hours either side of each boundary.
     */
    @Test
    fun theDefaultThresholdsAtTheDocumentedHours() {
        val cases = listOf(
            0.0 to RecoveryStep.RECOVERING,
            23.0 to RecoveryStep.RECOVERING,
            25.0 to RecoveryStep.EASING,
            47.0 to RecoveryStep.EASING,
            49.0 to RecoveryStep.READY,
            73.0 to RecoveryStep.UNTRAINED,
        )
        cases.forEach { (hours, expected) ->
            assertEquals(
                expected,
                MuscleRecovery.stepAtHours(
                    hours,
                    hardSession = false,
                    RecoveryThresholds(),
                ).value(),
                "at $hours h",
            )
        }
    }

    @Test
    fun theBoundariesThemselves() {
        // pr.md §6.5 reads "under 24 h", "24-48 h", "48-72 h", then base, so a whole
        // boundary hour belongs to the later step and 72 h is still Ready.
        assertEquals(RecoveryStep.RECOVERING, MuscleRecovery.stepAtHours(23.999, false).value())
        assertEquals(RecoveryStep.EASING, MuscleRecovery.stepAtHours(24.0, false).value())
        assertEquals(RecoveryStep.READY, MuscleRecovery.stepAtHours(48.0, false).value())
        assertEquals(RecoveryStep.READY, MuscleRecovery.stepAtHours(72.0, false).value())
        assertEquals(
            RecoveryStep.UNTRAINED,
            MuscleRecovery.stepAtHours(72.001, false).value(),
        )
    }

    @Test
    fun aHardSessionHoldsTheMuscleOneStepLonger() {
        assertEquals(RecoveryStep.RECOVERING, MuscleRecovery.stepAtHours(30.0, true).value())
        assertEquals(RecoveryStep.EASING, MuscleRecovery.stepAtHours(60.0, true).value())
        assertEquals(RecoveryStep.READY, MuscleRecovery.stepAtHours(90.0, true).value())
    }
}

class RecoveryMapTest {
    private val now = Instant.parse("2026-11-24T18:00:00Z")

    private fun set(
        session: String,
        at: Instant,
        primary: List<Muscle> = listOf(Muscle.QUADS),
        secondary: List<Muscle> = emptyList(),
        weight: Double = 100.0,
        reps: Int = 5,
        warmup: Boolean = false,
    ) = LoadedSet(
        sessionId = session,
        exerciseId = "ex-${primary.first().key}",
        primary = primary,
        secondary = secondary,
        completedAt = at,
        weightKg = weight,
        reps = reps,
        isWarmup = warmup,
    )

    @Test
    fun primaryCountsFullAndSecondaryHalf() {
        val states = RecoveryMap.compute(
            now = now,
            sets = listOf(
                set(
                    "s1",
                    now - 2.hours,
                    primary = listOf(Muscle.QUADS),
                    secondary = listOf(Muscle.GLUTES),
                ),
            ),
        )
        assertEquals(1.0, states.getValue(Muscle.QUADS).sets)
        assertEquals(Muscle.SECONDARY_WEIGHT, states.getValue(Muscle.GLUTES).sets)
        assertEquals(1.0, states.getValue(Muscle.QUADS).tonnageKg / 500.0)
        assertEquals(0.5, states.getValue(Muscle.GLUTES).tonnageKg / 500.0)
    }

    @Test
    fun anUntouchedMuscleIsUntrainedAndSaysSo() {
        val states = RecoveryMap.compute(now = now, sets = emptyList())
        assertEquals(RecoveryStep.UNTRAINED, states.getValue(Muscle.CHEST).step)
        assertNull(states.getValue(Muscle.CHEST).lastLoadedAt)
        assertNull(states.getValue(Muscle.CHEST).hoursSinceLoad)
    }

    @Test
    fun hoursComeFromTheMostRecentSetAcrossSessions() {
        val states = RecoveryMap.compute(
            now = now,
            sets = listOf(
                set("s1", now - 30.hours),
                set("s2", now - 3.hours),
            ),
        )
        val quads = states.getValue(Muscle.QUADS)
        assertEquals(RecoveryStep.RECOVERING, quads.step)
        assertEquals(3.0, quads.hoursSinceLoad ?: 0.0, 0.001)
        assertEquals(1L, states.getValue(Muscle.QUADS).sets.toLong())
    }

    @Test
    fun warmUpsAreNotLoad() {
        val states = RecoveryMap.compute(
            now = now,
            sets = listOf(set("s1", now - 3.hours, weight = 30.0, warmup = true)),
        )
        assertEquals(RecoveryStep.UNTRAINED, states.getValue(Muscle.QUADS).step)
    }

    @Test
    fun setsOlderThanTheWindowAreNotLoad() {
        val states = RecoveryMap.compute(
            now = now,
            sets = listOf(set("s1", now - 29.hours * 24)),
        )
        assertEquals(RecoveryStep.UNTRAINED, states.getValue(Muscle.QUADS).step)
    }

    @Test
    fun aSessionAboveTheMusclesOwnMedianHoldsOneStepLonger() {
        val history = (1..4).map { index ->
            set("session-$index", now - (index * 48 + 54).hours, reps = 5)
        }
        val heavy = set("today", now - 30.hours, reps = 5, weight = 150.0)
        val hard = RecoveryMap.compute(now = now, sets = history + heavy)
        assertEquals(RecoveryStep.RECOVERING, hard.getValue(Muscle.QUADS).step)
        assertTrue(hard.getValue(Muscle.QUADS).hardSession)

        val normal = set("today", now - 30.hours, reps = 5, weight = 90.0)
        val easy = RecoveryMap.compute(now = now, sets = history + normal)
        assertEquals(RecoveryStep.EASING, easy.getValue(Muscle.QUADS).step)
    }

    @Test
    fun oneSessionIsNotItsOwnBaseline() {
        val states = RecoveryMap.compute(
            now = now,
            sets = listOf(set("only", now - 3.hours, weight = 300.0)),
        )
        assertEquals(false, states.getValue(Muscle.QUADS).hardSession)
    }

    @Test
    fun theOrderedStatesCoverEveryMuscle() {
        val states = RecoveryMap.compute(now = now, sets = emptyList())
        val ordered = RecoveryMap.orderedStates(states)
        assertEquals(Muscle.entries.size, ordered.size)
        assertEquals(Muscle.CHEST, ordered.first().muscle)
        assertNotNull(ordered.firstOrNull { it.muscle == Muscle.NECK })
    }
}

class AdiposeModelTest {
    @Test
    fun withoutBodyFatAndWithoutTheNavyInputsThereIsNoLayer() {
        assertNull(AdiposeModel.compute(AdiposeInputs(sex = BodySex.MALE)))
    }

    @Test
    fun bodyFatAloneDrawsEveryRegionWithinTheClamps() {
        val layer = AdiposeModel.compute(
            AdiposeInputs(sex = BodySex.MALE, bodyFatPercent = 25.0),
        )
        assertNotNull(layer)
        assertEquals(MuscleRegion.entries.size, layer.thickness.size)
        layer.thickness.values.forEach {
            assertTrue(it in AdiposeModel.MIN_THICKNESS..AdiposeModel.MAX_THICKNESS)
        }
        assertTrue(
            layer.thicknessFor(MuscleRegion.MIDSECTION) > layer.thicknessFor(MuscleRegion.NECK),
        )
        assertTrue(layer.thicknessFor(MuscleRegion.HIPS) > layer.thicknessFor(MuscleRegion.CALVES))
    }

    @Test
    fun aHigherBodyFatDrawsAThickerLayer() {
        val lean = AdiposeModel.compute(AdiposeInputs(BodySex.MALE, bodyFatPercent = 12.0))!!
        val heavier = AdiposeModel.compute(AdiposeInputs(BodySex.MALE, bodyFatPercent = 35.0))!!
        assertTrue(heavier.maxThickness > lean.maxThickness)
    }

    @Test
    fun tapeMeasurementsFallBackToTheCitedNavyEstimate() {
        val layer = AdiposeModel.compute(
            AdiposeInputs(
                sex = BodySex.MALE,
                heightCm = 180.0,
                tapeCm = mapOf("neck" to 38.0, "waist" to 90.0),
            ),
        )
        assertNotNull(layer)
        assertTrue(layer.maxThickness > 0.0)
    }

    @Test
    fun aSingleMeasurementDoesNotScaleTheRegions() {
        val plain = AdiposeModel.compute(AdiposeInputs(BodySex.MALE, bodyFatPercent = 20.0))!!
        val measured = AdiposeModel.compute(
            AdiposeInputs(
                sex = BodySex.MALE,
                bodyFatPercent = 20.0,
                tapeCm = mapOf("waist" to 95.0),
            ),
        )!!
        assertEquals(
            plain.thicknessFor(MuscleRegion.MIDSECTION),
            measured.thicknessFor(MuscleRegion.MIDSECTION),
        )
    }

    @Test
    fun twoMeasurementsScaleAgainstThePersonsOwnMedian() {
        val layer = AdiposeModel.compute(
            AdiposeInputs(
                sex = BodySex.MALE,
                bodyFatPercent = 20.0,
                tapeCm = mapOf("waist" to 110.0, "upper_arm" to 30.0),
            ),
        )!!
        assertTrue(
            layer.thicknessFor(MuscleRegion.MIDSECTION) > layer.thicknessFor(MuscleRegion.ARMS),
        )
    }

    @Test
    fun anImpossibleBodyFatIsRejectedRatherThanDrawn() {
        assertNull(AdiposeModel.compute(AdiposeInputs(BodySex.MALE, bodyFatPercent = 0.0)))
        assertNull(AdiposeModel.compute(AdiposeInputs(BodySex.MALE, bodyFatPercent = 95.0)))
    }
}
