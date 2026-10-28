package com.yoloc.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.ErrorState
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.screens.navigation.YoloCNavHost
import com.yoloc.app.store.SettingsStore
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: app/_layout.tsx RootLayout + ThemedStack. Opens the database and reads settings before
 * choosing onboarding or the tabs; the theme follows the saved setting ("system" until then).
 */
@Composable
fun YoloCRoot(vm: RootViewModel = viewModel(factory = RootViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    YoloCTheme(preference = s.theme) {
        when (s.status) {
            SettingsStore.Status.Error -> ScreenContainer(scroll = false) {
                ErrorState(message = stringResource(R.string.startup_db_error_body), onRetry = vm::retry)
            }
            SettingsStore.Status.Ready -> YoloCNavHost(onboardingDone = s.onboardingDone)
            // Brief, local-only wait; the background color avoids a white flash.
            else -> Box(Modifier.fillMaxSize().background(YoloCTheme.colors.background))
        }
    }
}
