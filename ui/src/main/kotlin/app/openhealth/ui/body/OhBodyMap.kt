package app.openhealth.ui.body

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.BodySide
import app.openhealth.core.body.MeasurementSite
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.MuscleRegion
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.ui.R
import app.openhealth.ui.components.ohClickable
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.theme.OhAlpha
import app.openhealth.ui.theme.OhColors
import app.openhealth.ui.theme.OhRamp
import app.openhealth.ui.theme.OhTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The body map (`DESIGN.md` §9.1, §9.1.1): the MuscleMap geometry drawn as Compose paths,
 * whole muscles only, front and back side by side at equal height and never mirrored in
 * RTL. It carries one accessibility node per muscle — TalkBack hears "Forearms" once —
 * while every drawn shape of that muscle stays tappable.
 *
 * The geometry is parsed once per process and loaded off the first frame; until it
 * arrives the pair keeps its height and draws nothing (`DESIGN.md` Appendix B).
 */
@Composable
fun OhBodyMap(
    model: BodyMapModel,
    modifier: Modifier = Modifier,
    variant: BodyMapVariant = BodyMapVariant.FULL,
    sides: Set<BodySide> = setOf(BodySide.FRONT, BodySide.BACK),
    onMuscle: ((Muscle) -> Unit)? = null,
    onShowTable: (() -> Unit)? = null,
    /** A caller's accessible sentence, for a figure that is not the recovery map. */
    descriptionOverride: String? = null,
    /** Anchors on the front view for the measurement silhouette (`DESIGN.md` §9.3). */
    anchors: Map<MeasurementSite, Boolean> = emptyMap(),
    onAnchor: ((MeasurementSite) -> Unit)? = null,
    geometry: BodyGeometry? = null,
    /**
     * Build the adipose band inline instead of on a background dispatcher. Screenshot
     * tests set this so a golden never races the band; production leaves it false so the
     * main thread is never blocked by path boolean operations.
     */
    synchronousLayers: Boolean = false,
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(geometry ?: BodyGeometry.cached()) }
    LaunchedEffect(geometry) {
        if (loaded == null) {
            val first = withContext(Dispatchers.IO) {
                runCatching { BodyGeometry.load(context) }
            }
            loaded = first.getOrNull()
            first.exceptionOrNull()?.let { failure ->
                // A silent failure would leave the card blank forever; name the failure in
                // the local log (no personal data) and try once more after the first frame.
                android.util.Log.e(TAG, "Body geometry failed to load", failure)
                val retry = withContext(Dispatchers.IO) {
                    runCatching { BodyGeometry.load(context) }
                }
                loaded = retry.getOrNull()
                retry.exceptionOrNull()?.let {
                    android.util.Log.e(TAG, "Body geometry failed again; the card stays empty", it)
                }
            }
        }
    }
    val drawings = loaded
    if (drawings == null) {
        Placeholder(variant = variant, sex = model.sex, sides = sides, modifier = modifier)
        return
    }
    val front = drawings.view(model.sex, BodySide.FRONT)?.takeIf { BodySide.FRONT in sides }
    val back = drawings.view(model.sex, BodySide.BACK)?.takeIf { BodySide.BACK in sides }
    val description = descriptionOverride ?: bodyMapDescription(model)
    val tableLabel = stringResource(R.string.body_map_show_table)
    val paints = rememberPaints(model, variant)
    // One accessibility node per muscle, never one per view: TalkBack hears "Forearms"
    // once even though the forearm is drawn on the front and the back (DESIGN.md §9.1).
    val nodeSide = remember(front, back) {
        buildMap {
            listOfNotNull(front, back).forEach { view ->
                view.parts.mapNotNull { part -> part.muscle?.let { it to view.view.side } }
                    .forEach { (muscle, side) -> if (muscle !in this) put(muscle, side) }
            }
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                if (onShowTable != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(tableLabel) {
                            onShowTable()
                            true
                        },
                    )
                }
            },
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val maxHeight = when (variant) {
            BodyMapVariant.FULL -> minOf(
                screenHeight * OhTheme.dims.bodyMapMaxFraction,
                OhTheme.dims.bodyMapMax,
            )

            BodyMapVariant.MINI -> OhTheme.dims.bodyMapMiniHeight
            BodyMapVariant.EVENT_MINI -> OhTheme.dims.bodyMapEventMini
        }
        val gap = if (variant == BodyMapVariant.FULL) {
            OhTheme.dims.bodyMapPairGap
        } else {
            OhTheme.dims.bodyMapMiniGap
        }
        val ratios = (front?.viewBox?.aspectRatio ?: 0f) + (back?.viewBox?.aspectRatio ?: 0f)
        val height = minOf(maxHeight, (maxWidth - gap) / ratios.coerceAtLeast(1f))
        // Anatomy is anatomy: the pair is never mirrored in RTL (`DESIGN.md` §11.5).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    gap,
                    Alignment.CenterHorizontally,
                ),
            ) {
                front?.let {
                    ViewPanel(
                        view = it,
                        model = model,
                        variant = variant,
                        height = height,
                        paints = paints,
                        onMuscle = onMuscle,
                        nodeSide = nodeSide,
                        synchronousLayers = synchronousLayers,
                        anchors = anchors,
                        onAnchor = onAnchor,
                    )
                }
                back?.let {
                    ViewPanel(
                        view = it,
                        model = model,
                        variant = variant,
                        height = height,
                        paints = paints,
                        onMuscle = onMuscle,
                        nodeSide = nodeSide,
                        synchronousLayers = synchronousLayers,
                        anchors = anchors,
                        onAnchor = onAnchor,
                    )
                }
            }
        }
    }
}

