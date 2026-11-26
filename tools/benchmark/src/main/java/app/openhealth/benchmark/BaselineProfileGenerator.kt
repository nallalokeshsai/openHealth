package app.openhealth.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile that ships in `app-android/src/main/baseline-prof.txt`
 * (`Tech-Stack.md` §11–12; `BUILD-ROADMAP.md` §B10). It walks the first screen a person
 * sees, Today, and the two tabs it links to, then leaves; the profile covers class
 * loading and the first-frame work those five seconds actually do.
 *
 * The output lands in this module's `build/outputs/.../baseline-prof.txt`; the file is
 * copied into the app by hand and committed, because a profile is a reviewed artefact
 * and not something a build should rewrite silently.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule: BaselineProfileRule = BaselineProfileRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        profileBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("Due")), CONTENT_TIMEOUT_MILLIS)
            // A tab switch and back: the five roots share the theme, the top bar and the
            // tab bar, so this warms the code every screen starts with.
            device.findObject(By.text("Vitals"))?.click()
            device.waitForIdle()
            device.findObject(By.text("Today"))?.click()
            device.waitForIdle()
        },
    )

    private companion object {
        const val PACKAGE = "app.openhealth.bench"
        const val CONTENT_TIMEOUT_MILLIS = 5_000L
    }
}
