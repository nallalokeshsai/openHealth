package app.openhealth.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `Tech-Stack.md` §9: every answer is parsed into domain objects and validated. */
class CoachParserTest {
    private val plan = CoachPlan(
        listOf(
            CoachPlanRoutine(
                id = "push",
                name = "Push",
                weekday = 1,
                entries = listOf(
                    CoachPlanEntry(
                        id = "entry-bench",
                        exerciseId = "bench",
                        exerciseName = "Bench press",
                        sets = 3,
                        reps = 8,
                        repsHigh = null,
                        weightKg = 80.0,
                        rir = 2.0,
                        position = 0,
                    ),
                ),
            ),
            CoachPlanRoutine(id = "pull", name = "Pull", weekday = 3, entries = emptyList()),
        ),
    )

    private fun validAnswer() = """
        {
          "note": "One change this week.",
          "proposals": [
            {
              "summary": "Add a fourth bench set",
              "rationale": "You completed all sets last week.",
              "actions": [
                {"kind": "update_target", "routine_id": "push",
                 "entry_id": "entry-bench", "sets": 4}
              ]
            }
          ]
        }
    """.trimIndent()

    private fun codes(text: String): List<CoachFindingCode> {
        val result = CoachParser.parse(text, plan)
        assertTrue(result is CoachOutputResult.Invalid, "expected invalid, was $result")
        return (result as CoachOutputResult.Invalid).findings.map { it.code }
    }

    @Test
    fun aValidAnswerBecomesTypedActions() {
        val result = CoachParser.parse(validAnswer(), plan)
        assertTrue(result is CoachOutputResult.Valid)
        val answer = (result as CoachOutputResult.Valid).answer
        assertEquals("One change this week.", answer.note)
        val action = answer.proposals.single().actions.single() as CoachAction.UpdateTarget
        assertEquals("entry-bench", action.entryId)
        assertEquals(4, action.sets)
    }

    @Test
    fun aFencedAnswerIsReadButAProsePrefixIsNot() {
        val fenced = "```json\n" + validAnswer() + "\n```"
        assertTrue(CoachParser.parse(fenced, plan) is CoachOutputResult.Valid)
        assertTrue(CoachParser.parse("Here is the plan: ${validAnswer()}", plan)
            is CoachOutputResult.Invalid)
    }

    @Test
    fun anUnknownRootFieldIsRejected() {
        val text = validAnswer().replaceFirst("\"note\"", "\"extra\": 1, \"note\"")
        assertTrue(CoachFindingCode.UNKNOWN_FIELD in codes(text))
    }

    @Test
    fun anUnknownExerciseIdIsRejected() {
        val text = validAnswer().replace("update_target", "add_exercise")
            .replace("\"sets\": 4", "\"sets\": 3, \"reps\": 8, \"weight_kg\": 80")
            .replace("\"entry_id\": \"entry-bench\",", "\"exercise_id\": \"ghost\",")
        assertTrue(CoachFindingCode.UNKNOWN_EXERCISE in codes(text))
    }

    @Test
    fun aLoadOutsideThePlausibleScreenIsRejected() {
        val text = validAnswer()
            .replace("update_target", "add_exercise")
            .replace("\"sets\": 4", "\"sets\": 3, \"reps\": 8, \"weight_kg\": 900")
            .replace("\"entry_id\": \"entry-bench\",", "\"exercise_id\": \"bench\",")
        assertTrue(CoachFindingCode.BAD_WEIGHT in codes(text))
    }

    @Test
    fun aProposalThatChangesNothingIsRejected() {
        val text = validAnswer().replace("\"sets\": 4", "\"sets\": 3")
        assertTrue(CoachFindingCode.NO_CHANGE in codes(text))
    }

    @Test
    fun anEntryFromAnotherRoutineIsRejected() {
        val text = validAnswer().replace("\"routine_id\": \"push\"", "\"routine_id\": \"pull\"")
        assertTrue(CoachFindingCode.UNKNOWN_ENTRY in codes(text))
    }

    @Test
    fun aDiagnosisOrDoseIsRejected() {
        val text = validAnswer().replace(
            "You completed all sets last week.",
            "You should take 200 mg of caffeine and you likely have overtraining.",
        )
        assertTrue(CoachFindingCode.UNSAFE_CLAIM in codes(text))
    }

    @Test
    fun moreThanTenActionsIsRejected() {
        val actions = (1..11).joinToString(",") {
            """{"kind":"move_day","routine_id":"pull","weekday":${it % 7 + 1}}"""
        }
        val text = """
            {"note":"Many changes.","proposals":[{"summary":"Move","rationale":"Because.",
            "actions":[$actions]}]}
        """.trimIndent()
        assertTrue(CoachFindingCode.TOO_MANY_ACTIONS in codes(text))
    }

    @Test
    fun aPlainNoteWithNoProposalIsValid() {
        val result = CoachParser.parse(
            """{"note":"Nothing to change this week.","proposals":[]}""",
            plan,
        )
        assertTrue(result is CoachOutputResult.Valid)
        assertTrue((result as CoachOutputResult.Valid).answer.proposals.isEmpty())
    }

    @Test
    fun anEmptyNoteIsRejected() {
        val text = """{"note":" ","proposals":[]}"""
        assertTrue(CoachFindingCode.EMPTY_TEXT in codes(text))
    }

    @Test
    fun aWeekdayOutsideTheIsoRangeIsRejected() {
        val text = validAnswer()
            .replace("update_target", "move_day")
            .replace("\"entry_id\": \"entry-bench\",", "")
            .replace("\"sets\": 4", "\"weekday\": 9")
        assertTrue(CoachFindingCode.BAD_WEEKDAY in codes(text))
    }

    @Test
    fun movingARoutineToTheDayItAlreadyHasIsNoChange() {
        val text = validAnswer()
            .replace("update_target", "move_day")
            .replace("\"entry_id\": \"entry-bench\",", "")
            .replace("\"sets\": 4", "\"weekday\": 1")
        assertTrue(CoachFindingCode.NO_CHANGE in codes(text))
    }

    @Test
    fun theDiffShowsTheBeforeAndAfterWithoutApplying() {
        val answer = (CoachParser.parse(validAnswer(), plan) as CoachOutputResult.Valid).answer
        val rows = CoachDiff.rows(answer.proposals.single().actions, plan)
        val row = rows.single() as CoachDiffRow.Target
        assertEquals(3, row.before.sets)
        assertEquals(4, row.after.sets)
        assertEquals("Bench press", row.exerciseName)
    }
}