@Composable
private fun Placeholder(
    variant: BodyMapVariant,
    sex: BodySex,
    sides: Set<BodySide>,
    modifier: Modifier,
) {
    val fixed = when (variant) {
        BodyMapVariant.FULL -> null
        BodyMapVariant.MINI -> OhTheme.dims.bodyMapMiniHeight
        BodyMapVariant.EVENT_MINI -> OhTheme.dims.bodyMapEventMini
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val pairGap = if (variant == BodyMapVariant.FULL) {
            OhTheme.dims.bodyMapPairGap
        } else {
            OhTheme.dims.bodyMapMiniGap
        }
        // The same arithmetic the drawn pair uses, from the known viewBox ratios, so the
        // card does not jump when the geometry arrives (DESIGN.md Appendix B). One view
        // on its own is half the pair's width and has no gap to pay for.
        val shown = sides.size.coerceAtLeast(1)
        val available = if (shown > 1) maxWidth - pairGap else maxWidth
        val ratios = placeholderRatioSum(sex, shown)
        val maxHeight = fixed ?: minOf(
            LocalConfiguration.current.screenHeightDp.dp * OhTheme.dims.bodyMapMaxFraction,
            OhTheme.dims.bodyMapMax,
        )
        val height = minOf(maxHeight, available / ratios)
        Box(modifier = Modifier.fillMaxWidth().height(height))
    }
}

/**
 * At 44 dp a muscle is one finger wide even when the shape is a few dp across
 * (`DESIGN.md` §11.1): a tap that misses every path falls back to the nearest muscle
 * whose bounds, grown by half a touch target, contain it.
 */
private fun GeometryView.nearestWithin(x: Float, y: Float, reach: Float): Muscle? =
    parts.asSequence()
        .filter { it.muscle != null }
        .filter { part ->
            x >= part.bounds.left - reach &&
                x <= part.bounds.right + reach &&
                y >= part.bounds.top - reach &&
                y <= part.bounds.bottom + reach
        }
        .minByOrNull { part ->
            val dx = (part.bounds.left + part.bounds.right) / 2f - x
            val dy = (part.bounds.top + part.bounds.bottom) / 2f - y
            dx * dx + dy * dy
        }
        ?.muscle

