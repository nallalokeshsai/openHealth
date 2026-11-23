@file:Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")

package app.openhealth.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.ui.icon.OhIcon
import app.openhealth.ui.icon.OhIconName
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.semiboldWeight

/**
 * DESIGN.md §5.14. `surface-2`, 10 dp radius, 46 dp, 16 dp side padding, a 2.5 dp accent-ink focus
 * ring with a 2 dp offset, and no border or glow otherwise. An error appears below the field only
 * after a failed submit.
 */
@Composable
fun OhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ringPx = with(density) { geometry.fieldFocusRing.toPx() }
    val offsetPx = with(density) { geometry.fieldFocusOffset.toPx() }
    val focusRadiusPx = with(density) { geometry.fieldFocusRadius.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(geometry.fieldHeight)
                .then(
                    if (focused) {
                        Modifier.drawBehind {
                            drawRoundRect(
                                color = colors.accInk,
                                topLeft = Offset(-offsetPx, -offsetPx),
                                size = Size(size.width + offsetPx * 2, size.height + offsetPx * 2),
                                cornerRadius = CornerRadius(focusRadiusPx),
                                style = Stroke(width = ringPx)
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(OhTheme.radius.md))
                .background(colors.surface2)
                .padding(horizontal = geometry.fieldPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                textStyle = type.body.copy(color = if (enabled) colors.label else colors.label3),
                cursorBrush = SolidColor(colors.accInk),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                singleLine = singleLine,
                interactionSource = interaction,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = type.body,
                                color = colors.label3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            )
            if (trailing != null) {
                Spacer(Modifier.width(OhTheme.space.space3))
                trailing()
            }
        }
        if (isError && errorMessage != null) {
            Row(
                modifier = Modifier
                    .padding(top = OhTheme.space.space1)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                OhIcon(
                    name = OhIconName.Warning,
                    contentDescription = null,
                    size = geometry.iconInText,
                    tint = colors.redInk
                )
                Spacer(Modifier.width(OhTheme.space.space0))
                Text(errorMessage, style = type.footnote, color = colors.redInk)
            }
        }
    }
}

/** The search variant of §5.14: an 18 dp magnifier, a 22 dp clear, an optional count badge. */
@Composable
fun OhSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    resultCount: Int? = null,
    onClear: () -> Unit = { onValueChange("") }
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    OhTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        keyboardOptions = KeyboardOptions.Default,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (resultCount != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(OhTheme.radius.sm))
                            .background(colors.surface3)
                            .padding(horizontal = OhTheme.space.space1, vertical = OhTheme.space.space0)
                    ) {
                        Text(resultCount.toString(), style = type.caption, color = colors.label2)
                    }
                    Spacer(Modifier.width(OhTheme.space.space3))
                }
                if (value.isNotEmpty()) {
                    OhIconButton(
                        icon = OhIconName.Close,
                        contentDescription = "Clear search",
                        onClick = onClear,
                        tint = colors.label2
                    )
                }
            }
        }
    )
}

/** A labelled field group for the gallery and for settings forms: label above, field under. */
@Composable
fun OhLabelledField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = type.subhead.semiboldWeight(), color = colors.label2)
        Spacer(Modifier.height(OhTheme.space.space2))
        content()
    }
}
