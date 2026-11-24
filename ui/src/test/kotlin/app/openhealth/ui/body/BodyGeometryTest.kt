package app.openhealth.ui.body

import android.content.Context
import android.graphics.Region
import app.openhealth.core.body.AdiposeInputs
import app.openhealth.core.body.AdiposeLayer
import app.openhealth.core.body.AdiposeModel
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.BodySide
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.MuscleRegion
import app.openhealth.ui.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The geometry and the adipose layer (`DESIGN.md` §9.1, §9.1.1). These tests read the real
 * shipped TSV and use real Skia paths, so they fail if the resource drifts from the muscle
 * taxonomy or if the fat band ever reaches under a muscle.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BodyGeometryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val geometry: BodyGeometry by lazy {
        BodyGeometry.parse(
            context.resources.openRawResource(R.raw.body_paths)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() },
        )
    }

    @Test
    fun allFourViewsParseWithAViewBox() {
        assertEquals(4, geometry.views.size)
        BodySex.entries.forEach { sex ->
            BodySide.entries.forEach { side ->
                val view = assertNotNull(geometry.view(sex, side), "$sex $side")
                assertTrue(view.viewBox.width > 0f && view.viewBox.height > 0f)
                assertTrue(view.parts.isNotEmpty())
            }
        }
    }

    @Test
    fun everyMuscleIsDrawnSomewhereAndNoPartIsUnknown() {
        val drawn = geometry.views.values
            .flatMap { view -> view.parts.mapNotNull { it.muscle } }
            .toSet()
        assertEquals(Muscle.entries.toSet(), drawn)
    }

    @Test
    fun everyMuscleHasATappableRegion() {
        val view = geometry.view(BodySex.MALE, BodySide.FRONT)!!
        val regions = bodyHitRegions(view)
        assertEquals(
            view.parts.mapNotNull { it.muscle }.toSet(),
            regions.keys,
        )
        regions.forEach { (muscle, region) ->
            assertFalse(region.isEmpty, "${muscle.key} has no tappable area")
        }
    }

    @Test
    fun aMuscleIsOneRegionEvenWhenItIsDrawnInManyShapes() {
        val view = geometry.view(BodySex.MALE, BodySide.FRONT)!!
        val absParts = view.parts.filter { it.muscle == Muscle.ABS }
        assertTrue(absParts.sumOf { it.paths.size } > 1, "abs are drawn as several shapes")
        assertFalse(bodyHitRegions(view).getValue(Muscle.ABS).isEmpty)
    }

    @Test
    fun theAdiposeBandIsDrawnOnlyOutsideTheMuscles() {
        val layer = AdiposeModel.compute(
            AdiposeInputs(sex = BodySex.MALE, bodyFatPercent = 35.0),
        )!!
        BodySex.entries.forEach { sex ->
            BodySide.entries.forEach { side -> assertBandOutsideSeams(sex, side, layer) }
        }
    }

    private fun assertBandOutsideSeams(sex: BodySex, side: BodySide, layer: AdiposeLayer) {
        val view = geometry.view(sex, side)!!
        val thickness = MuscleRegion.entries.associateWith { region ->
            (layer.thicknessFor(region) * view.viewBox.height).toFloat()
        }
        val band = AdiposeGeometry.layer(view, thickness)
        assertFalse(band.isEmpty, "the fat layer is drawn on $sex $side")
        val bounds = Region(
            view.viewBox.x.toInt(),
            view.viewBox.y.toInt(),
            (view.viewBox.x + view.viewBox.width).toInt(),
            (view.viewBox.y + view.viewBox.height).toInt(),
        )
        val muscleUnion = android.graphics.Path().apply {
            view.parts.filter { it.muscle != null }.forEach { part ->
                part.paths.forEach(::addPath)
            }
        }
        // The bug this guards is fat filling the seam between two muscles: everything the
        // closed silhouette adds over the raw union is a seam, and the band must not touch
        // any of it.
        val silhouette = android.graphics.Path().apply {
            addPath(muscleUnion)
            addPath(strokeOutline(muscleUnion, AdiposeGeometry.SEAM_UNITS / 2f))
        }
        val seam = Region()
        seam.setPath(silhouette, bounds)
        seam.op(muscleRegion(muscleUnion, bounds), Region.Op.DIFFERENCE)
        val fat = Region()
        fat.setPath(band, bounds)
        val overlap = Region(fat)
        overlap.op(seam, Region.Op.INTERSECT)
        // A boolean difference is exact; rasterising both sides of a one-unit boundary
        // can leave a single stray cell, nothing more.
        assertTrue(
            area(overlap) <= SEAM_TOLERANCE,
            "the fat layer fills a seam on $sex $side: $overlap",
        )
    }

    @Test
    fun thereIsNoFatBandWithoutEnoughData() {
        val layer = AdiposeModel.compute(AdiposeInputs(sex = BodySex.FEMALE))
        assertEquals(null, layer)
    }

    @Test
    fun aFemaleViewHasItsOwnViewBox() {
        val female = geometry.view(BodySex.FEMALE, BodySide.FRONT)!!
        val male = geometry.view(BodySex.MALE, BodySide.FRONT)!!
        assertTrue(female.viewBox.height != male.viewBox.height)
    }

    private fun area(region: Region): Long {
        var total = 0L
        val iterator = android.graphics.RegionIterator(region)
        val rect = android.graphics.Rect()
        while (iterator.next(rect)) total += rect.width().toLong() * rect.height()
        return total
    }

    private fun muscleRegion(path: android.graphics.Path, bounds: Region): Region {
        val region = Region()
        region.setPath(path, bounds)
        return region
    }

    private fun strokeOutline(path: android.graphics.Path, width: Float): android.graphics.Path {
        val ring = android.graphics.Path()
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = width
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }.getFillPath(path, ring)
        return ring
    }

    private companion object {
        const val SEAM_TOLERANCE = 4L
    }

    @Test
    fun everyViewDrawsTheSilhouettePartsThatTakeNoColour() {
        val view = geometry.view(BodySex.MALE, BodySide.FRONT)!!
        val inert = view.parts.filter { it.muscle == null }.map { it.key }.toSet()
        assertTrue("head" in inert && "hands" in inert)
    }

    /** A path's own bounds are not a hit test; the region is. */
    @Test
    fun theRegionKnowsAPointInsideAndOutside() {
        val view = geometry.view(BodySex.MALE, BodySide.FRONT)!!
        val chest = bodyHitRegions(view).getValue(Muscle.CHEST)
        val bounds = view.parts.first { it.muscle == Muscle.CHEST }.bounds
        val inside =
            Region(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt(),
            )
        inside.op(chest, Region.Op.INTERSECT)
        assertFalse(inside.isEmpty)
    }
}
