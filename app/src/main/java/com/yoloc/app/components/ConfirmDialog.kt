package com.yoloc.app.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: Alert.alert(title, body, [cancel, confirm]). A system-style dialog in the app's colors;
 * dismissing it (back, outside tap) cancels.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    cancelLabel: String,
    onCancel: () -> Unit,
) {
    val link = YoloCTheme.colors.link
    AlertDialog(
        onDismissRequest = onCancel,
        title = { AppText(title, variant = TypeVariant.Title) },
        text = { AppText(body) },
        confirmButton = { TextButton(onClick = onConfirm) { AppText(confirmLabel, variant = TypeVariant.Label, color = link) } },
        dismissButton = { TextButton(onClick = onCancel) { AppText(cancelLabel, variant = TypeVariant.Label, color = link) } },
        containerColor = YoloCTheme.colors.surfaceRaised,
    )
}
