package com.yoloc.app.screens.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.yoloc.app.screens.appContainer
import com.yoloc.app.store.USER_NAME_MAX
import com.yoloc.app.features.settings.RowSwitch
import com.yoloc.app.components.cardShadow
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.YTextField
import com.yoloc.app.components.BottomSheet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.BuildConfig
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.ConfirmDialog
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.HeaderedSection
import com.yoloc.app.components.Toast
import com.yoloc.app.components.ToastSlot
import com.yoloc.app.components.ToastTone
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.components.YSlider
import com.yoloc.app.features.export.RetrainingExportSheet
import com.yoloc.app.features.settings.LOCATION_PERMISSIONS
import com.yoloc.app.features.settings.LocationAccess
import com.yoloc.app.features.settings.SettingsGroup
import com.yoloc.app.features.settings.SettingsRow
import com.yoloc.app.features.settings.THRESHOLD_MAX
import com.yoloc.app.features.settings.THRESHOLD_MIN
import com.yoloc.app.features.settings.THRESHOLD_STEP
import com.yoloc.app.features.settings.formatBytes
import com.yoloc.app.features.settings.hasLocationPermission
import com.yoloc.app.features.settings.thresholdPercent
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: app/(tabs)/settings.tsx (CLAUDE.md 3.2). Detection threshold, theme, opt-in location, and
 * the user's data (plots, storage, CSV export, retraining export, clear all). Every change is
 * saved at once; nothing needs a Save button.
 */
@Composable
fun SettingsScreen(
    onOpenPlots: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSeed: () -> Unit,
    onOpenInference: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val rationale = {
        activity != null && LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }
    // RN: storage recounts on focus; location permission is re-checked when the app returns.
    LifecycleResumeEffect(vm) {
        vm.onResume()
        vm.onLocationChecked(hasLocationPermission(context), rationale())
        onPauseOrDispose {}
    }
    var editingName by rememberSaveable { mutableStateOf(false) }
    // One prompt at a time: a second launch is answered at once with nothing granted.
    var asking by remember { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
        asking = false
        vm.onLocationResult(result.values.any { it }, rationale())
    }
    val turnOnGps = {
        if (!asking && vm.toggleGps(true)) {
            asking = true
            askLocation.launch(LOCATION_PERMISSIONS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        ScreenContainer(contentPadding = PaddingValues(start = Spacing.screen, top = Spacing.screen, end = Spacing.screen, bottom = 96.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                AppText(stringResource(R.string.settings_title), variant = TypeVariant.Headline)
                AppText(stringResource(R.string.settings_subtitle), tone = Tone.Muted)
            }
            // Redesign 2026-10-28 (the prototype): the name, one Dark mode switch and the model.
            HeaderedSection(stringResource(R.string.settings_profile_title)) {
                val dark = YoloCTheme.colors.isDark
                SettingsGroup {
                    row {
                        SettingsRow(
                            IconName.User,
                            stringResource(R.string.settings_name),
                            detail = s.userName.ifEmpty { stringResource(R.string.settings_name_none) },
                            onClick = { editingName = true },
                        )
                    }
                    row {
                        SettingsRow(
                            if (dark) IconName.Moon else IconName.Sun,
                            stringResource(R.string.settings_dark_mode),
                            detail = stringResource(R.string.settings_dark_mode_detail),
                            onClick = { vm.setDark(!dark) },
                            checked = dark,
                            trailing = { RowSwitch(dark) },
                        )
                    }
                    row {
                        SettingsRow(
                            IconName.Cpu,
                            stringResource(R.string.settings_model),
                            detail = stringResource(R.string.settings_model_detail, appContainer().modelConfig.displayName),
                            onClick = onOpenAbout,
                        )
                    }
                }
            }
            HeaderedSection(stringResource(R.string.settings_detection_title)) { Threshold(s, vm) }
            HeaderedSection(stringResource(R.string.settings_location_title)) {
                Location(s, onToggle = { on -> if (on) turnOnGps() else vm.toggleGps(false) }, onTryAgain = turnOnGps)
            }
            HeaderedSection(stringResource(R.string.settings_data_title)) { Data(s, vm, onOpenPlots) }
            HeaderedSection(stringResource(R.string.settings_app_title)) {
                SettingsGroup {
                    row {
                        SettingsRow(
                            IconName.Info,
                            stringResource(R.string.settings_about),
                            detail = stringResource(R.string.settings_about_detail),
                            onClick = onOpenAbout,
                        )
                    }
                    row { SettingsRow(IconName.RotateCcw, stringResource(R.string.settings_show_onboarding), onClick = vm::showOnboardingAgain) }
                    if (BuildConfig.DEBUG) {
                        row { SettingsRow(IconName.Grid, stringResource(R.string.settings_dev_gallery), onClick = onOpenGallery) }
                        row { SettingsRow(IconName.Database, stringResource(R.string.settings_dev_seed), onClick = onOpenSeed) }
                        row { SettingsRow(IconName.Cpu, stringResource(R.string.settings_dev_inference), onClick = onOpenInference) }
                    }
                }
            }
        }

        ToastSlot(s.local.toast, Modifier.align(Alignment.BottomCenter).padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.md)) {
            ToastMessage(it)
        }
    }

    RetrainingExportSheet(
        visible = s.local.retrainOpen,
        onClose = vm::closeRetrain,
        onDone = vm::onRetrainDone,
        onError = {
            // RN: the toast lives under the sheet; close the sheet so the message is seen.
            vm.closeRetrain()
            vm.onRetrainError()
        },
    )

    if (editingName) NameSheet(s.userName, onSave = { vm.setUserName(it); editingName = false }, onClose = { editingName = false })

    if (s.local.confirmingClear) {
        val summary = s.local.summary
        val res = LocalResources.current
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_title),
            body = if (summary != null) {
                stringResource(
                    R.string.settings_clear_body,
                    res.getQuantityString(R.plurals.settings_scan_count, summary.scans, summary.scans),
                    res.getQuantityString(R.plurals.settings_plot_count, summary.plots, summary.plots),
                )
            } else {
                stringResource(R.string.settings_clear_body_unknown)
            },
            confirmLabel = stringResource(R.string.settings_clear_confirm),
            onConfirm = vm::confirmClear,
            cancelLabel = stringResource(R.string.settings_cancel),
            onCancel = vm::cancelClear,
        )
    }
}

