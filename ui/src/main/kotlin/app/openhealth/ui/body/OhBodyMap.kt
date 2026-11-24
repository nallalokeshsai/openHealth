package app.openhealth.ui.body

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
import androidx.compose.ui.graphics.drawscope.withTransform
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
import app.openhealth.core.body.BodySide
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.MuscleRegion
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.ui.R
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
    geometry: BodyGeometry? = null,
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(geometry ?: BodyGeometry.cached()) }
    LaunchedEffect(geometry) {
        if (loaded == null) {
            loaded = withContext(Dispatchers.IO) { BodyGeometry.load(context) }
        }
    }
    val drawings = loaded
    if (drawings == null) {
        Placeholder(variant = variant, modifier = modifier)
        return
    }
    val front = drawings.view(model.sex, BodySide.FRONT)?.takeIf { BodySide.FRONT in sides }
    val back = drawings.view(model.sex, BodySide.BACK)?.takeIf { BodySide.BACK in sides }
    val description = bodyMapDescription(model)
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
                    )
                }
            }
        }
    }
}

@Composable
private fun Placeholder(variant: BodyMapVariant, modifier: Modifier) {
    val height = when (variant) {
        BodyMapVariant.FULL -> OhTheme.dims.bodyMapMax
        BodyMapVariant.MINI -> OhTheme.dims.bodyMapMiniHeight
        BodyMapVariant.EVENT_MINI -> OhTheme.dims.bodyMapEventMini
    }
    Box(modifier = modifier.fillMaxWidth().height(height))
}

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
    val fatColor = colors.fat.copy(alpha = OhAlpha.bodyFatLayer)
    val baseColor = baseFill(colors, variant)
    val fatLayer = remember(view, model.adipose) {
        model.adipose?.let { layer ->
            AdiposeGeometry.layer(
                view = view,
                thickness = MuscleRegion.entries.associateWith { region ->
                    (layer.thicknessFor(region) * vb.height).toFloat()
                },
            )
        }
    }
    val hitRegions = remember(view) {
        bodyHitRegions(view)
    }
    val labels = Muscle.entries.associateWith { muscle -> muscleLabel(muscle) }
    val states = Muscle.entries.associateWith { muscle -> muscleStateLabel(model.shade(muscle)) }

    Box(modifier = Modifier.height(height).width(width)) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(view, onMuscle) {
                detectTapGestures { offset ->
                    if (onMuscle == null) return@detectTapGestures
                    val unit = size.height / vb.height
                    val x = (offset.x / unit + vb.x).toInt()
                    val y = (offset.y / unit + vb.y).toInt()
                    val touched = hitRegions.entries
                        .lastOrNull { (_, region) -> region.contains(x, y) }
                        ?.key
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
                view.parts.forEach { part ->
                    part.paths.forEach { path ->
                        drawPath(path.asComposePath(), color = baseColor)
                    }
                }
                if (!mini) {
                    fatLayer?.let { fat -> drawPath(fat.asComposePath(), color = fatColor) }
                }
                view.parts.filter { it.muscle != null }.forEach { part ->
                    val paint = paints.getValue(part.muscle!!)
                    part.paths.forEach { path ->
                        drawPath(path.asComposePath(), color = paint.track)
                        paint.overlay?.let { overlay ->
                            drawPath(path.asComposePath(), color = overlay)
                        }
                    }
                }
                view.parts.filter { it.muscle != null }.forEach { part ->
                    part.paths.forEach { path ->
                        drawPath(
                            path.asComposePath(),
                            color = colors.surface,
                            style = Stroke(
                                width = strokeUnits,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
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
        // One accessibility node per muscle; the drawn shapes stay one pointer target.
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
                        role = Role.Button
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

private const val GLOW_BOTH_SIDES = 2

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

@Composable
private fun muscleStateLabel(shade: MuscleShade): String = when (shade) {
    is MuscleShade.Recovery -> when (shade.step) {
        RecoveryStep.UNTRAINED -> stringResource(R.string.recovery_untrained)
        RecoveryStep.RECOVERING -> stringResource(R.string.recovery_recovering)
        RecoveryStep.EASING -> stringResource(R.string.recovery_easing)
        RecoveryStep.READY -> stringResource(R.string.recovery_ready)
    }

    is MuscleShade.Plan -> stringResource(R.string.body_map_plan_sets, shade.weeklySets)
    MuscleShade.None -> stringResource(R.string.recovery_untrained)
}
