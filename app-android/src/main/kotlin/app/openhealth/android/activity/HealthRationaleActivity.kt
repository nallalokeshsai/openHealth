package app.openhealth.android.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhTheme

/**
 * The privacy rationale the platform links to from the Health Connect permission screen
 * (`Tech-Stack.md` §8). It must say the same thing as the store listing: what is read,
 * where it goes and what never leaves the phone. It is a separate activity because the
 * platform opens it by intent, not a navigation destination.
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OhTheme(dark = isSystemInDarkThemeCompat()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OhTheme.colors.bg)
                        .windowInsetsPadding(
                            androidx.compose.foundation.layout.WindowInsets.statusBars,
                        )
                        .windowInsetsPadding(
                            androidx.compose.foundation.layout.WindowInsets.navigationBars,
                        )
                        .padding(horizontal = OhTheme.space.s6),
                ) {
                    OhPageHeader(
                        title = stringResource(R.string.health_rationale_title),
                        onBack = { finish() },
                    )
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = stringResource(R.string.health_rationale_body),
                            style = OhTheme.type.body,
                            color = OhTheme.colors.label,
                        )
                        Spacer(Modifier.height(OhTheme.space.s4))
                        Text(
                            text = stringResource(R.string.health_rationale_list),
                            style = OhTheme.type.body,
                            color = OhTheme.colors.label2,
                        )
                        Spacer(Modifier.height(OhTheme.space.s4))
                        Text(
                            text = stringResource(R.string.health_rationale_never),
                            style = OhTheme.type.body,
                            color = OhTheme.colors.label2,
                        )
                        Spacer(Modifier.height(OhTheme.space.s6))
                        OhButton(
                            text = stringResource(R.string.health_rationale_close),
                            onClick = { finish() },
                            variant = OhButtonVariant.Secondary,
                        )
                        Spacer(Modifier.height(OhTheme.space.s8))
                    }
                }
            }
        }
    }

    private fun isSystemInDarkThemeCompat(): Boolean {
        val mode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