/** A tap reaches half a touch target past a thin muscle (`DESIGN.md` §11.1). */
private const val TOUCH_REACH_HALVES = 2f

private const val TAG = "OhBodyMap"

private const val MALE_RATIO = 727f / 1280f
private const val FEMALE_RATIO = 650f / 1450f

/**
 * The aspect ratios the placeholder reserves before the geometry is parsed: one view's
 * ratio per side shown. Both views of a sex share a viewBox (`res/raw/body_paths.tsv`),
 * which `BodyGeometryTest` keeps true.
 */
internal fun placeholderRatioSum(sex: BodySex, sides: Int): Float =
    (if (sex == BodySex.MALE) MALE_RATIO else FEMALE_RATIO) * sides

data class AnimatedPaint(val track: Color, val overlay: Color?)

@Composable
private fun rememberPaints(
    model: BodyMapModel,
    variant: BodyMapVariant,
): Map<Muscle, AnimatedPaint> {
    val colors = OhTheme.colors
    val motion = OhTheme.motion
    val colorSpec = tween<Color>(motion.duration(motion.med), easing = motion.ease)
    val alphaSpec = tween<Float>(motion.duration(motion.med), easing = motion.ease)
    return Muscle.entries.associateWith { muscle ->
        val target = targetPaint(model, muscle, variant, colors)
        val track = animateColorAsState(target.track, colorSpec, label = "body-${muscle.key}").value
        val alpha = animateFloatAsState(
            target.overlay?.alpha ?: 0f,
            alphaSpec,
            label = "body-alpha-${muscle.key}",
        ).value
        AnimatedPaint(track = track, overlay = target.overlay?.copy(alpha = alpha))
    }
}

private fun targetPaint(
    model: BodyMapModel,
    muscle: Muscle,
    variant: BodyMapVariant,
    colors: OhColors,
): AnimatedPaint {
    val base = baseFill(colors, variant)
    return when (val shade = model.shade(muscle)) {
        is MuscleShade.Recovery -> AnimatedPaint(
            track = when (shade.step) {
                RecoveryStep.UNTRAINED -> base
                RecoveryStep.RECOVERING -> colors.recActive
                RecoveryStep.EASING -> colors.recEasing
                RecoveryStep.READY -> colors.recReady
            },
            overlay = null,
        )

        is MuscleShade.Plan -> {
            val step = OhRamp.of(colors, shade.share)
            AnimatedPaint(track = step.track, overlay = step.overlay)
        }

        MuscleShade.None -> AnimatedPaint(track = base, overlay = null)
    }
}

private fun baseFill(colors: OhColors, variant: BodyMapVariant): Color = if (
    variant == BodyMapVariant.FULL
) {
    colors.bodyBase
} else {
    colors.label.copy(alpha = OhAlpha.bodyMiniBase)
}