@Composable
private fun Threshold(s: SettingsViewModel.State, vm: SettingsViewModel) {
    val colors = YoloCTheme.colors
    val label = stringResource(R.string.settings_threshold)
    val res = LocalResources.current
    val shape = RoundedCornerShape(Radii.md)
    Column(
        Modifier
            .fillMaxWidth()
            .cardShadow(shape)
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            AppText(label, Modifier.weight(1f), variant = TypeVariant.BodyStrong)
            // The slider speaks the value itself.
            AppText(
                stringResource(R.string.settings_threshold_value, thresholdPercent(s.shownThreshold)),
                Modifier.clearAndSetSemantics {},
                variant = TypeVariant.Headline,
            )
        }
        YSlider(
            value = s.confThreshold,
            min = THRESHOLD_MIN,
            max = THRESHOLD_MAX,
            step = THRESHOLD_STEP,
            onChangeEnd = vm::onThresholdChangeEnd,
            formatValue = { thresholdPercent(it).let { pct -> res.getQuantityString(R.plurals.settings_threshold_a11y, pct, pct) } },
            accessibilityLabel = label,
            onChange = vm::onThresholdChange,
            marker = s.defaultThreshold,
        )
        Row(Modifier.fillMaxWidth().offset(y = -Spacing.xxs), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText(stringResource(R.string.settings_threshold_more), variant = TypeVariant.Caption, tone = Tone.Muted)
            AppText(stringResource(R.string.settings_threshold_fewer), variant = TypeVariant.Caption, tone = Tone.Muted)
        }
        AppText(stringResource(R.string.settings_threshold_help))
        AppText(stringResource(R.string.settings_threshold_applies), variant = TypeVariant.Caption, tone = Tone.Muted)
        if (!s.isDefaultThreshold) {
            YButton(
                stringResource(R.string.settings_threshold_reset, thresholdPercent(s.defaultThreshold)),
                vm::resetThreshold,
                variant = ButtonVariant.Ghost,
                icon = IconName.RotateCcw,
            )
        }
    }
}

/**
 * RN: LocationSetting. Opt-in location (CLAUDE.md 2.2). Turning it on asks for permission; a
 * refusal keeps it off and says why, with Try again or, once Android stops asking, a way to the
 * phone's settings.
 */
