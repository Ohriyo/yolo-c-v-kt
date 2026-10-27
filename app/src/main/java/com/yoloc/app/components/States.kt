package com.yoloc.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/** A short practical tip, e.g. photo tips on "No cacao pods found". */
data class EmptyTip(val icon: IconName, val text: String)

/** The one action an empty state offers. */
data class StateAction(val label: String, val onClick: () -> Unit, val icon: IconName? = null)

/**
 * RN: components/EmptyState.tsx. An empty screen is an invitation to act: what is missing, how to
 * fix it, and the action. Left-aligned like the rest of the app; tips are divided rows, not a
 * boxed card.
 */
@Composable
fun EmptyState(
    icon: IconName,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    tips: List<EmptyTip> = emptyList(),
    action: StateAction? = null,
) {
    val colors = YoloCTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(icon, size = 28.dp, color = colors.icon)
            AppText(title, variant = TypeVariant.Headline)
            if (body != null) AppText(body, tone = Tone.Muted)
        }
        if (tips.isNotEmpty()) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = colors.border)
                for (tip in tips) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tip.icon, size = 20.dp, color = colors.icon)
                        AppText(tip.text, Modifier.weight(1f))
                    }
                    HorizontalDivider(thickness = 1.dp, color = colors.border)
                }
            }
        }
        if (action != null) {
            YButton(action.label, action.onClick, icon = action.icon, size = ButtonSize.Lg, fullWidth = true)
        }
    }
}

/**
 * RN: components/ErrorState.tsx. [message] says what happened and what to do next: no apologies,
 * no vague wording.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.error_default_title),
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    retryLabel: String = stringResource(R.string.error_retry),
    backLabel: String = stringResource(R.string.error_back),
) {
    val colors = YoloCTheme.colors
    Column(
        // RN: accessibilityRole="alert"
        modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.padding(vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(IconName.AlertCircle, size = 28.dp, color = colors.text)
            AppText(title, variant = TypeVariant.Headline)
            AppText(message, tone = Tone.Muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (onRetry != null) {
                YButton(retryLabel, onRetry, icon = IconName.RefreshCw, size = ButtonSize.Lg, fullWidth = true)
            }
            if (onBack != null) {
                YButton(
                    backLabel,
                    onBack,
                    variant = if (onRetry != null) ButtonVariant.Ghost else ButtonVariant.Secondary,
                    fullWidth = true,
                )
            }
        }
    }
}
