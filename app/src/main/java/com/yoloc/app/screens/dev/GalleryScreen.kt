package com.yoloc.app.screens.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.components.AppTabBar
import com.yoloc.app.components.AppText
import com.yoloc.app.components.BottomSheet
import com.yoloc.app.components.BottomSheetPanel
import com.yoloc.app.components.BoxDetection
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.Card
import com.yoloc.app.components.ChipSize
import com.yoloc.app.components.DetectionListItem
import com.yoloc.app.components.EmptyState
import com.yoloc.app.components.EmptyTip
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ImageWithBoxes
import com.yoloc.app.components.ReadoutCell
import com.yoloc.app.components.ReadoutSize
import com.yoloc.app.components.ReadoutStrip
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.SectionHeader
import com.yoloc.app.components.SegmentedControl
import com.yoloc.app.components.SegmentedOption
import com.yoloc.app.components.StateAction
import com.yoloc.app.components.StatusChip
import com.yoloc.app.components.TabBarItem
import com.yoloc.app.components.TextAction
import com.yoloc.app.components.Toast
import com.yoloc.app.components.ToastTone
import com.yoloc.app.components.Tone
import com.yoloc.app.components.VerdictLine
import com.yoloc.app.components.YButton
import com.yoloc.app.components.statusIcon
import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.theme.Palette
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme
import com.yoloc.app.theme.YoloCType
import java.io.File

/*
 * RN: features/dev/GalleryScreen.tsx. Dev only: every core component in every state, in light
 * and dark. Fixture copy below is for display only; class names and advice come from the DB in
 * the app.
 */

/** RN: assets/icon.png (debug assets only, like the route). */
private const val FIXTURE_IMAGE = "file:///android_asset/gallery/icon.png"

private val boxes = listOf(
    BoxDetection("a", StatusKey.Early, "Early stage", 0.82, 0.08, 0.32, 0.3, 0.42),
    BoxDetection("b", StatusKey.Healthy, "Healthy pod", 0.91, 0.56, 0.22, 0.34, 0.5),
    BoxDetection("c", StatusKey.Infected, "Fully infected pod", 0.37, 0.3, 0.02, 0.28, 0.2),
)

private val typeSamples = listOf(
    TypeVariant.Readout to "128",
    TypeVariant.Headline to "History",
    TypeVariant.Figure to "91%",
    TypeVariant.Verdict to "Early-stage signs in 2 of 5 pods",
    TypeVariant.Title to "Recent scans",
    TypeVariant.Data to "0.64  1.38 s",
    TypeVariant.BodyStrong to "Plot B, row 4",
    TypeVariant.Body to "Hold the phone 30–50 cm from the pod, in shade or even daylight.",
    TypeVariant.Label to "Save scan",
    TypeVariant.Caption to "Today at 9:41, Plot B",
)

private val swatches = listOf(
    "forest900" to Palette.forest900,
    "forest700" to Palette.forest700,
    "sage500" to Palette.sage500,
    "mist300" to Palette.mist300,
    "cream100" to Palette.cream100,
    "statusEarly" to Palette.statusEarly,
    "statusInfected" to Palette.statusInfected,
    "sageInk" to Palette.sageInk,
    "amberInk" to Palette.amberInk,
    "brickLight" to Palette.brickLight,
    "sageChip" to Palette.sageChip,
    "white" to Palette.white,
    "light50" to Palette.light50,
    "light600" to Palette.light600,
    "dark950" to Palette.dark950,
    "dark900" to Palette.dark900,
    "dark850" to Palette.dark850,
    "dark300" to Palette.dark300,
)

private val noop = {}

private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

