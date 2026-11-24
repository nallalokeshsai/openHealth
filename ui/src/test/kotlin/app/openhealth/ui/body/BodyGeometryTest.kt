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
            BodySide.entries.forEach { side ->
                val mask = bandRaster(sex, side, layer)
                assertTrue(mask.band.any { it }, "the fat layer is drawn on $sex $side")
                assertFalse(
                    mask.band.count { it } * 4 > mask.band.size,
                    "the fat layer covers a quarter of the mask on $sex $side",
                )
                // The exit test (`BUILD-ROADMAP.md` §B4): no recovery colour can sit on
                // fat, so the band may not reach under a muscle.
                // The band's edge is a one-pixel ramp, so a fringe of its pixels shares a
                // pixel with the muscle it starts from; a band that was not cut against the
                // muscles at all overlaps half the figure.
                val overlap = mask.band.indices.count { mask.band[it] && mask.muscles[it] }
                assertTrue(
                    overlap * OVERLAP_DIVISOR <= mask.band.count { it },
                    "the fat layer reaches under a muscle on $sex $side ($overlap)",
                )
            }
        }
    }

    /**
     * The inputs the path version could not draw: 22 % body fat scaled by neck, chest and
     * waist tape. Skia's `Path.op` failed on them, the band came out as the whole figure
     * and the phone drew a filled gold back with the muscles painted back on top. The mask
     * has to keep it a ring, whatever the thickness map looks like.
     */
    @Test
    fun aTapeScaledLayerIsARingAndNotAFilledFigure() {
        val layer = AdiposeModel.compute(
            AdiposeInputs(
                sex = BodySex.MALE,
                bodyFatPercent = 22.0,
                heightCm = 178.0,
                tapeCm = mapOf("neck" to 38.0, "chest" to 100.0, "waist" to 90.0),
            ),
        )!!
        BodySex.entries.forEach { sex ->
            BodySide.entries.forEach { side ->
                val view = geometry.view(sex, side)!!
                val mask = bandRaster(sex, side, layer)
                val band = mask.band
                assertTrue(band.any { it }, "the fat layer is drawn on $sex $side")
                assertFalse(
                    band.count { it } * 3 > band.size,
                    "the tape-scaled band fills the figure on $sex $side",
                )
                val overlap = band.indices.count { band[it] && mask.muscles[it] }
                assertTrue(
                    overlap * OVERLAP_DIVISOR <= band.count { it },
                    "the tape-scaled band reaches under a muscle on $sex $side ($overlap)",
                )
                // The middle of the figure at three heights: muscle, seam or body on every
                // view, and band on the filled figure the path version produced.
                listOf(0.35f, 0.45f, 0.55f).forEach { fraction ->
                    assertFalse(
                        bandAt(band, view, view.viewBox.x + view.viewBox.width / 2, fraction),
                        "$sex $side: the band fills the middle at ${fraction * 100} %",
                    )
                }
            }
        }
    }

    /** The band and, drawn separately, the muscles, one flag per mask pixel. */
    private data class Mask(val band: BooleanArray, val muscles: BooleanArray)

    private fun bandRaster(sex: BodySex, side: BodySide, layer: AdiposeLayer): Mask {
        val view = geometry.view(sex, side)!!
        val mask = AdiposeMask.build(view, thicknessOf(layer, view))
        val width = mask.width
        val height = mask.height
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        // A band pixel is one the edge ramp has made solid: the ramp's own fringe is a
        // fraction of a pixel either side of the contour.
        val band = BooleanArray(pixels.size) { (pixels[it] ushr 24) >= 128 }

        val bitmap = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bitmap)
        val pxPerUnit = AdiposeMask.HEIGHT_PX / view.viewBox.height
        canvas.scale(pxPerUnit, pxPerUnit)
        canvas.translate(-view.viewBox.x, -view.viewBox.y)
        val paint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
            color = 0xFF000000.toInt()
        }
        view.parts.filter { it.muscle != null }.forEach { part ->
            part.paths.forEach { path -> canvas.drawPath(path, paint) }
        }
        val musclePixels = IntArray(width * height)
        bitmap.getPixels(musclePixels, 0, width, 0, 0, width, height)
        val muscles = BooleanArray(musclePixels.size) { (musclePixels[it] ushr 24) != 0 }
        return Mask(band, muscles)
    }

    private fun bandAt(band: BooleanArray, view: GeometryView, x: Float, y: Float): Boolean {
        val pxPerUnit = AdiposeMask.HEIGHT_PX / view.viewBox.height
        val width = (view.viewBox.width * pxPerUnit).toInt()
        val mx = ((x - view.viewBox.x) * pxPerUnit).toInt()
        val my = ((y - view.viewBox.y) * pxPerUnit).toInt()
        if (mx !in 0 until width || my < 0) return false
        val index = my * width + mx
        return index in band.indices && band[index]
    }

    private fun thicknessOf(layer: AdiposeLayer, view: GeometryView): Map<MuscleRegion, Float> =
        MuscleRegion.entries.associateWith { region ->
            (layer.thicknessFor(region) * view.viewBox.height).toFloat()
        }

    /**
     * The band cache keeps a scrub fast without growing for ever: a slider that walks
     * through a hundred thicknesses must not hold a hundred masks.
     */
    @Test
    fun theBandCacheIsBoundedAndKeepsTheNewest() {
        val view = geometry.view(BodySex.MALE, BodySide.FRONT)!!
        val keys = (1..CACHE_PROBE).map { step ->
            MuscleRegion.entries.associateWith { CACHE_BASE + step }
        }
        val one = android.graphics.Bitmap.createBitmap(
            1,
            1,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        keys.forEach { thickness -> AdiposeMask.store(view, thickness, one) }
        assertEquals(null, AdiposeMask.cached(view, keys.first()), "the oldest band is gone")
        assertNotNull(AdiposeMask.cached(view, keys.last()), "the newest band is kept")
    }

    /** The same thickness is built once; that is what survives a rotation. */
    @Test
    fun theSameThicknessIsReturnedFromTheCache() {
        val view = geometry.view(BodySex.MALE, BodySide.BACK)!!
        val thickness = MuscleRegion.entries.associateWith { CACHE_BASE }
        val band = AdiposeMask.build(view, thickness)
        AdiposeMask.store(view, thickness, band)
        assertTrue(AdiposeMask.cached(view, thickness) === band)
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

    private companion object {
        /** More thicknesses than the cache may hold, so the oldest has to fall out. */
        const val CACHE_PROBE = 24
        const val CACHE_BASE = 3f
        const val RATIO_TOLERANCE = 1e-6

        /** The band's one-pixel edge ramp may share at most a hundredth of itself. */
        const val OVERLAP_DIVISOR = 100
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