@Composable
private fun ViewPanel(
    view: GeometryView,
    model: BodyMapModel,
    variant: BodyMapVariant,
    height: Dp,
    paints: Map<Muscle, AnimatedPaint>,
    onMuscle: ((Muscle) -> Unit)?,
    nodeSide: Map<Muscle, BodySide>,
    synchronousLayers: Boolean,
    anchors: Map<MeasurementSite, Boolean>,
    onAnchor: ((MeasurementSite) -> Unit)?,
) {
    val colors = OhTheme.colors
    val dims = OhTheme.dims
    val vb = view.viewBox
    val width = height * vb.aspectRatio
    val scale = height.value / vb.height
    val mini = variant != BodyMapVariant.FULL
    val strokeDp = if (mini) dims.bodyMapMiniStroke else dims.bodyMapStroke
    val selectedStrokeDp = if (mini) dims.bodyMapMiniStroke else dims.bodyMapSelectedStroke
    val hatchColor = colors.surface.copy(alpha = OhAlpha.bodyHatch)
    val fatColor = colors.fat.copy(alpha = colors.fatLayerAlpha)
    val baseColor = baseFill(colors, variant)
    // The mask carries alpha only; the colour and the layer's own alpha come from the paint.
    val fatPaint = remember(fatColor) {
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = PorterDuffColorFilter(fatColor.toArgb(), PorterDuff.Mode.SRC_IN)
        }
    }
    // Building the adipose band is a set of boolean path operations over the whole
    // silhouette; on a slow device that is seconds of work, so it never runs on the main
    // thread and its result is cached across rotation. The muscles draw first, the band
    // follows a frame or two later.
    // A mini map never draws the band (below), so it is never built for one either.
    val fatThickness = model.adipose?.takeUnless { mini }?.let { layer ->
        MuscleRegion.entries.associateWith { region ->
            (layer.thicknessFor(region) * vb.height).toFloat()
        }
    }
    val fatMask by produceState<Bitmap?>(
        initialValue = null,
        // The band belongs to a view as much as to a thickness: the same figure seen as
        // the other sex is a different silhouette, so both are keys.
        view,
        fatThickness,
    ) {
        // Reset on every key change: a map without an adipose layer (or with the chip
        // switched off) must not keep the previous band.
        value = fatThickness?.let { thickness ->
            AdiposeMask.cached(view, thickness) ?: if (synchronousLayers) {
                AdiposeMask.build(view, thickness).also {
                    AdiposeMask.store(view, thickness, it)
                }
            } else {
                null
            }
        }
        if (value == null && fatThickness != null) {
            value = withContext(Dispatchers.Default) {
                AdiposeMask.build(view, fatThickness).also {
                    AdiposeMask.store(view, fatThickness, it)
                }
            }
        }
    }
    val hitRegions = remember(view) {
        bodyHitRegions(view)
    }
    val labels = Muscle.entries.associateWith { muscle -> muscleLabel(muscle) }
    val states = Muscle.entries.associateWith { muscle ->
        when (val shade = model.shade(muscle)) {
            is MuscleShade.Recovery -> recoveryStateLabel(shade.step, shade.hoursSinceLoad)
            is MuscleShade.Plan -> stringResource(
                R.string.body_map_table_weekly_sets,
                shade.weeklySets,
            )

            MuscleShade.None -> if (model.type == BodyMapType.PLAN) {
                stringResource(R.string.body_map_plan_none)
            } else {
                stringResource(R.string.recovery_untrained)
            }
        }
    }

    Box(modifier = Modifier.height(height).width(width)) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(view, onMuscle) {
                detectTapGestures { offset ->
                    if (onMuscle == null) return@detectTapGestures
                    val unit = size.height / vb.height
                    val vx = offset.x / unit + vb.x
                    val vy = offset.y / unit + vb.y
                    val reachDp = dims.touchTarget.value / TOUCH_REACH_HALVES
                    val reach = reachDp * vb.height / size.height.toDp().value
                    val touched = hitRegions.entries
                        .lastOrNull { (_, region) -> region.contains(vx.toInt(), vy.toInt()) }
                        ?.key ?: view.nearestWithin(vx, vy, reach)
                    if (touched != null) onMuscle(touched)
                }
            },
        ) {
            val scalePx = size.height / vb.height
            withTransform({
                translate(-vb.x * scalePx, -vb.y * scalePx)
                scale(scalePx, scalePx, Offset.Zero)
            }) {
                val strokeUnits = strokeDp.toPx() / scalePx
                val stroke = Stroke(
                    width = strokeUnits,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                // The untrained base is painted once, for the whole silhouette. A muscle
                // than has no colour of its own is not filled again on top of it: two
                // coats of 11 % label would be 21 % (DESIGN.md §9.1).
                view.parts.forEach { part ->
                    part.paths.forEach { path ->
                        drawPath(path.asComposePath(), color = baseColor)
                    }
                }
                if (!mini) {
                    fatMask?.let { mask ->
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawBitmap(
                                mask,
                                null,
                                RectF(vb.x, vb.y, vb.x + vb.width, vb.y + vb.height),
                                fatPaint,
                            )
                        }
                    }
                }
                if (mini) {
                    // At 46 dp the 3 dp separation stroke is wider than most muscles; the
                    // fill goes on last so the colour survives (DESIGN.md §9.1).
                    view.parts.filter { it.muscle != null }.forEach { part ->
                        part.paths.forEach { path ->
                            drawPath(path.asComposePath(), color = colors.surface, style = stroke)
                        }
                    }
                }
                view.parts.filter { it.muscle != null }.forEach { part ->
                    val paint = paints.getValue(part.muscle!!)
                    if (paint.track != baseColor) {
                        part.paths.forEach { path ->
                            drawPath(path.asComposePath(), color = paint.track)
                            paint.overlay?.let { overlay ->
                                drawPath(path.asComposePath(), color = overlay)
                            }
                        }
                    }
                }
                if (!mini) {
                    view.parts.filter { it.muscle != null }.forEach { part ->
                        part.paths.forEach { path ->
                            drawPath(path.asComposePath(), color = colors.surface, style = stroke)
                        }
                    }
                }
                if (!mini) {
                    drawHatches(
                        view = view,
                        model = model,
                        color = hatchColor,
                        spacingUnits = dims.bodyMapHatchSpacing.toPx() / scalePx,
                        strokeUnits = dims.bodyMapHatchStroke.toPx() / scalePx,
                    )
                }
                model.selected?.let { selected ->
                    view.parts.filter { it.muscle == selected }.forEach { part ->
                        part.paths.forEach { path ->
                            val glow = (
                                selectedStrokeDp + dims.bodyMapSelectedGlow * GLOW_BOTH_SIDES
                                ).toPx() / scalePx
                            drawPath(
                                path.asComposePath(),
                                color = colors.surface,
                                style = Stroke(width = glow, join = StrokeJoin.Round),
                            )
                            drawPath(
                                path.asComposePath(),
                                color = colors.label,
                                style = Stroke(
                                    width = selectedStrokeDp.toPx() / scalePx,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }
                    }
                }
            }
        }
        // The measurement silhouette's anchors (`DESIGN.md` §9.3): one 44 dp target per
        // measured site on the front view, over the figure, never replacing a muscle tap.
        if (view.view.side == BodySide.FRONT && anchors.isNotEmpty() && onAnchor != null) {
            MeasurementSite.anchors.forEach { site ->
                val fraction = FRONT_ANCHOR_POSITIONS[site] ?: return@forEach
                val measured = anchors[site] == true
                val label = measurementSiteLabel(site)
                val state = stringResource(
                    if (measured) {
                        R.string.measure_anchor_measured
                    } else {
                        R.string.measure_anchor_open
                    },
                )
                val target = OhTheme.dims.touchTarget.value
                val cx = vb.width * fraction.first * scale
                val cy = vb.height * fraction.second * scale
                val anchorInteraction = rememberInteraction()
                Box(
                    modifier = Modifier
                        .offset(x = (cx - target / 2f).dp, y = (cy - target / 2f).dp)
                        .size(OhTheme.dims.touchTarget)
                        .ohClickable(
                            interactionSource = anchorInteraction,
                            enabled = onAnchor != null,
                            onClick = { onAnchor?.invoke(site) },
                        )
                        .semantics {
                            contentDescription = label
                            stateDescription = state
                            if (onAnchor != null) {
                                role = Role.Button
                                onClick(label = label) {
                                    onAnchor(site)
                                    true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(OhTheme.dims.measureAnchor)) {
                        if (measured) {
                            drawCircle(
                                color = colors.acc.copy(alpha = OhAlpha.anchorGlow),
                                radius = size.minDimension / 2f,
                            )
                        }
                        drawCircle(
                            color = if (measured) colors.acc else colors.label3,
                            radius = size.minDimension / ANCHOR_DOT_DIVISOR,
                        )
                    }
                }
            }
        }
        // One accessibility node per muscle; the drawn shapes stay one pointer target.
        // On the measurement silhouette the anchors are the controls, so the muscle
        // nodes would repeat "Chest" twice under the same finger; they are left out there.
        if (onMuscle != null || anchors.isEmpty()) {
            view.parts.filter { it.muscle != null }.reversed().forEach { part ->
                val muscle = part.muscle ?: return@forEach
                if (nodeSide[muscle] != view.view.side) return@forEach
                val bounds = part.bounds
                // At least one 44 dp target per muscle (DESIGN.md §11.1), centred on the
                // muscle's own bounds: a narrower box is covered by its neighbours and can
                // drop out of the platform accessibility tree.
                val minSide = OhTheme.dims.touchTarget.value
                val boxWidth = maxOf(bounds.width() * scale, minSide)
                val boxHeight = maxOf(bounds.height() * scale, minSide)
                Box(
                    modifier = Modifier
                        .offset(
                            x = (
                                ((bounds.left + bounds.right) / 2f - vb.x) * scale -
                                    boxWidth / 2f
                                ).dp,
                            y = (
                                ((bounds.top + bounds.bottom) / 2f - vb.y) * scale -
                                    boxHeight / 2f
                                ).dp,
                        )
                        .size(width = boxWidth.dp, height = boxHeight.dp)
                        .semantics {
                            contentDescription = labels.getValue(muscle)
                            stateDescription = states.getValue(muscle)
                            if (onMuscle != null) role = Role.Button
                            selected = model.selected == muscle
                            if (onMuscle != null) {
                                onClick(label = labels.getValue(muscle)) {
                                    onMuscle(muscle)
                                    true
                                }
                            }
                        },
                )
            }
        }
    }
}

private const val GLOW_BOTH_SIDES = 2

/**
 * Where each measurement anchor sits on the front view, as a fraction of the viewBox.
 * The MuscleMap bounds span both limbs for a paired muscle, so their centre is the spine;
 * these coordinates put the dot on the anatomy instead (`DESIGN.md` §9.3).
 */
@Suppress("MagicNumber")
private val FRONT_ANCHOR_POSITIONS: Map<MeasurementSite, Pair<Float, Float>> = mapOf(
    MeasurementSite.NECK to (0.50f to 0.14f),
    MeasurementSite.SHOULDERS to (0.38f to 0.20f),
    MeasurementSite.CHEST to (0.52f to 0.27f),
    MeasurementSite.WAIST to (0.50f to 0.40f),
    MeasurementSite.HIPS to (0.50f to 0.50f),
    MeasurementSite.UPPER_ARM to (0.30f to 0.34f),
    MeasurementSite.FOREARM to (0.24f to 0.46f),
    MeasurementSite.THIGH to (0.42f to 0.62f),
    MeasurementSite.CALF to (0.42f to 0.82f),
)
private const val ANCHOR_DOT_DIVISOR = 2.75f

private fun DrawScope.drawHatches(
    view: GeometryView,
    model: BodyMapModel,
    color: Color,
    spacingUnits: Float,
    strokeUnits: Float,
) {
    if (spacingUnits <= 0f) return
    view.parts.filter { it.muscle != null }.forEach { part ->
        val shade = model.shade(part.muscle!!)
        if (shade !is MuscleShade.Recovery || shade.step != RecoveryStep.RECOVERING) return@forEach
        part.paths.forEach { path ->
            clipPath(path.asComposePath()) {
                val top = part.bounds.top
                val bottom = part.bounds.bottom
                val height = part.bounds.height()
                var x = part.bounds.left - height
                while (x < part.bounds.right) {
                    drawLine(
                        color = color,
                        start = Offset(x, top),
                        end = Offset(x + height, bottom),
                        strokeWidth = strokeUnits,
                    )
                    x += spacingUnits
                }
            }
        }
    }
}
