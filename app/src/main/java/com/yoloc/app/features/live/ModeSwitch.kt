package com.yoloc.app.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.a11yAction
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

enum class CameraMode { Photo, Live }

/**
 * RN: features/live/ModeSwitch.tsx. Photo | Live switch on the camera screens (dark in both
 * themes). The chosen mode is a filled white segment. Switching replaces the screen, so Android
 * back from either still returns to where the camera was opened.
 *
 * @param onSwitch Called with the other mode only (choosing the current one does nothing).
 */
@Composable
fun ModeSwitch(mode: CameraMode, onSwitch: (CameraMode) -> Unit) {
    val cam = YoloCTheme.colors.camera
    val groupLabel = stringResource(R.string.live_mode_label)
    val shape = RoundedCornerShape(Radii.sm)
    Row(
        Modifier
            .semantics { contentDescription = groupLabel }
            .selectableGroup()
            .background(cam.bar, shape)
            .border(1.dp, cam.frameBorder, shape)
            .padding(2.dp),
    ) {
        for ((value, label) in listOf(CameraMode.Photo to R.string.live_photo_mode, CameraMode.Live to R.string.live_live_mode)) {
            val selected = value == mode
            val text = stringResource(label)
            val choose = { if (!selected) onSwitch(value) }
            Box(
                Modifier
                    .defaultMinSize(minWidth = 72.dp, minHeight = 40.dp)
                    .background(if (selected) cam.control else Color.Transparent, RoundedCornerShape(Radii.sm - 1.dp))
                    .a11yAction(text, choose, role = Role.RadioButton, selected = selected)
                    .selectable(selected, role = Role.RadioButton, interactionSource = null, indication = null, onClick = choose)
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                AppText(text, variant = TypeVariant.Label, color = if (selected) cam.screen else cam.lensText)
            }
        }
    }
}
