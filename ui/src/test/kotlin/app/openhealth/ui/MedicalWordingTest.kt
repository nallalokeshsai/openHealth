package app.openhealth.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The wording test of `BUILD-ROADMAP.md` §B7: a vital is a neutral display of the
 * person's own reading against a cited range, never a verdict. The words "risk",
 * "warning", "diagnose" and "abnormal" are banned from product copy. The two disclaimer
 * strings are allowed to say the app does not diagnose, because that is the sentence
 * that keeps it on the wellness side of `pr.md` §12.1.
 *
 * This is deliberately a text scan, not a per-screen assertion: a new string that calls
 * a reading "abnormal" fails the build even if no test remembered to visit its screen.
 */
class MedicalWordingTest {
    private val banned =
        Regex("\\brisks?\\b|\\bwarnings?\\b|diagnos|abnormal", RegexOption.IGNORE_CASE)

    /** The two sentences whose whole job is to deny that this app diagnoses. */
    private val allowed = setOf("onb_welcome_disclaimer", "settings_about_medical")

    @Test
    fun theWordingRuleFailsOnTheVerdictWords() {
        listOf(
            "This reading carries a risk.",
            "Warning: low oxygen.",
            "The app can diagnose this.",
            "An abnormal glucose value.",
        ).forEach { sample ->
            assertTrue(banned.containsMatchIn(sample), "the rule missed: $sample")
        }
        assertFalse(banned.containsMatchIn("Outside the published range"))
        assertFalse(banned.containsMatchIn("Inside the published range"))
    }

    @Test
    fun noProductStringUsesAVerdict() {
        val file = File("src/main/res/values/strings.xml")
        assertTrue(file.exists(), "expected ${file.absolutePath}")
        val entries = stringsIn(file)
        assertTrue(entries.size > 500, "the resource scan found only ${entries.size} strings")
        assertNoVerdict(entries)
    }

    /**
     * The app module carries product copy of its own (toasts, the lock prompt, the
     * startup error). It is scanned here because the rule is about what a person reads,
     * not about which module a string happens to live in.
     */
    @Test
    fun noAppModuleStringUsesAVerdict() {
        val file = File("../app-android/src/main/res/values/strings.xml")
        assertTrue(file.exists(), "expected ${file.absolutePath}")
        val entries = stringsIn(file)
        assertTrue(entries.size > 20, "the app resource scan found only ${entries.size} strings")
        assertNoVerdict(entries)
    }

    private fun stringsIn(file: File): List<Pair<String, String>> = Regex(
        """<string name="([^"]+)">(.*?)</string>""",
        RegexOption.DOT_MATCHES_ALL,
    )
        .findAll(file.readText())
        .map { it.groupValues[1] to it.groupValues[2] }
        .toList()

    private fun assertNoVerdict(entries: List<Pair<String, String>>) {
        val offenders = entries.filter { (name, text) ->
            name !in allowed && banned.containsMatchIn(text)
        }
        assertTrue(
            offenders.isEmpty(),
            "product copy uses a verdict word: " +
                offenders.joinToString { (name, text) -> "$name=\"$text\"" },
        )
    }
}
