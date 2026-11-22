# openHealth — Compose Implementation Guide

> **Patterns only, not values (2026-11-22).** Every colour, size, radius, type role and structure in this file is superseded by `DESIGN.md` Appendix A and B and `Tech-Stack.md` §13. Do not copy a literal from here: hex literals below are six digits (alpha zero in Compose), spacing and radii are wrong, it uses Hilt and `MaterialTheme`, and it shows six tabs. Use it only to see what idiomatic Compose looks like; `AGENTS.md` lists every known error.

**Version:** 0.1  
**Date:** 2026-11-21  
**Reference:** DESIGN.md, WIREFRAMES.md, Tech-Stack.md §11

This guide maps openHealth's design tokens and components to Kotlin + Jetpack Compose code patterns, ensuring 1:1 alignment between DESIGN.md numbers and shipped code.

---

## Core Principle: Frozen Token Set

**All colour, size, and shape values must come from a single theme object.** No hardcoded hex in composables. No arbitrary `12.dp` or `Modifier.padding(16.dp)` without checking the token scale first.

The theme object is initialized once at app startup and passed through CompositionLocal (Material 3 convention). Every screen reads from it, never defines its own colors.

---

## Token File Structure

```kotlin
// app/src/main/kotlin/com/openhealth/design/tokens.kt

object OpenHealthTokens {
    // Colors (dark theme canonical)
    object Dark {
        // Surface ramp
        val bg = Color(0x000000)
        val bgEl = Color(0x0E0E10)
        val surface = Color(0x1C1C1E)
        val surface2 = Color(0x2C2C2E)
        val surface3 = Color(0x3A3A3C)
        
        // Labels (opacity-based)
        val label = Color(0xFFFFFF)
        val label2 = Color.White.copy(alpha = 0.60f)
        val label3 = Color.White.copy(alpha = 0.32f)
        val label4 = Color.White.copy(alpha = 0.18f)
        val labelDim = Color.White.copy(alpha = 0.55f)
        
        // Separators
        val sep = Color(0x545458).copy(alpha = 0.60f)
        val sepOp = Color(0x545458).copy(alpha = 0.34f)
        
        // Accent (lime default)
        val acc = Color(0xA3E635)
        val acc2 = Color(0x84CC16)
        val onAcc = Color(0x0C0E12)
        val accInk = Color(0xA3E635)
        val accSoft = Color(0xA3E635).copy(alpha = 0.16f)
        val accLine = Color(0xA3E635).copy(alpha = 0.38f)
        
        // Status
        val gold = Color(0xFFB84D)
        val goldSoft = Color(0xFFB84D).copy(alpha = 0.16f)
        val goldInk = Color(0xFFB84D)
        val red = Color(0xFF453A)
        val green = Color(0x34C759)
    }
    
    object Light {
        // (Complete sibling; every Dark value has a Light counterpart)
        val bg = Color(0xFFFFFF)
        val bgEl = Color(0xF5F5F7)
        val surface = Color(0xFFFFFF)
        val surface2 = Color(0xF5F5F7)
        val surface3 = Color(0xEBEBF0)
        
        val label = Color(0x000000)
        val label2 = Color.Black.copy(alpha = 0.60f)
        val label3 = Color.Black.copy(alpha = 0.45f)
        // ... (rest omitted for brevity)
    }
    
    // Typography (11-step scale, 13 sp baseline)
    object Typography {
        // Format: sizeSp / weight
        val caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
        val captionBold = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600)
        
        val label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
        val label2 = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600)
        
        val footnote = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
        val footnoteBold = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W600)
        
        val body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal)
        val bodyBold = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W600)
        
        val headline = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W600)
        val headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.W600)
        val headlineXLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.W700)
    }
    
    // Spacing (9 values: 4, 8, 12, 16, 20, 24, 28, 32, 40 dp)
    object Spacing {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val base = 16.dp
        val lg = 20.dp
        val xl = 24.dp
        val xxl = 28.dp
        val xxxl = 32.dp
        val huge = 40.dp
    }
    
    // Shapes (6 radii: 4, 8, 10, 12, 14, 20 dp)
    object Shapes {
        val xs = RoundedCornerShape(4.dp)
        val sm = RoundedCornerShape(8.dp)
        val md = RoundedCornerShape(10.dp)
        val base = RoundedCornerShape(12.dp)
        val lg = RoundedCornerShape(14.dp)
        val xl = RoundedCornerShape(20.dp)
    }
    
    // Motion (140–220 ms standard)
    object Motion {
        const val QUICK = 140
        const val STANDARD = 180
        const val SLOW = 220
        const val EASING = "easeOut" // cubic-bezier(0.17, 0.67, 0.83, 0.67)
    }
}

// Shorthand for current theme
object OH {
    val colors = if (isDarkTheme) OpenHealthTokens.Dark else OpenHealthTokens.Light
    val typography = OpenHealthTokens.Typography
    val spacing = OpenHealthTokens.Spacing
    val shapes = OpenHealthTokens.Shapes
    val motion = OpenHealthTokens.Motion
}
```

