package com.yoloc.app.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.yoloc.app.features.onboarding.ClassLegend
import com.yoloc.app.features.onboarding.FeatureRows
import com.yoloc.app.features.onboarding.PanelChip
import com.yoloc.app.features.onboarding.PanelFeature
import com.yoloc.app.features.onboarding.ScanOverlay
import com.yoloc.app.util.rememberReduceMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.store.USER_NAME_MAX
import com.yoloc.app.components.YTextField
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import com.yoloc.app.components.AppText
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.Tone
import com.yoloc.app.features.learn.onboardingTipPicks
import com.yoloc.app.features.learn.photoTips
import com.yoloc.app.features.onboarding.PhotoIllustration
import com.yoloc.app.features.onboarding.FrameAction
import com.yoloc.app.features.onboarding.ONBOARDING_STEPS
import com.yoloc.app.features.onboarding.OnboardingFrame
import com.yoloc.app.features.onboarding.OnboardingSlide
import com.yoloc.app.features.onboarding.RecallIllustration
import com.yoloc.app.features.onboarding.ViewfinderIllustration
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: app/(onboarding)/welcome.tsx. First run (CLAUDE.md 3.1): four short steps, including how to
 * take a photo the detector can read, before the permission primer. Skip is always there; Android
 * back steps back a slide.
 */
@Composable
fun WelcomeScreen(onToPrimer: () -> Unit, vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = s.index > 0) { vm.back() }
    val reduceMotion = rememberReduceMotion()
    val forward = if (s.isLast) onToPrimer else vm::next
    OnboardingFrame(
        step = s.index,
        total = s.total,
        title = stringResource(s.slide.title),
        body = stringResource(s.slide.body),
        primary = if (s.isLast) {
            FrameAction(stringResource(R.string.onboarding_get_started), onToPrimer)
        } else {
            FrameAction(stringResource(R.string.onboarding_next), vm::next)
        },
        // Redesign 2026-10-31: the slides also swipe, like the buttons (and Android back).
        modifier = Modifier.swipeSteps(onForward = forward, onBack = { vm.back() }),
        onSkip = if (s.isLast) null else onToPrimer,
        illustration = { SlideIllustration(s.slide, s.aidFound) },
        pebble = true,
        chips = slideChips(s.slide),
        overlay = { if (s.slide == OnboardingSlide.Scan) ScanOverlay(reduceMotion) },
        features = slideFeatures(s.slide, s.aidFound),
    ) {
        if (s.slide == OnboardingSlide.Tips) PhotoTipsPanel(reduceMotion)
        if (s.slide == OnboardingSlide.Aid) ClassLegend(reduceMotion, Modifier.padding(top = Spacing.xs))
    }
}

/** A horizontal fling past [SWIPE_DISTANCE]: left goes forward, right goes back. */
private fun Modifier.swipeSteps(onForward: () -> Unit, onBack: () -> Unit) = pointerInput(Unit) {
    var dragged = 0f
    detectHorizontalDragGestures(
        onDragStart = { dragged = 0f },
        onDragEnd = {
            val distance = SWIPE_DISTANCE.toPx()
            if (dragged < -distance) onForward() else if (dragged > distance) onBack()
        },
        onHorizontalDrag = { _, dx -> dragged += dx },
    )
}

private val SWIPE_DISTANCE = 56.dp

/** Neutral facts over the photo's lower edge; never a result class. */
@Composable
private fun slideChips(slide: OnboardingSlide): List<PanelChip> = when (slide) {
    OnboardingSlide.Scan -> listOf(
        PanelChip(IconName.Cpu, stringResource(R.string.onboarding_chip_on_device)),
        PanelChip(IconName.Crosshair, stringResource(R.string.onboarding_chip_one_pod)),
    )
    OnboardingSlide.Offline -> listOf(
        PanelChip(IconName.WifiOff, stringResource(R.string.onboarding_chip_offline)),
        PanelChip(IconName.Lock, stringResource(R.string.onboarding_chip_private)),
    )
    OnboardingSlide.Tips, OnboardingSlide.Aid -> emptyList()
}

/**
 * The short lines under each slide's text. The tips slide has its photo tips instead. On the aid
 * slide the measured miss rate leads (the body already says it can miss early signs); without a
 * recall in the model config, the plain limit does.
 */
