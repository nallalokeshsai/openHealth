package app.openhealth.ui.body

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.core.body.AdiposeInputs
import app.openhealth.core.body.AdiposeModel
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.BodySide
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.MuscleRecoveryState
import app.openhealth.core.exercise.Equipment
import app.openhealth.core.exercise.Exercise
import app.openhealth.core.exercise.ExerciseLevel
import app.openhealth.core.exercise.PlanCoverage
import app.openhealth.core.exercise.PlannedExercise
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.ui.R
import app.openhealth.ui.theme.OhAccent
import app.openhealth.ui.theme.OhTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The four views × three map types × two themes of `BUILD-ROADMAP.md` §B4's exit. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class BodyMapScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val geometry: BodyGeometry by lazy {
        BodyGeometry.parse(
            context.resources.openRawResource(R.raw.body_paths)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() },
        )
    }

    private data class Shot(
        val name: String,
        val dark: Boolean,
        val model: BodyMapModel,
        val sides: Set<BodySide>,
        val variant: BodyMapVariant = BodyMapVariant.FULL,
        val scale: Float = 1f,
        val rtl: Boolean = false,
    )

    /** One setContent per test: switching the whole shot through state is allowed. */
    private fun shots(shots: List<Shot>) {
        val state = mutableStateOf(shots.first())
        compose.setContent {
            val shot = state.value
            OhTheme(dark = shot.dark, accent = OhAccent.Brand) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalDensity provides Density(
                        LocalDensity.current.density,
                        fontScale = shot.scale,
                    ),
                    LocalLayoutDirection provides if (shot.rtl) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .testTag(SHOT)
                            .fillMaxSize()
                            .background(OhTheme.colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        OhBodyMap(
                            model = shot.model,
                            geometry = geometry,
                            sides = shot.sides,
                            variant = shot.variant,
                            synchronousLayers = true,
                        )
                    }
                }
            }
        }
        shots.forEach { shot ->
            compose.runOnIdle { state.value = shot }
            compose.waitForIdle()
            val directory = when {
                shot.rtl -> "rtl"
                shot.scale > 1f -> "text200"
                shot.dark -> "dark"
                else -> "light"
            }
            compose.onNodeWithTag(SHOT).captureRoboImage(
                "src/test/screenshots/$directory/${shot.name}.png",
            )
        }
    }

    @Test
    fun fourViewsTimesThreeMapTypesTimesTwoThemes() {
        val shots = buildList {
            BodySex.entries.forEach { sex ->
                BodySide.entries.forEach { side ->
                    listOf(
                        "recovery" to Models.recovery(sex),
                        "plan" to Models.plan(sex),
                        "untrained" to Models.untrained(sex),
                    ).forEach { (type, model) ->
                        val name = "body-${sex.name.lowercase()}-${side.name.lowercase()}-$type"
                        add(Shot(name, true, model, setOf(side)))
                        add(Shot(name, false, model, setOf(side)))
                        // The map is text-scaled and never mirrored, so both extra
                        // variants exist to prove that (`DESIGN.md` §11.1, §11.5).
                        add(Shot(name, true, model, setOf(side), scale = 2f))
                        add(Shot(name, true, model, setOf(side), rtl = true))
                    }
                }
            }
        }
        shots(shots)
    }

    @Test
    fun thePairAndTheSelection() {
        val pair = setOf(BodySide.FRONT, BodySide.BACK)
        val selected = Models.recovery(BodySex.MALE).copy(selected = Muscle.FOREARMS)
        shots(
            listOf(
                Shot("body-pair-recovery", true, Models.recovery(BodySex.MALE), pair),
                Shot("body-pair-recovery", false, Models.recovery(BodySex.MALE), pair),
                Shot("body-selected-forearms", true, selected, pair),
                Shot("body-selected-forearms", false, selected, pair),
                Shot("body-pair-recovery", true, Models.recovery(BodySex.MALE), pair, scale = 2f),
                Shot("body-pair-recovery", true, Models.recovery(BodySex.MALE), pair, rtl = true),
            ),
        )
    }

    @Test
    fun theMiniMap() {
        shots(
            listOf(
                Shot(
                    name = "body-mini",
                    dark = true,
                    model = Models.recovery(BodySex.MALE),
                    sides = setOf(BodySide.FRONT, BodySide.BACK),
                    variant = BodyMapVariant.MINI,
                ),
                Shot(
                    name = "body-mini",
                    dark = false,
                    model = Models.recovery(BodySex.MALE),
                    sides = setOf(BodySide.FRONT, BodySide.BACK),
                    variant = BodyMapVariant.MINI,
                ),
            ),
        )
    }

    private companion object {
        const val SHOT = "body-map"
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class BodyMapSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val geometry: BodyGeometry by lazy {
        BodyGeometry.parse(
            RuntimeEnvironment.getApplication().resources.openRawResource(R.raw.body_paths)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() },
        )
    }

    @Test
    fun talkBackAnnouncesEachMuscleOnce() {
        compose.setContent {
            OhTheme(dark = true) {
                OhBodyMap(model = Models.recovery(BodySex.MALE), geometry = geometry)
            }
        }
        compose.onAllNodesWithContentDescription("Forearms").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Quadriceps").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Glutes").assertCountEquals(1)
    }

    @Test
    fun everyMuscleIsOneAccessibilityNode() {
        compose.setContent {
            OhTheme(dark = true) {
                OhBodyMap(model = Models.recovery(BodySex.FEMALE), geometry = geometry)
            }
        }
        val labels = listOf(
            "Chest", "Upper back", "Lower back", "Trapezius", "Shoulders", "Biceps", "Triceps",
            "Forearms", "Abdominals", "Obliques", "Serratus", "Hip flexors", "Glutes",
            "Adductors", "Quadriceps", "Hamstrings", "Calves", "Tibialis", "Neck",
        )
        labels.forEach { label ->
            compose.onAllNodesWithContentDescription(label).assertCountEquals(1)
        }
    }

    @Test
    fun aTouchOnTheDrawnChestSelectsIt() {
        var opened: Muscle? = null
        compose.setContent {
            OhTheme(dark = true) {
                OhBodyMap(
                    model = Models.recovery(BodySex.MALE),
                    geometry = geometry,
                    sides = setOf(BodySide.FRONT),
                    onMuscle = { opened = it },
                    modifier = Modifier.testTag(MAP),
                )
            }
        }
        val chest = compose.onNodeWithContentDescription("Chest").fetchSemanticsNode()
        val centre = chest.boundsInRoot.center
        compose.onNodeWithTag(MAP).performTouchInput { click(centre) }
        compose.runOnIdle { assertEquals(Muscle.CHEST, opened) }
    }

    @Test
    fun aTouchOnTheBackViewUsesItsOwnViewBox() {
        var opened: Muscle? = null
        compose.setContent {
            OhTheme(dark = true) {
                OhBodyMap(
                    model = Models.recovery(BodySex.MALE),
                    geometry = geometry,
                    sides = setOf(BodySide.BACK),
                    onMuscle = { opened = it },
                    modifier = Modifier.testTag(MAP),
                )
            }
        }
        val glutes = compose.onNodeWithContentDescription("Glutes").fetchSemanticsNode()
        val centre = glutes.boundsInRoot.center
        compose.onNodeWithTag(MAP).performTouchInput { click(centre) }
        compose.runOnIdle { assertEquals(Muscle.GLUTES, opened) }
    }

    @Test
    fun aMuscleNodeCarriesItsStepAndOpensTheMuscle() {
        var opened: Muscle? = null
        compose.setContent {
            OhTheme(dark = true) {
                OhBodyMap(
                    model = Models.recovery(BodySex.MALE),
                    geometry = geometry,
                    onMuscle = { opened = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Forearms")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(Muscle.FOREARMS, opened) }
    }

    @Test
    fun theTableAlternativeHasARowForEveryMuscle() {
        var rows: List<BodyMapTableRow> = emptyList()
        var description: String = ""
        compose.setContent {
            OhTheme(dark = true) {
                rows = bodyMapTableRows(Models.recovery(BodySex.MALE))
                description = bodyMapDescription(Models.recovery(BodySex.MALE))
            }
        }
        compose.waitForIdle()
        assertEquals(Muscle.entries.size, rows.size)
        assertTrue(rows.any { it.stateLabel == "Recovering" })
        assertTrue(rows.any { it.stateLabel == "Easing" })
        assertTrue(rows.any { it.stateLabel == "Ready" })
        assertTrue(description.startsWith("Recovery map:"))
        assertTrue(description.contains("was trained most recently"))
    }

    /**
     * A mini map does not draw the adipose band (`DESIGN.md` §9.1: 46 dp, three dp
     * strokes), so it must not pay for one either: the band is a raster of a few hundred
     * thousand pixels and nothing a 46 dp figure needs.
     */
    @Test
    fun aMiniMapNeverBuildsTheAdiposeBand() {
        val layer = AdiposeModel.compute(
            AdiposeInputs(sex = BodySex.MALE, bodyFatPercent = MINI_PROBE_BODY_FAT),
        )!!
        val view = geometry.view(BodySex.MALE, BodySide.FRONT)!!
        val thickness = app.openhealth.core.body.MuscleRegion.entries.associateWith { region ->
            (layer.thicknessFor(region) * view.viewBox.height).toFloat()
        }
        assertNull(AdiposeMask.cached(view, thickness), "this thickness is new")
        compose.setContent {
            OhTheme(dark = true) {
                OhBodyMap(
                    model = Models.recovery(BodySex.MALE).copy(adipose = layer),
                    geometry = geometry,
                    sides = setOf(BodySide.FRONT),
                    variant = BodyMapVariant.MINI,
                    synchronousLayers = true,
                )
            }
        }
        compose.waitForIdle()
        assertNull(AdiposeMask.cached(view, thickness), "a mini map built a band")
    }

    private companion object {
        const val MAP = "body-map-view"

        /** A body fat no other shot uses, so the cache probe can only see this test. */
        const val MINI_PROBE_BODY_FAT = 31.5
    }
}

/** The four fixture maps the goldens use. */
private object Models {
    private fun exercise(vararg primary: Muscle, id: String) = Exercise(
        id = id,
        name = id,
        primary = primary.toList(),
        secondary = emptyList(),
        equipment = Equipment.BARBELL,
        level = ExerciseLevel.INTERMEDIATE,
        mechanic = null,
        force = null,
        bodyPart = app.openhealth.core.body.BodyPart.of(primary.toList()),
        instructions = listOf("Do it."),
    )

    private fun adipose(sex: BodySex) = AdiposeModel.compute(
        AdiposeInputs(sex = sex, bodyFatPercent = 22.0),
    )!!

    fun recovery(sex: BodySex): BodyMapModel {
        val steps = listOf(
            Muscle.CHEST to RecoveryStep.RECOVERING,
            Muscle.UPPER_BACK to RecoveryStep.EASING,
            Muscle.LOWER_BACK to RecoveryStep.RECOVERING,
            Muscle.TRAPEZIUS to RecoveryStep.READY,
            Muscle.DELTOIDS to RecoveryStep.READY,
            Muscle.BICEPS to RecoveryStep.RECOVERING,
            Muscle.TRICEPS to RecoveryStep.EASING,
            Muscle.FOREARMS to RecoveryStep.READY,
            Muscle.ABS to RecoveryStep.RECOVERING,
            Muscle.OBLIQUES to RecoveryStep.RECOVERING,
            Muscle.SERRATUS to RecoveryStep.READY,
            Muscle.HIP_FLEXORS to RecoveryStep.EASING,
            Muscle.GLUTES to RecoveryStep.READY,
            Muscle.ADDUCTORS to RecoveryStep.READY,
            Muscle.QUADS to RecoveryStep.EASING,
            Muscle.HAMSTRINGS to RecoveryStep.RECOVERING,
            Muscle.CALVES to RecoveryStep.READY,
            Muscle.TIBIALIS to RecoveryStep.READY,
            Muscle.NECK to RecoveryStep.RECOVERING,
        )
        val states = steps.associate { (muscle, step) ->
            muscle to MuscleRecoveryState(
                muscle = muscle,
                step = step,
                hoursSinceLoad = when (step) {
                    RecoveryStep.RECOVERING -> 6.0
                    RecoveryStep.EASING -> 30.0
                    RecoveryStep.READY -> 60.0
                    RecoveryStep.UNTRAINED -> null
                },
                sets = 4.0,
            )
        }
        return BodyMapModel.recovery(
            sex = sex,
            states = states,
            adipose = adipose(sex),
        )
    }

    fun plan(sex: BodySex): BodyMapModel = BodyMapModel.plan(
        sex = sex,
        coverage = PlanCoverage.compute(
            listOf(
                PlannedExercise(exercise(Muscle.CHEST, id = "bench"), 4),
                PlannedExercise(exercise(Muscle.UPPER_BACK, id = "row"), 4),
                PlannedExercise(exercise(Muscle.DELTOIDS, id = "press"), 3),
                PlannedExercise(exercise(Muscle.QUADS, Muscle.GLUTES, id = "squat"), 5),
                PlannedExercise(exercise(Muscle.HAMSTRINGS, id = "deadlift"), 3),
                PlannedExercise(exercise(Muscle.BICEPS, id = "curl"), 3),
                PlannedExercise(exercise(Muscle.TRICEPS, id = "pushdown"), 3),
                PlannedExercise(exercise(Muscle.CALVES, id = "calf"), 4),
            ),
        ),
    )

    fun untrained(sex: BodySex): BodyMapModel = BodyMapModel(sex = sex, type = BodyMapType.RECOVERY)
}
