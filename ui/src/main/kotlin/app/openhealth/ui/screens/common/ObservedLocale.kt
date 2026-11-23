package app.openhealth.ui.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The current locale as Compose observes it. Reading `Locale.getDefault()` inside a
 * composable does not recompose when the language changes; `LocalConfiguration` does.
 */
@Composable
fun observedLocale(): Locale = LocalConfiguration.current.locales[0]