---

## Theme Object (Material 3 Integration)

```kotlin
// app/src/main/kotlin/com/openhealth/design/Theme.kt

private val DarkColorScheme = darkColorScheme(
    primary = OpenHealthTokens.Dark.acc,
    onPrimary = OpenHealthTokens.Dark.onAcc,
    surface = OpenHealthTokens.Dark.surface,
    onSurface = OpenHealthTokens.Dark.label,
    // ... map all Material colors to tokens
)

private val LightColorScheme = lightColorScheme(
    primary = OpenHealthTokens.Light.acc,
    onPrimary = OpenHealthTokens.Light.onAcc,
    surface = OpenHealthTokens.Light.surface,
    onSurface = OpenHealthTokens.Light.label,
    // ... map all Material colors to tokens
)

@Composable
fun OpenHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            displayLarge = OpenHealthTokens.Typography.headlineXLarge,
            displayMedium = OpenHealthTokens.Typography.headlineLarge,
            headlineSmall = OpenHealthTokens.Typography.headline,
            bodyMedium = OpenHealthTokens.Typography.body,
            labelMedium = OpenHealthTokens.Typography.label,
            // ... map all 15 Material text styles
        ),
        shapes = Shapes(
            small = OpenHealthTokens.Shapes.xs,
            medium = OpenHealthTokens.Shapes.base,
            large = OpenHealthTokens.Shapes.lg,
        ),
        content = content
    )
}
```

---

## Common Composables

### 1. Card

```kotlin
@Composable
fun OHCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = OH.spacing.sm),
        color = OH.colors.surface,
        shape = OH.shapes.base,
        border = BorderStroke(0.5.dp, OH.colors.sep.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.padding(OH.spacing.base)) {
            content()
        }
    }
}

// Usage
OHCard {
    Column(verticalArrangement = Arrangement.spacedBy(OH.spacing.base)) {
        CardHeader(icon = "🌙", title = "Readiness", value = "72%")
        // ... content
    }
}
```

### 2. Card Header

```kotlin
@Composable
fun CardHeader(
    icon: String,
    title: String,
    value: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .paddingBottom(OH.spacing.base)
            .border(
                bottom = BorderStroke(0.5.dp, OH.colors.sep.copy(alpha = 0.3f))
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OH.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 20.sp,
                modifier = Modifier
                    .size(20.dp)
                    .background(OH.colors.accSoft, RoundedCornerShape(4.dp))
            )
            Text(
                text = title,
                style = OH.typography.footnoteBold,
                color = OH.colors.label
            )
        }
        
        if (value != null) {
            Text(
                text = value,
                style = OH.typography.footnoteBold,
                color = OH.colors.label
            )
        }
    }
}
```

### 3. Row Item

```kotlin
@Composable
fun OHRowItem(
    label: String,
    value: String,
    valueColor: Color = OH.colors.label,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onClick
                ) else Modifier
            )
            .padding(vertical = OH.spacing.base)
            .border(bottom = BorderStroke(0.5.dp, OH.colors.sep.copy(alpha = 0.3f))),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = OH.typography.label,
            color = OH.colors.label2
        )
        Text(
            text = value,
            style = OH.typography.footnote,
            fontWeight = FontWeight.W600,
            color = valueColor
        )
    }
}

// Usage
OHRowItem(
    label = "Weight",
    value = "75.2 kg",
    valueColor = OH.colors.label,
    onClick = { /* open detail */ }
)
```

### 4. Button (Primary)

```kotlin
@Composable
fun OHPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        onClick = onClick,
        enabled = enabled,
        colors = ButtonColors(
            containerColor = OH.colors.acc,
            contentColor = OH.colors.onAcc,
            disabledContainerColor = OH.colors.surface2,
            disabledContentColor = OH.colors.label3
        ),
        shape = OH.shapes.base,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(
            text = text,
            style = OH.typography.footnote,
            fontWeight = FontWeight.W600
        )
    }
}
```

### 5. Progress Ring (Calories, Steps, etc.)