@Composable
private fun Location(s: SettingsViewModel.State, onToggle: (Boolean) -> Unit, onTryAgain: () -> Unit) {
    val colors = YoloCTheme.colors
    val context = LocalContext.current
    val message = when (s.locationMessage) {
        SettingsViewModel.LocationMessage.On -> R.string.settings_gps_on
        SettingsViewModel.LocationMessage.Off -> R.string.settings_gps_off
        SettingsViewModel.LocationMessage.Ask -> R.string.settings_gps_ask
        SettingsViewModel.LocationMessage.Blocked -> R.string.settings_gps_blocked
        SettingsViewModel.LocationMessage.Revoked -> R.string.settings_gps_revoked
    }
    // One row: the action belongs to it, so no divider between them.
    SettingsGroup {
        row {
            Column {
                SettingsRow(
                    IconName.MapPin,
                    stringResource(R.string.settings_gps),
                    detail = stringResource(message),
                    onClick = { onToggle(!s.gpsOptIn) },
                    checked = s.gpsOptIn,
                    trailing = { RowSwitch(s.gpsOptIn) },
                )
                if (s.locationProblem) {
                    Box(Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)) {
                        if (s.local.locationAccess == LocationAccess.Blocked) {
                            YButton(
                                stringResource(R.string.settings_open_settings),
                                {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                    )
                                },
                                variant = ButtonVariant.Secondary,
                                icon = IconName.Settings,
                            )
                        } else {
                            YButton(stringResource(R.string.settings_try_again), onTryAgain, variant = ButtonVariant.Secondary, icon = IconName.RefreshCw)
                        }
                    }
                }
            }
        }
    }
}

/** RN: DataSettings. Plots, storage, export and clear-all; the numbers reload when saved scans change. */
@Composable
private fun Data(s: SettingsViewModel.State, vm: SettingsViewModel, onOpenPlots: () -> Unit) {
    val summary = s.local.summary
    SettingsGroup {
        row {
            SettingsRow(
                IconName.Map,
                stringResource(R.string.settings_plots),
                detail = summary?.let { pluralStringResource(R.plurals.settings_plot_count, it.plots, it.plots) },
                onClick = onOpenPlots,
            )
        }
        row {
            SettingsRow(
                IconName.HardDrive,
                stringResource(R.string.settings_storage),
                detail = when {
                    summary != null -> stringResource(
                        R.string.settings_storage_detail,
                        pluralStringResource(R.plurals.settings_scan_count, summary.scans, summary.scans),
                        formatBytes(summary.bytes),
                    )
                    s.local.summaryFailed -> stringResource(R.string.settings_storage_unknown)
                    else -> " "
                },
            )
        }
        row {
            SettingsRow(
                IconName.Share2,
                stringResource(R.string.settings_export),
                detail = stringResource(R.string.settings_export_detail),
                onClick = vm::exportCsv,
                enabled = !s.local.exporting,
            )
        }
        row {
            SettingsRow(
                IconName.Package,
                stringResource(R.string.settings_retrain),
                detail = stringResource(R.string.settings_retrain_detail),
                onClick = vm::openRetrain,
            )
        }
        row {
            SettingsRow(
                IconName.Trash2,
                stringResource(R.string.settings_clear),
                onClick = vm::askClear,
                destructive = true,
                enabled = !s.local.clearing,
                chevron = false,
            )
        }
    }
}

@Composable
private fun ToastMessage(toast: SettingsViewModel.Toast) {
    val res = LocalResources.current
    val (message, tone) = when (toast) {
        SettingsViewModel.Toast.ExportEmpty -> stringResource(R.string.settings_export_empty) to ToastTone.Info
        SettingsViewModel.Toast.ExportFailed -> stringResource(R.string.settings_export_failed) to ToastTone.Error
        SettingsViewModel.Toast.ClearNothing -> stringResource(R.string.settings_clear_nothing) to ToastTone.Info
        SettingsViewModel.Toast.Cleared -> stringResource(R.string.settings_cleared) to ToastTone.Success
        SettingsViewModel.Toast.ClearFailed -> stringResource(R.string.settings_clear_failed) to ToastTone.Error
        SettingsViewModel.Toast.RetrainFailed -> stringResource(R.string.settings_retrain_failed) to ToastTone.Error
        is SettingsViewModel.Toast.RetrainDone -> {
            val done = res.getQuantityString(R.plurals.settings_retrain_done, toast.scans, toast.scans)
            val text = if (toast.skipped > 0) {
                "$done. ${res.getQuantityString(R.plurals.settings_retrain_skipped, toast.skipped, toast.skipped)}"
            } else {
                done
            }
            text to ToastTone.Success
        }
    }
    Toast(message, tone = tone)
}

/** Redesign 2026-10-28 (Kotlin only): change or remove the name Home greets with. */
@Composable
private fun NameSheet(current: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    BottomSheet(true, stringResource(R.string.settings_name_sheet_title), onClose = onClose) {
        AppText(stringResource(R.string.settings_name_help), tone = Tone.Muted)
        YTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.onboarding_name_placeholder),
            accessibilityLabel = stringResource(R.string.settings_name),
            modifier = Modifier.focusRequester(focus),
            maxLength = USER_NAME_MAX,
            onDone = { onSave(name) },
            selectAllOnFocus = true,
        )
        YButton(stringResource(R.string.settings_name_save), { onSave(name) }, size = ButtonSize.Lg, fullWidth = true)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
