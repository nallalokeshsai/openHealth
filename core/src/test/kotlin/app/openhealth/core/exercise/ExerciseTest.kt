package app.openhealth.core.exercise

import app.openhealth.core.body.BodyPart
import app.openhealth.core.body.Muscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseCatalogueTest {
    private val catalogue = ExerciseCatalogue.bundled

    @Test
    fun theLibraryIsBigEnoughToPlanWith() {
        assertTrue(catalogue.size >= 150, "library has ${catalogue.size} exercises")
    }

    @Test
    fun everyExerciseHasANameMuscleAndInstructions() {
        catalogue.forEach { exercise ->
            assertTrue(exercise.name.isNotBlank(), exercise.id)
            assertTrue(exercise.primary.isNotEmpty(), exercise.id)
            assertTrue(exercise.instructions.isNotEmpty(), exercise.id)
            assertTrue(exercise.instructions.none { it.isBlank() }, exercise.id)
            assertTrue(exercise.primary.none { it in exercise.secondary }, exercise.id)
        }
    }

    @Test
    fun idsAndNamesAreUnique() {
        assertEquals(catalogue.size, catalogue.map { it.id }.toSet().size)
        assertEquals(catalogue.size, catalogue.map { it.name.lowercase() }.toSet().size)
    }

    @Test
    fun everyStoredMuscleKeyIsAWholeMuscle() {
        catalogue.forEach { exercise ->
            (exercise.primary + exercise.secondary).forEach { muscle ->
                assertTrue(muscle in Muscle.entries, exercise.id)
            }
            assertTrue(exercise.bodyPart != null, exercise.id)
        }
    }

    @Test
    fun theBodyPartMatchesThePrimaryMuscles() {
        catalogue.forEach { exercise ->
            assertEquals(BodyPart.of(exercise.primary), exercise.bodyPart, exercise.id)
        }
    }

    @Test
    fun theLibraryCoversEveryBodyPart() {
        val parts = catalogue.mapNotNull { it.bodyPart }.toSet()
        assertEquals(BodyPart.entries.toSet(), parts)
    }

    @Test
    fun instructionsAreRedistributableTextNotMarkup() {
        catalogue.forEach { exercise ->
            exercise.instructions.forEach { step ->
                assertFalse(step.contains("<"), exercise.id)
                assertFalse(step.contains("\t"), exercise.id)
            }
        }
    }

    @Test
    fun alternativesShareAPrimaryMuscleAndNeverRepeatTheExercise() {
        val bench = catalogue.first { it.name.startsWith("Barbell Bench Press") }
        val alternatives = ExerciseAlternatives.forExercise(bench, catalogue)
        assertTrue(alternatives.isNotEmpty())
        assertTrue(alternatives.none { it.id == bench.id })
        assertEquals(3, alternatives.size)
        alternatives.forEach { candidate ->
            assertTrue(candidate.primary.any { it in bench.primary }, candidate.name)
        }
    }

    @Test
    fun alternativesOfACustomExerciseUseOnlyWhatItStates() {
        val custom = Exercise(
            id = "custom-1",
            name = "Garage press",
            primary = listOf(Muscle.CHEST),
            secondary = emptyList(),
            equipment = Equipment.BARBELL,
            level = ExerciseLevel.INTERMEDIATE,
            mechanic = ExerciseMechanic.COMPOUND,
            force = ExerciseForce.PUSH,
            bodyPart = BodyPart.CHEST,
            instructions = listOf("Press the bar."),
            isCustom = true,
        )
        val alternatives = ExerciseAlternatives.forExercise(custom, catalogue)
        assertTrue(alternatives.isNotEmpty())
        assertTrue(alternatives.all { Muscle.CHEST in it.primary })
    }
}

class PlanCoverageTest {
    private fun exercise(vararg muscles: Muscle, id: String = muscles.first().key) = Exercise(
        id = id,
        name = id,
        primary = muscles.toList(),
        secondary = emptyList(),
        equipment = Equipment.BARBELL,
        level = ExerciseLevel.INTERMEDIATE,
        mechanic = ExerciseMechanic.COMPOUND,
        force = null,
        bodyPart = BodyPart.of(muscles.toList()),
        instructions = listOf("Do it."),
    )

    @Test
    fun primaryMusclesEarnTheWholeSetAndSecondaryHalf() {
        val primary = PlannedExercise(exercise(Muscle.CHEST), targetSets = 4)
        val withSecondary = Exercise(
            id = "row",
            name = "Row",
            primary = listOf(Muscle.UPPER_BACK),
            secondary = listOf(Muscle.BICEPS),
            equipment = Equipment.BARBELL,
            level = ExerciseLevel.INTERMEDIATE,
            mechanic = ExerciseMechanic.COMPOUND,
            force = null,
            bodyPart = BodyPart.BACK,
            instructions = listOf("Pull."),
        )
        val coverage = PlanCoverage.compute(
            listOf(primary, PlannedExercise(withSecondary, targetSets = 4)),
        )
        assertEquals(4.0, coverage.getValue(Muscle.CHEST).weeklySets)
        assertEquals(4.0, coverage.getValue(Muscle.UPPER_BACK).weeklySets)
        assertEquals(2.0, coverage.getValue(Muscle.BICEPS).weeklySets)
        assertEquals(1.0, coverage.getValue(Muscle.CHEST).share)
        assertEquals(0.5, coverage.getValue(Muscle.BICEPS).share)
    }

    @Test
    fun thePlanRepeatsWithTheTrainingFrequency() {
        val coverage = PlanCoverage.compute(
            listOf(PlannedExercise(exercise(Muscle.QUADS), targetSets = 3)),
            timesPerWeek = 2,
        )
        assertEquals(6.0, coverage.getValue(Muscle.QUADS).weeklySets)
    }

    @Test
    fun anEmptyPlanHasNoCoverage() {
        assertTrue(PlanCoverage.compute(emptyList()).isEmpty())
    }
}
