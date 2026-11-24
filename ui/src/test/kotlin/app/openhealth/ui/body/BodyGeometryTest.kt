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
        val muscles = muscleRegion(muscleUnion, bounds)
        val seam = Region()
        seam.setPath(silhouette, bounds)
        seam.op(muscles, Region.Op.DIFFERENCE)
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
        // The exit test itself (`BUILD-ROADMAP.md` §B4): the band never reaches under a
        // muscle, so a recovery colour can never sit on fat. The seam check above does
        // not imply it; a band cut against the seams alone would still cover every
        // muscle. The intersection is taken on the paths, where the boolean is exact:
        // rasterising the two shapes separately disagrees by up to a cell along a
        // shared edge and would need a tolerance to hide it.
        val underMuscle = android.graphics.Path(band).apply {
            op(muscleUnion, android.graphics.Path.Op.INTERSECT)
        }
        val underRegion = Region().apply { setPath(underMuscle, bounds) }
        // Skia's boolean operators flatten curves, so the cut leaves hairline slivers
        // along the shared edge: measured 0, 0, 7 and 94 square viewBox units against
        // muscle areas of 135 601 to 155 194, the worst 0.07 %. A band that was not cut
        // against the silhouette at all would overlap by tens of thousands of units, so
        // a thousandth of the muscle area separates the two cases by three orders of
        // magnitude.
        assertTrue(
            area(underRegion) * UNDER_MUSCLE_BOUND <= area(muscles),
            "the fat layer reaches under a muscle on $sex $side: " +
                "${area(underRegion)} of ${area(muscles)}",
        )
    }

    /**
     * The band cache keeps a scrub fast without growing for ever: a slider that walks
     * through a hundred thicknesses must not hold a hundred paths.
     */
    @Test
    fun theBandCacheIsBoundedAndKeepsTheNewest() {
        val view = geometry.view(BodySex.MALE, BodySide.FRONT)!!
        val keys = (1..CACHE_PROBE).map { step ->
            MuscleRegion.entries.associateWith { CACHE_BASE + step }
        }
        keys.forEach { thickness ->
            AdiposeGeometry.store(view, thickness, android.graphics.Path())
        }
        assertEquals(null, AdiposeGeometry.cached(view, keys.first()), "the oldest band is gone")
        assertNotNull(AdiposeGeometry.cached(view, keys.last()), "the newest band is kept")
    }

    /** The same thickness is built once; that is what survives a rotation. */
    @Test
    fun theSameThicknessIsReturnedFromTheCache() {
        val view = geometry.view(BodySex.MALE, BodySide.BACK)!!
        val thickness = MuscleRegion.entries.associateWith { CACHE_BASE }
        val band = AdiposeGeometry.layer(view, thickness)
        AdiposeGeometry.store(view, thickness, band)
        assertTrue(AdiposeGeometry.cached(view, thickness) === band)
    }

    @Test
    fun thereIsNoFatBandWithoutEnoughData() {
        val layer = AdiposeModel.compute(AdiposeInputs(sex = BodySex.FEMALE))
        assertEquals(null, layer)
    }

    /**
     * The card reserves the map's height before the geometry is parsed, so it does not
     * jump when it arrives (`DESIGN.md` Appendix B). The reserved ratio has to be the
     * real one, and one view on its own has to reserve half the pair.
     */
    @Test
    fun theReservedHeightMatchesTheShippedViewBoxes() {
        BodySex.entries.forEach { sex ->
            val front = geometry.view(sex, BodySide.FRONT)!!
            val back = geometry.view(sex, BodySide.BACK)!!
            assertEquals(
                front.viewBox.aspectRatio.toDouble(),
                back.viewBox.aspectRatio.toDouble(),
                RATIO_TOLERANCE,
                "$sex: the two views share a viewBox, as the reserve assumes",
            )
            assertEquals(
                (front.viewBox.aspectRatio + back.viewBox.aspectRatio).toDouble(),
                placeholderRatioSum(sex, 2).toDouble(),
                RATIO_TOLERANCE,
                "$sex: the pair reserves both views",
            )
            assertEquals(
                front.viewBox.aspectRatio.toDouble(),
                placeholderRatioSum(sex, 1).toDouble(),
                RATIO_TOLERANCE,
                "$sex: one view reserves one view, not two",
            )
        }
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

        /** More thicknesses than the cache may hold, so the oldest has to fall out. */
        const val CACHE_PROBE = 24
        const val CACHE_BASE = 3f
        const val RATIO_TOLERANCE = 1e-6

        /** The band may cover at most one thousandth of the muscle area. */
        const val UNDER_MUSCLE_BOUND = 1_000L
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
