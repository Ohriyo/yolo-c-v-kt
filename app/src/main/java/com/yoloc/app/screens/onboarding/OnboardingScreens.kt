package com.yoloc.app.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.yoloc.app.theme.Radii
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
        onSkip = if (s.isLast) null else onToPrimer,
        illustration = { SlideIllustration(s.slide, s.aidFound) },
    ) {
        if (s.slide == OnboardingSlide.Tips) PhotoTipsPanel()
        val found = s.aidFound
        if (s.slide == OnboardingSlide.Aid && found != null) {
            AppText(stringResource(R.string.onboarding_aid_detail, 10 - found), variant = TypeVariant.BodyStrong)
        }
    }
}

@Composable
private fun SlideIllustration(slide: OnboardingSlide, found: Int?) {
    when (slide) {
        OnboardingSlide.Scan -> PhotoIllustration(R.drawable.photo_cacao_cluster)
        OnboardingSlide.Offline -> PhotoIllustration(R.drawable.photo_cacao_column)
        OnboardingSlide.Tips -> ViewfinderIllustration()
        OnboardingSlide.Aid -> if (found == null) {
            PhotoIllustration(R.drawable.photo_cacao_cluster)
        } else {
            RecallIllustration(found, stringResource(R.string.about_recall_a11y, found))
        }
    }
}

@Composable
private fun PhotoTipsPanel() {
    val colors = YoloCTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs)
            .background(colors.surface, RoundedCornerShape(Radii.md))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        for (tip in onboardingTipPicks.mapNotNull { photoTips.getOrNull(it) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(colors.surfaceRaised, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(tip.icon, size = 20.dp, color = colors.icon)
                }
                AppText(stringResource(tip.text), Modifier.weight(1f))
            }
        }
    }
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