/** RN: GalleryScreen with its own light / dark switch (the app's theme still owns the system bars). */
@Composable
fun GalleryScreen() {
    var theme by rememberSaveable { mutableStateOf(ThemeSetting.Light) }
    YoloCTheme(preference = theme, systemBars = false) {
        GalleryContent(theme) { theme = it }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryContent(theme: ThemeSetting, onTheme: (ThemeSetting) -> Unit) {
    val colors = YoloCTheme.colors
    var selectedBox by rememberSaveable { mutableStateOf<String?>("a") }
    var selectedRow by rememberSaveable { mutableIntStateOf(2) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableStateOf("home") }
    var segment by rememberSaveable { mutableStateOf(ThemeSetting.Light) }

    ScreenContainer(
        contentPadding = PaddingValues(start = Spacing.screen, top = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        spacing = Spacing.xl,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppText("Component gallery", variant = TypeVariant.Headline)
            AppText("Every core component and state. Switch themes to check both.", tone = Tone.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                for (t in listOf(ThemeSetting.Light, ThemeSetting.Dark)) {
                    YButton(
                        if (t == ThemeSetting.Light) "Light theme" else "Dark theme",
                        { onTheme(t) },
                        variant = if (theme == t) ButtonVariant.Primary else ButtonVariant.Secondary,
                        icon = if (t == ThemeSetting.Light) IconName.Sun else IconName.Moon,
                    )
                }
            }
        }

        Section("Typography", "Barlow for text, Barlow Semi Condensed with tabular figures for numbers") {
            for ((variant, text) in typeSamples) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppText(
                        "${variant.name.replaceFirstChar { it.lowercase() }} ${YoloCType.of(variant).fontSize.value.toInt()}",
                        Modifier.width(84.dp).alignByBaseline(),
                        variant = TypeVariant.Caption,
                        tone = Tone.Faint,
                    )
                    AppText(text, Modifier.weight(1f).alignByBaseline(), variant = variant)
                }
            }
        }

        Section("Color", "Brand accents, status inks and the neutral grounds") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for ((name, color) in swatches) {
                    Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(
                            Modifier
                                .padding(bottom = Spacing.xxs)
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(color, RoundedCornerShape(Radii.sm))
                                .border(1.dp, colors.border, RoundedCornerShape(Radii.sm)),
                        )
                        AppText(name, variant = TypeVariant.Caption)
                        AppText(hex(color), variant = TypeVariant.Caption, tone = Tone.Faint)
                    }
                }
            }
        }

        Section("Button", "Default, pressed, disabled and loading for each variant") {
            for (variant in listOf(ButtonVariant.Primary, ButtonVariant.Secondary, ButtonVariant.Ghost)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppText(variant.name.lowercase(), variant = TypeVariant.Caption, tone = Tone.Muted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        YButton("Save scan", noop, variant = variant, icon = IconName.Check)
                        YButton("Pressed", noop, variant = variant, previewPressed = true)
                        YButton("Disabled", noop, variant = variant, enabled = false)
                        YButton("Saving", noop, variant = variant, loading = true)
                    }
                }
            }
            YButton("Scan a pod", noop, icon = IconName.Camera, size = ButtonSize.Lg, fullWidth = true)
            Box(Modifier.fillMaxWidth().background(colors.camera.lens, RoundedCornerShape(Radii.md)).padding(Spacing.md)) {
                YButton("Allow camera", noop, variant = ButtonVariant.OnDark, icon = IconName.Camera)
            }
        }

        Section("Card", "A bordered panel, plain and pressed. Lists use divided rows") {
            Card {
                AppText("Plot B, row 4", variant = TypeVariant.BodyStrong)
                AppText("Last scanned yesterday", tone = Tone.Muted)
            }
            Card(onClick = noop, accessibilityLabel = "Open scan", previewPressed = true) {
                AppText("Pressed panel", variant = TypeVariant.BodyStrong)
                AppText("The fill darkens while held", tone = Tone.Muted)
            }
        }

        Section("StatusChip", "Icon + label + fill, never color alone") {
            val statuses = listOf(StatusKey.Healthy, StatusKey.Early, StatusKey.Infected)
            val labels = listOf("Healthy pod", "Early stage", "Fully infected pod")
            val confidences = listOf(0.91, 0.64, 0.88)
            ChipRow { statuses.forEach { StatusChip(it) } }
            ChipRow { statuses.forEachIndexed { i, st -> StatusChip(st, label = labels[i], confidence = confidences[i]) } }
            ChipRow { statuses.forEachIndexed { i, st -> StatusChip(st, confidence = confidences[i], size = ChipSize.Sm) } }
        }

        Section("VerdictLine", "Each verdict, no pods, and low confidence") {
            VerdictLine(Verdict.Healthy, "No signs of Frosty Pod Rot on 4 pods")
            VerdictLine(Verdict.EarlyStage, "Early-stage signs detected in 2 of 5 pods")
            VerdictLine(Verdict.Infected, "Advanced infection detected in 1 of 3 pods", detail = "Also 1 early-stage pod")
            VerdictLine(Verdict.NoPod, stringResource(R.string.verdict_no_pod_title), detail = "Try another photo")
            VerdictLine(Verdict.EarlyStage, "Early-stage signs detected in 1 of 2 pods", lowConfidence = true)
        }

        Section("ReadoutStrip", "The signature row: readings between hairlines") {
            ReadoutStrip(
                listOf(
                    ReadoutCell("pods", "Pods", "5"),
                    ReadoutCell("top", "Highest confidence", "91%"),
                    ReadoutCell("time", "Analysis", "1.4 s"),
                ),
            )
            val names = listOf(R.string.status_healthy, R.string.status_early, R.string.status_infected).map { stringResource(it) }
            ReadoutStrip(
                listOf(StatusKey.Healthy, StatusKey.Early, StatusKey.Infected).mapIndexed { i, st ->
                    ReadoutCell(
                        st.token,
                        names[i],
                        listOf(42, 7, 3)[i].toString(),
                        icon = statusIcon.getValue(st),
                        iconColor = colors.status(st).ink,
                        onClick = noop,
                    )
                },
                size = ReadoutSize.Lg,
            )
        }

        Section("DetectionListItem", "Default, selected (tap to move), and corrected") {
            listOf(
                Triple(StatusKey.Early, "Early stage", 0.82),
                Triple(StatusKey.Healthy, "Healthy pod", 0.91),
                Triple(StatusKey.Infected, "Fully infected pod", 0.37),
            ).forEachIndexed { i, (status, label, confidence) ->
                DetectionListItem(i + 1, status, label, confidence, selected = selectedRow == i + 1, onClick = { selectedRow = i + 1 })
            }
            DetectionListItem(4, StatusKey.Healthy, "Healthy pod", 0.55, correctedFrom = "Early stage")
        }

        Section("ImageWithBoxes", "Boxes with labels, a selected box, no boxes, and a broken image") {
            ImageWithBoxes(
                FIXTURE_IMAGE,
                1024,
                1024,
                boxes,
                selectedId = selectedBox,
                onSelect = { id -> selectedBox = if (selectedBox == id) null else id },
                accessibilityLabel = "Sample photo with three detections",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ImageWithBoxes(FIXTURE_IMAGE, 1024, 1024, emptyList(), Modifier.weight(1f))
                ImageWithBoxes(File("/missing/scan.jpg"), 4, 3, emptyList(), Modifier.weight(1f))
            }
        }

        Section("EmptyState") {
            EmptyState(
                IconName.Search,
                stringResource(R.string.verdict_no_pod_title),
                body = "The photo did not show a pod clearly enough to check.",
                tips = listOf(
                    EmptyTip(IconName.Maximize, "Fill most of the frame with one or two pods."),
                    EmptyTip(IconName.Sun, "Use shade or even daylight, not direct sun."),
                    EmptyTip(IconName.Crosshair, "Hold steady and tap the pod to focus."),
                ),
                action = StateAction("Retake photo", noop, IconName.Camera),
            )
            EmptyState(IconName.Clock, "No scans yet", body = "Saved scans appear here, newest first.")
        }

        Section("ErrorState") {
            ErrorState(
                "Restart the app. If it still fails, reinstall it to restore the model file.",
                title = "The detection model did not load",
                onRetry = noop,
                onBack = noop,
            )
        }

        Section("BottomSheet", "Inline preview, plus the real modal") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.md))
                    .border(1.dp, colors.border, RoundedCornerShape(Radii.md))
                    .padding(top = Spacing.xs),
            ) {
                BottomSheetPanel("Save to plot", noop) {
                    AppText("Choose a plot for this scan, or save without one.", tone = Tone.Muted)
                    YButton("Save scan", noop, icon = IconName.Check, fullWidth = true)
                }
            }
            YButton("Open bottom sheet", { sheetOpen = true }, variant = ButtonVariant.Secondary)
            BottomSheet(sheetOpen, "Save to plot", onClose = { sheetOpen = false }) {
                AppText("Choose a plot for this scan, or save without one.", tone = Tone.Muted)
                YButton("Save scan", { sheetOpen = false }, icon = IconName.Check, fullWidth = true)
                YButton("Cancel", { sheetOpen = false }, variant = ButtonVariant.Ghost, fullWidth = true)
            }
        }

        Section("Toast", "Info, success, and error with an action") {
            Toast("Analyzing on your device", tone = ToastTone.Info)
            Toast("Scan saved", tone = ToastTone.Success)
            Toast("Scan deleted", tone = ToastTone.Error, action = TextAction("Undo", noop))
        }

        Section("SectionHeader") {
            SectionHeader("Recent scans", action = TextAction("See all", noop))
            SectionHeader("Last 30 days", subtitle = "Counts by result")
        }

        Section("SegmentedControl", "Selected segment: fill and a check mark") {
            SegmentedControl(
                listOf(
                    SegmentedOption(ThemeSetting.System, "System", IconName.Smartphone),
                    SegmentedOption(ThemeSetting.Light, "Light", IconName.Sun),
                    SegmentedOption(ThemeSetting.Dark, "Dark", IconName.Moon),
                ),
                segment,
                { segment = it },
                accessibilityLabel = "Theme",
            )
        }

        Section("TabBar", "Tap to move the active tab. Scan is the filled center action.") {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).border(1.dp, colors.border, RoundedCornerShape(Radii.md))) {
                AppTabBar(
                    listOf(
                        TabBarItem("home", stringResource(R.string.tabs_home), IconName.Home),
                        TabBarItem("history", stringResource(R.string.tabs_history), IconName.Clock),
                        TabBarItem("scan", stringResource(R.string.tabs_scan), IconName.Camera),
                        TabBarItem("learn", stringResource(R.string.tabs_learn), IconName.BookOpen),
                        TabBarItem("settings", stringResource(R.string.tabs_settings), IconName.Settings),
                    ),
                    activeTab,
                    { activeTab = it },
                    centerKey = "scan",
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A titled block with a hairline above it. */
@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    val border = YoloCTheme.colors.border
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(top = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SectionHeader(title, subtitle = subtitle)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
    }
}