```kotlin
@Composable
fun OHProgressRing(
    current: Int,
    total: Int,
    unit: String = "",
    size: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val progress = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            // Background circle
            drawCircle(
                color = OH.colors.surface2,
                radius = size.toPx() / 2 - 8.dp.toPx(),
                style = Stroke(width = 8.dp.toPx())
            )
            
            // Progress arc
            drawArc(
                color = OH.colors.acc,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // Center text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = current.toString(),
                style = OH.typography.bodyBold,
                color = OH.colors.label,
                fontSize = 20.sp
            )
            Text(
                text = "/ $total $unit",
                style = OH.typography.label,
                color = OH.colors.label2,
                fontSize = 11.sp
            )
        }
    }
}

// Usage
OHProgressRing(current = 1240, total = 2200, unit = "kcal", size = 120.dp)
```

### 6. Chip

```kotlin
@Composable
fun OHChip(
    text: String,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) OH.colors.acc else OH.colors.surface2
    val textColor = if (isSelected) OH.colors.onAcc else OH.colors.label
    
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .border(
                width = 0.5.dp,
                color = if (!isSelected) OH.colors.sep.copy(alpha = 0.3f) else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = OH.spacing.base, vertical = OH.spacing.sm)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onClick
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = OH.typography.caption,
            color = textColor
        )
    }
}
```

### 7. Week Strip (7-day bubbles)

```kotlin
@Composable
fun WeekStrip(
    days: List<DayData>,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OH.spacing.sm),
        contentPadding = PaddingValues(horizontal = OH.spacing.base)
    ) {
        items(days.size) { index ->
            val day = days[index]
            val isToday = index == selectedDay
            
            Column(
                modifier = Modifier
                    .size(width = 32.dp, height = 40.dp)
                    .background(
                        if (isToday) OH.colors.acc else OH.colors.surface2,
                        RoundedCornerShape(OH.shapes.sm)
                    )
                    .clickable { onDaySelected(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = day.dayOfWeek.substring(0, 3),
                    style = OH.typography.caption,
                    color = if (isToday) OH.colors.onAcc else OH.colors.label2,
                    fontSize = 9.sp
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    style = OH.typography.label2,
                    color = if (isToday) OH.colors.onAcc else OH.colors.label,
                    fontSize = 12.sp
                )
            }
        }
    }
}
```

### 8. Bottom Navigation (6 tabs)

```kotlin
@Composable
fun OHBottomNavigation(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.height(60.dp),
        containerColor = OH.colors.bgEl,
        contentColor = OH.colors.label
    ) {
        navigationBarItems.forEachIndexed { index, (icon, label) ->
            NavigationBarItem(
                icon = { Text(icon, fontSize = 24.sp) },
                label = { Text(label, style = OH.typography.caption) },
                selected = currentTab == index,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemColors(
                    selectedIconColor = OH.colors.acc,
                    selectedTextColor = OH.colors.acc,
                    unselectedIconColor = OH.colors.label3,
                    unselectedTextColor = OH.colors.label3,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
```

### 9. Bottom Sheet

```kotlin
@Composable
fun OHBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = OH.colors.surface,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            shape = RoundedCornerShape(topStart = OH.shapes.lg, topEnd = OH.shapes.lg),
            sheetMaxWidth = Dp.Unspecified // Full-width on mobile
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OH.spacing.base),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = OH.typography.headline,
                        color = OH.colors.label
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 20.sp, color = OH.colors.label)
                    }
                }
                
                // Content
                Box(modifier = Modifier.padding(OH.spacing.base)) {
                    content()
                }
            }
        }
    }
}
```

---

## Screen Template

Every root screen follows this pattern:

```kotlin
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OH.colors.bg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp), // Tab bar height
            contentPadding = PaddingValues(horizontal = OH.spacing.base)
        ) {
            item { PageHeader("Good morning", DateTime.now().format()) }
            
            if (state.connectionError != null) {
                item { ConnectionBanner(state.connectionError!!) }
            }
            
            item { ReadinessCard(state.readiness) }
            item { WeekStripCard(state.week) }
            item { NutritionCard(state.nutrition) }
            item { StepsCard(state.steps) }
            item { WeightCard(state.weight) }
            
            if (state.dueItems.isNotEmpty()) {
                item { DueItemsCard(state.dueItems) }
            }
            
            item { QuickActionsCard() }
            
            item { Spacer(modifier = Modifier.height(OH.spacing.huge)) }
        }
        
        OHBottomNavigation(
            currentTab = 0,
            onTabSelected = { /* navigate */ },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// Individual card composables
@Composable
private fun ReadinessCard(readiness: Readiness) {
    OHCard {
        CardHeader(icon = "🌙", title = "Readiness", value = "${readiness.score}%")
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = OH.spacing.base),
            horizontalArrangement = Arrangement.spacedBy(OH.spacing.sm)
        ) {
            OHChip("Sleep: ${readiness.sleepDuration}")
            OHChip("HRV: ${readiness.hrv}")
            OHChip("RHR: ${readiness.restingHR}")
        }
        
        Text(
            text = readiness.explanation,
            style = OH.typography.label,
            color = OH.colors.label2
        )
    }
}
```

