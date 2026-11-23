package app.openhealth.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/**
 * The app-lock surface (`pr.md` §6.23). It shows nothing about the person or their data
 * until the platform authenticates them; a failure states a plain reason and offers the
 * button again.
 */
@Composable
fun LockScreen(message: LockMessage?, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(OhTheme.dims.touchTarget * 2)
                .clip(CircleShape)
                .background(OhTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            OhIcon(
                icon = OhIcons.lock,
                contentDescription = null,
                size = OhTheme.dims.iconButton,
                tint = OhTheme.colors.accInk,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s7))
        Text(
            text = stringResource(R.string.lock_title),
            style = OhTheme.type.title3.copy(fontWeight = OhWeight.semibold),
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = stringResource(R.string.lock_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        if (message != null && message != LockMessage.NONE) {
            Spacer(Modifier.height(OhTheme.space.s5))
            Text(
                text = stringResource(
                    when (message) {
                        LockMessage.NO_ENROLMENT -> R.string.lock_no_enrolment
                        LockMessage.LOCKED_OUT -> R.string.lock_locked_out
                        LockMessage.CANCELLED -> R.string.lock_cancelled
                        LockMessage.NONE -> R.string.lock_body
                    },
                ),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.orangeInk,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s8))
        OhButton(
            text = stringResource(R.string.lock_unlock),
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
