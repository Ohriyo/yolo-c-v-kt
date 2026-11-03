package com.yoloc.app.screens

import androidx.activity.compose.ReportDrawn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.screens.intro.IntroScreen
import com.yoloc.app.screens.intro.SplashHandoff
import com.yoloc.app.screens.navigation.YoloCNavHost
import com.yoloc.app.store.SettingsStore
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: app/_layout.tsx RootLayout + ThemedStack. Opens the database and reads settings before
 * choosing onboarding or the tabs; the theme follows the saved setting ("system" until then).
 * Redesign 2026-10-31: on a cold start ([intro] set by MainActivity) the launch intro plays first,
 * then crossfades to that same first screen. It is not a route, so Back never returns to it. The
 * first screen is composed only when the intro ends: composed under it, its first frame (about 1 s
 * of main thread in a debug build) held up the splash and stalled the intro.
 */
@Composable
fun YoloCRoot(intro: SplashHandoff? = null, vm: RootViewModel = viewModel(factory = RootViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    // Not saved: an activity that starts again gets no intro (MainActivity passes null then).
    var showIntro by remember { mutableStateOf(intro != null) }
    YoloCTheme(preference = s.theme) {
        when (s.status) {
            SettingsStore.Status.Error -> ScreenContainer(scroll = false) {
                ErrorState(message = stringResource(R.string.startup_db_error_body), onRetry = vm::retry)
            }
            SettingsStore.Status.Ready -> Crossfade(showIntro, animationSpec = tween(Motion.BASE), label = "intro") { introShown ->
                if (introShown && intro != null) {
                    IntroScreen(intro, onDone = { showIntro = false })
                } else {
                    // "Fully drawn" in Logcat: cold start to the first interactive screen (PARITY.md Step 6).
                    ReportDrawn()
                    YoloCNavHost(onboardingDone = s.onboardingDone)
                }
            }
            // Brief, local-only wait; the background color avoids a white flash.
            else -> Box(Modifier.fillMaxSize().background(YoloCTheme.colors.background))
        }
    }
}