@Composable
private fun slideFeatures(slide: OnboardingSlide, found: Int?): List<PanelFeature> = when (slide) {
    OnboardingSlide.Scan -> listOf(
        PanelFeature(IconName.Crosshair, stringResource(R.string.onboarding_scan_point)),
        PanelFeature(IconName.Camera, stringResource(R.string.onboarding_scan_shutter)),
        PanelFeature(IconName.Clock, stringResource(R.string.onboarding_scan_seconds)),
    )
    OnboardingSlide.Offline -> listOf(
        PanelFeature(IconName.WifiOff, stringResource(R.string.onboarding_offline_signal)),
        PanelFeature(IconName.MapPin, stringResource(R.string.onboarding_offline_plots)),
        PanelFeature(IconName.Share2, stringResource(R.string.onboarding_offline_csv)),
    )
    OnboardingSlide.Tips -> emptyList()
    OnboardingSlide.Aid -> listOf(
        if (found == null) {
            PanelFeature(IconName.Eye, stringResource(R.string.onboarding_aid_limit))
        } else {
            PanelFeature(IconName.BarChart2, stringResource(R.string.onboarding_aid_detail, 10 - found))
        },
        PanelFeature(IconName.Layers, stringResource(R.string.onboarding_aid_verdict)),
        PanelFeature(IconName.Sliders, stringResource(R.string.onboarding_aid_confidence)),
    )
}

@Composable
private fun SlideIllustration(slide: OnboardingSlide, found: Int?) {
    when (slide) {
        OnboardingSlide.Scan -> PhotoIllustration(R.drawable.photo_cacao_cluster)
        OnboardingSlide.Offline -> PhotoIllustration(R.drawable.photo_cacao_column)
        // Scaled with the print, so its umbrella never meets the pebble's corner.
        OnboardingSlide.Tips -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val scale = (maxHeight / VIEWFINDER_ROOM).coerceAtMost(1f)
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { ViewfinderIllustration() }
        }
        OnboardingSlide.Aid -> if (found == null) {
            PhotoIllustration(R.drawable.photo_cacao_cluster)
        } else {
            RecallIllustration(found, stringResource(R.string.about_recall_a11y, found))
        }
    }
}

/** The viewfinder drawing's height with its umbrella and some air around it. */
private val VIEWFINDER_ROOM = 232.dp

/** The four photo tips, as feature rows. All four stay when space runs out: they are the slide. */
@Composable
private fun PhotoTipsPanel(reduceMotion: Boolean) {
    FeatureRows(
        onboardingTipPicks.mapNotNull { photoTips.getOrNull(it) }.map { PanelFeature(it.icon, stringResource(it.text)) },
        reduceMotion,
        Modifier.padding(top = Spacing.xs),
    )
}

/**
 * RN: app/(onboarding)/permissions.tsx. Permission primer (CLAUDE.md 3.1/3.3): explains, but
 * requests nothing. The camera is asked for only when the user first opens Scan; location only if
 * turned on in Settings. Continue leads to the name step (RN: Continue ends onboarding).
 */
@Composable
fun PermissionsScreen(onNext: () -> Unit) {
    val colors = YoloCTheme.colors
    OnboardingFrame(
        step = ONBOARDING_STEPS - 2,
        total = ONBOARDING_STEPS,
        title = stringResource(R.string.onboarding_permissions_title),
        body = stringResource(R.string.onboarding_permissions_body),
        primary = FrameAction(stringResource(R.string.onboarding_continue), onNext),
        illustration = { PhotoIllustration(R.drawable.photo_cacao_cluster) },
    ) {
        Row(
            Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(IconName.MapPin, size = 20.dp, color = colors.icon)
            AppText(stringResource(R.string.onboarding_permissions_location), Modifier.weight(1f), tone = Tone.Muted)
        }
    }
}

/**
 * Redesign 2026-10-28 (Kotlin only): the optional first name Home greets with, as its own last
 * step. Continue saves it (when given) and flips the flag; the NavHost then swaps onboarding for
 * the tabs.
 */
@Composable
fun NameScreen(vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    var name by rememberSaveable { mutableStateOf("") }
    OnboardingFrame(
        step = ONBOARDING_STEPS - 1,
        total = ONBOARDING_STEPS,
        title = stringResource(R.string.onboarding_name_label),
        body = stringResource(R.string.onboarding_name_body),
        primary = FrameAction(stringResource(R.string.onboarding_continue), { vm.complete(name) }),
        illustration = { PhotoIllustration(R.drawable.photo_cacao_column) },
    ) {
        YTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.onboarding_name_placeholder),
            accessibilityLabel = stringResource(R.string.onboarding_name_a11y),
            maxLength = USER_NAME_MAX,
        )
    }
}
