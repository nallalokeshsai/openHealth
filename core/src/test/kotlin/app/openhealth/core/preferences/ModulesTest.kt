package app.openhealth.core.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModulesTest {
    @Test
    fun theThirteenModulesOfTheContractAreAllDefinedOnce() {
        assertEquals(13, HealthModule.entries.size)
        assertEquals(13, HealthModule.entries.map { it.key }.toSet().size)
        assertEquals(HealthModule.TRAINING, HealthModule.fromKey("training"))
        assertNull(HealthModule.fromKey("does-not-exist"))
    }

    @Test
    fun everythingIsTheFullSetAndTheOtherPresetsAreSubsets() {
        assertEquals(HealthModule.entries.toSet(), ModulePreset.EVERYTHING.enabled)
        ModulePreset.entries
            .filterNot { it == ModulePreset.EVERYTHING || it == ModulePreset.CUSTOM }
            .forEach { preset ->
                assertTrue(
                    preset.enabled.all { it in ModulePreset.EVERYTHING.enabled },
                    "${preset.key} contains a module outside the closed set",
                )
                assertTrue(preset.enabled.isNotEmpty())
            }
        assertEquals(emptySet(), ModulePreset.CUSTOM.enabled)
    }

    @Test
    fun justTrainingKeepsTheTrainingDomainAndSleep() {
        val preset = ModulePreset.JUST_TRAINING
        assertTrue(HealthModule.TRAINING in preset.enabled)
        assertTrue(HealthModule.STEPS in preset.enabled)
        assertTrue(HealthModule.BODY in preset.enabled)
        assertTrue(HealthModule.SLEEP in preset.enabled)
        assertTrue(HealthModule.NUTRITION !in preset.enabled)
        assertTrue(HealthModule.CYCLE !in preset.enabled)
    }
}