---

## Motion & Animations

```kotlin
// Composable animation helpers
@Composable
fun animateColorAsState(
    targetValue: Color,
    animationSpec: AnimationSpec<Color> = tween(
        durationMillis = OH.motion.STANDARD,
        easing = EaseOut
    )
): State<Color> = androidx.compose.animation.animateColorAsState(
    targetValue = targetValue,
    animationSpec = animationSpec
)

// Button press: scale + opacity (140 ms)
@Composable
fun OHInteraction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = OH.motion.QUICK)
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = OH.motion.QUICK)
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            }
    ) {
        content()
    }
}
```

---

## Accessibility

```kotlin
// Every row is a single accessibility node
@Composable
fun AccessibleRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: $value"
                onClick { onClick(); true }
            }
    ) {
        Text(text = label)
        Text(text = value)
    }
}

// Every provenance number is tappable
Text(
    text = "75.2 kg",
    modifier = Modifier.semantics {
        onClick {
            /* open provenance sheet */
            true
        }
    }
)

// Content descriptions for all icons
Text(
    text = "🌙",
    modifier = Modifier.semantics {
        contentDescription = "Moon icon, Readiness metric"
    }
)
```

---

## Testing Checklist

Before shipping each screen:

- [ ] Light theme: all text ≥ 4.5:1 contrast (measured against light bg)
- [ ] Dark theme: all text ≥ 4.5:1 contrast (measured against dark bg)
- [ ] 1.3× font scale: layouts reflow, no overflow, touch targets ≥ 44 dp
- [ ] Reduced motion: animations disabled, instant state changes
- [ ] High contrast: colours remain distinct without relying on hue alone
- [ ] Colour-blind (deuteranopia): no information lost (use patterns + icons)
- [ ] RTL: no hardcoded `start`/`end` or `left`/`right`
- [ ] Empty state: shown when list is empty, action button visible
- [ ] Undo toast: appears and expires after 3–5 s, tap to undo within window
- [ ] Every number: tap opens provenance sheet
- [ ] Every destructive action: confirmation + undo

---

## Token Updates Workflow

If a screen needs a size or colour not in the frozen set:

1. **Don't hardcode it in the screen file.**
2. File an issue: "Add `spacing.extraLarge = 48.dp` to tokens (needed for X screen)".
3. Update `tokens.kt` and `DESIGN.md` simultaneously.
4. Merge the token change first; then update the screen.
5. Never ship hardcoded values.

---

## File Organization

```
app/src/main/kotlin/
├── com/openhealth/
│   ├── design/
│   │   ├── tokens.kt              # Frozen token set (colours, sizes, shapes, motion)
│   │   ├── theme.kt               # Material 3 theme + CompositionLocal
│   │   └── components/
│   │       ├── Button.kt          # OHPrimaryButton, OHSecondaryButton
│   │       ├── Card.kt            # OHCard, CardHeader
│   │       ├── Row.kt             # OHRowItem
│   │       ├── Ring.kt            # OHProgressRing (calories, steps)
│   │       ├── Chip.kt            # OHChip
│   │       ├── WeekStrip.kt       # 7-day bubbles
│   │       ├── BottomNav.kt       # 6-tab navigation
│   │       ├── BottomSheet.kt     # OHBottomSheet
│   │       └── Banner.kt          # ConnectionBanner, InfoBanner
│   ├── feature/
│   │   ├── today/
│   │   │   ├── TodayScreen.kt     # Root screen
│   │   │   ├── TodayViewModel.kt
│   │   │   └── components/        # Today-specific cards
│   │   ├── train/
│   │   ├── food/
│   │   ├── body/
│   │   ├── insights/
│   │   └── more/
│   └── sheets/
│       ├── ReadinessSheet.kt      # Detail sheets (provenance, quick-add, etc.)
│       ├── ProvenanceSheet.kt
│       └── ...
```

---

## Next Steps for Developers

1. Create `tokens.kt` with frozen values (copy from DESIGN.md §12)
2. Create `theme.kt` and integrate Material 3
3. Build 6 reusable component files (Button, Card, Row, Ring, Chip, WeekStrip)
4. Implement 6 root screens in order (Today, Train, Food, Body, Insights, More)
5. Implement bottom navigation + sheet system
6. Test all screens in both themes + accessibility modes
7. Run review gate (WIREFRAMES.md §end) before each screen ships

