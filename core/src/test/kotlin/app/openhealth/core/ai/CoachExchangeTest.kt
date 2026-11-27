package app.openhealth.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** `Tech-Stack.md` §9: one repair round, then fail visibly. */
class CoachExchangeTest {
    private val plan = CoachPlan(
        listOf(
            CoachPlanRoutine(
                id = "push",
                name = "Push",
                weekday = 1,
                entries = listOf(
                    CoachPlanEntry("entry", "bench", "Bench press", 3, 8, null, 80.0, 2.0, 0),
                ),
            ),
        ),
    )

    private val good = """
        {"note":"A change.","proposals":[{"summary":"Add a set","rationale":"Ready.",
        "actions":[{"kind":"update_target","routine_id":"push","entry_id":"entry","sets":4}]}]}
    """.trimIndent()

    private val bad = """{"note":"A change.","proposals":[{"summary":"Add a set",
        "rationale":"Ready.","actions":[{"kind":"update_target","routine_id":"push",
        "entry_id":"entry","sets":99}]}]}"""

    private class FakeProvider(private val answers: List<CoachProviderResult>) : CoachProvider {
        val requests = mutableListOf<CoachProviderRequest>()

        override suspend fun complete(request: CoachProviderRequest): CoachProviderResult {
            requests += request
            return answers[requests.size - 1]
        }
    }

    private val request = CoachProviderRequest("system", "user")

    @Test
    fun aValidFirstAnswerNeedsNoRepair() = runTest {
        val provider = FakeProvider(listOf(CoachProviderResult.Text(good)))
        val result = CoachExchange(provider).run(request, plan)
        assertTrue(result is CoachExchangeResult.Answer)
        assertEquals(false, (result as CoachExchangeResult.Answer).repaired)
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun anInvalidAnswerGetsExactlyOneRepairRound() = runTest {
        val provider = FakeProvider(
            listOf(CoachProviderResult.Text(bad), CoachProviderResult.Text(good)),
        )
        val result = CoachExchange(provider).run(request, plan)
        assertTrue(result is CoachExchangeResult.Answer)
        assertTrue((result as CoachExchangeResult.Answer).repaired)
        assertEquals(2, provider.requests.size)
        assertTrue(provider.requests[1].user.contains("rejected"))
        assertTrue(provider.requests[1].user.contains("bad sets"))
        assertEquals(provider.requests[0].system, provider.requests[1].system)
    }

    @Test
    fun twoInvalidAnswersFailVisiblyWithTheFindings() = runTest {
        val provider = FakeProvider(
            listOf(CoachProviderResult.Text(bad), CoachProviderResult.Text(bad)),
        )
        val result = CoachExchange(provider).run(request, plan)
        assertTrue(result is CoachExchangeResult.Failed)
        val failure = (result as CoachExchangeResult.Failed).failure
        assertEquals(CoachFailureKind.INVALID_OUTPUT, failure.kind)
        assertTrue(CoachFindingCode.BAD_SETS in failure.findings.map { it.code })
        assertEquals(2, provider.requests.size)
    }

    @Test
    fun aRepairThatIsStillUnsafeFailsAsSafetyNotAsFormat() = runTest {
        val unsafe = good.replace("Ready.", "You likely have a strain; take 200 mg.")
        val provider = FakeProvider(
            listOf(CoachProviderResult.Text(bad), CoachProviderResult.Text(unsafe)),
        )
        val result = CoachExchange(provider).run(request, plan)
        assertTrue(result is CoachExchangeResult.Failed)
        assertEquals(
            CoachFailureKind.SAFETY,
            (result as CoachExchangeResult.Failed).failure.kind,
        )
    }

    @Test
    fun aProviderFailureIsNotRepaired() = runTest {
        val provider = FakeProvider(
            listOf(CoachProviderResult.Failed(CoachFailure(CoachFailureKind.OFFLINE))),
        )
        val result = CoachExchange(provider).run(request, plan)
        assertTrue(result is CoachExchangeResult.Failed)
        assertEquals(CoachFailureKind.OFFLINE, (result as CoachExchangeResult.Failed).failure.kind)
        assertEquals(1, provider.requests.size)
    }
}
