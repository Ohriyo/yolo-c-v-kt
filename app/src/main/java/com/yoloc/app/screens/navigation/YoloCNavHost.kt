package com.yoloc.app.screens.navigation

import android.app.Activity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yoloc.app.BuildConfig
import com.yoloc.app.R
import com.yoloc.app.components.AppTabBar
import com.yoloc.app.components.IconName
import com.yoloc.app.components.TabBarItem
import com.yoloc.app.screens.appContainer
import com.yoloc.app.screens.about.AboutScreen
import com.yoloc.app.screens.dev.GalleryScreen
import com.yoloc.app.screens.dev.InferenceScreen
import com.yoloc.app.screens.dev.SeedScreen
import com.yoloc.app.screens.history.HistoryScreen
import com.yoloc.app.screens.history.ScanDetailScreen
import com.yoloc.app.screens.home.HomeScreen
import com.yoloc.app.screens.learn.LearnScreen
import com.yoloc.app.screens.learn.LearnTopicScreen
import com.yoloc.app.screens.learn.learnTopicTitle
import com.yoloc.app.screens.onboarding.PermissionsScreen
import com.yoloc.app.screens.onboarding.WelcomeScreen
import com.yoloc.app.screens.plots.PlotsScreen
import com.yoloc.app.screens.scan.CameraScreen
import com.yoloc.app.screens.scan.LiveScreen
import com.yoloc.app.screens.scan.PreviewScreen
import com.yoloc.app.screens.scan.ProcessingScreen
import com.yoloc.app.screens.scan.ResultScreen
import com.yoloc.app.screens.scan.ResultViewModel
import com.yoloc.app.screens.settings.SettingsScreen
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.YoloCTheme

/** Not a tab: the center button opens the full-screen camera (CLAUDE.md 3.2). */
private data object ScanButton

internal val tabRoutes = listOf(Home::class, History::class, Learn::class, Settings::class)

private fun NavDestination?.isTab() = this != null && tabRoutes.any { hasRoute(it) }

private fun NavDestination?.isCamera() = this != null && (hasRoute<Camera>() || hasRoute<Live>())

private fun NavBackStackEntry.isTab() = destination.isTab()

/**
 * RN: app/_layout.tsx and app/(tabs)/_layout.tsx. The onboarding gate, the tabs with their bar,
 * and every stack screen. Screens not built yet show a placeholder (RN: strings.placeholder).
 */
@Composable
fun YoloCNavHost(onboardingDone: Boolean) {
    val nav = rememberNavController()
    // Chosen once: changing a NavHost's startDestination later would rebuild the graph.
    val start: Any = remember { if (onboardingDone) MainGraph else OnboardingGraph }
    // RN: Stack.Protected. Flipping the flag swaps graphs and drops the other graph's history, so
    // back from Home exits instead of returning to onboarding (and "Show onboarding again" works
    // the other way).
    LaunchedEffect(onboardingDone) {
        val inMain = nav.currentDestination?.hierarchy?.any { it.hasRoute<MainGraph>() } == true
        if (onboardingDone && !inMain) nav.navigate(MainGraph) { popUpTo<OnboardingGraph> { inclusive = true } }
        if (!onboardingDone && inMain) nav.navigate(OnboardingGraph) { popUpTo<MainGraph> { inclusive = true } }
    }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTab = destination.isTab()
    CameraBars(destination.isCamera())

    val colors = YoloCTheme.colors
    val items = listOf<TabBarItem<Any>>(
        TabBarItem(Home, stringResource(R.string.tabs_home), IconName.Home),
        TabBarItem(History, stringResource(R.string.tabs_history), IconName.Clock),
        // Scan sits in the middle of the five, after Home and History.
        TabBarItem(ScanButton, stringResource(R.string.tabs_scan), IconName.Camera),
        TabBarItem(Learn, stringResource(R.string.tabs_learn), IconName.BookOpen),
        TabBarItem(Settings, stringResource(R.string.tabs_settings), IconName.Settings),
    )
    val activeTab: Any = items.firstOrNull { destination?.hasRoute(it.key::class) == true }?.key ?: Home

    Column(Modifier.fillMaxSize().background(colors.background)) {
        NavHost(
            navController = nav,
            startDestination = start,
            // The tab bar keeps the navigation bar clear, so tab screens must not pad for it too.
            modifier = Modifier.weight(1f).then(if (onTab) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
            // RN: JS tabs switch without animation; stack screens fade (the camera's own "fade").
            enterTransition = { if (initialState.isTab() && targetState.isTab()) EnterTransition.None else fadeIn(tween(Motion.BASE)) },
            exitTransition = { if (initialState.isTab() && targetState.isTab()) ExitTransition.None else fadeOut(tween(Motion.BASE)) },
        ) {
            navigation<OnboardingGraph>(startDestination = Welcome) {
                composable<Welcome> { entry -> WelcomeScreen(onToPrimer = { entry.ifShown { nav.navigate(Permissions) } }) }
                composable<Permissions> { PermissionsScreen() }
            }
            navigation<MainGraph>(startDestination = Home) {
                composable<Home> { entry ->
                    val requests = appContainer().historyRequests
                    HomeScreen(
                        onScan = { entry.ifShown { nav.navigate(Camera) } },
                        onOpenScan = { id -> entry.ifShown { nav.navigate(ScanDetail(id)) } },
                        onOpenHistory = { nav.selectTab(History) },
                        onOpenCount = { v -> nav.openHistory(requests, HistoryRequest.ByVerdict(v)) },
                        onOpenLearn = { nav.selectTab(Learn) },
                    )
                }
                composable<History> { entry ->
                    HistoryScreen(
                        onOpenScan = { id -> entry.ifShown { nav.navigate(ScanDetail(id)) } },
                        onScan = { entry.ifShown { nav.navigate(Camera) } },
                    )
                }
                composable<Learn> { entry -> LearnScreen(onOpenTopic = { topic -> entry.ifShown { nav.navigate(LearnTopic(topic.key)) } }) }
                composable<Settings> { entry ->
                    SettingsScreen(
                        onOpenPlots = { entry.ifShown { nav.navigate(Plots) } },
                        onOpenAbout = { entry.ifShown { nav.navigate(About) } },
                        onOpenGallery = { entry.ifShown { nav.navigate(DevGallery) } },
                        onOpenSeed = { entry.ifShown { nav.navigate(DevSeed) } },
                        onOpenInference = { entry.ifShown { nav.navigate(DevInference) } },
                    )
                }
                // The camera screens are dark in both themes, including during the fade.
                // The photo reaches Preview from the ViewModel's state (after the picker or the
                // capture), so these navigations are not gated on ifShown: a dropped one would
                // leave the photo waiting with no screen.
                composable<Camera> { entry ->
                    CameraScreen(
                        onClose = { entry.ifShown { nav.closeCamera() } },
                        onOpenPreview = { nav.navigate(Preview) },
                        onLive = { entry.ifShown { nav.switchToLive() } },
                    )
                }
                composable<Live> { entry ->
                    LiveScreen(
                        onClose = { entry.ifShown { nav.closeCamera() } },
                        onPhotoMode = { entry.ifShown { nav.switchToPhoto() } },
                        onBack = { entry.ifShown { nav.closeCamera() } },
                        onOpenPreview = { nav.liveToPreview() },
                    )
                }
                composable<Preview> { entry ->
                    StackScreen(stringResource(R.string.preview_title), onBack = { entry.ifShown { nav.navigateUp() } }) {
                        PreviewScreen(
                            onBackToCamera = { entry.ifShown { nav.dismissToCamera() } },
                            onUsePhoto = { entry.ifShown { nav.navigate(Processing) } },
                        )
                    }
                }
                composable<Processing> { entry ->
                    // Back while analyzing returns to Preview; leaving clears the ViewModel, which cancels the run.
                    val back = { entry.ifShown { nav.navigateUp() } }
                    StackScreen(stringResource(R.string.processing_title), onBack = back) {
                        ProcessingScreen(
                            // Fired once by the Done state, which can land before the screen is
                            // RESUMED (back from the background, or during the fade in): never dropped.
                            onDone = { nav.processingToResult() },
                            onBack = back,
                            onBackToCamera = { entry.ifShown { nav.dismissToCamera() } },
                        )
                    }
                }
                composable<Result> { entry ->
                    val vm: ResultViewModel = viewModel(factory = ResultViewModel.Factory)
                    // RN: usePreventRemove covers the header back too: guarded, it asks first.
                    val back = { entry.ifShown { if (vm.state.value.guarded) vm.onLeaveRequested() else nav.navigateUp() } }
                    StackScreen(stringResource(R.string.result_title), onBack = back) {
                        ResultScreen(
                            vm = vm,
                            onDiscarded = { nav.dismissToCamera() },
                            onSaved = { nav.openSavedScan(it) },
                            onManagePlots = { entry.ifShown { nav.navigate(Plots) } },
                            onBackToCamera = { entry.ifShown { nav.dismissToCamera() } },
                        )
                    }
                }
                composable<ScanDetail> { entry ->
                    // RN: canGoBack() ? back() : navigate('/history'). Only from the shown screen, so a
                    // second back (or one landing as a delete closes it) cannot pop the screen below.
                    val back = { entry.ifShown { if (!nav.popBackStack()) nav.selectTab(History) } }
                    StackScreen(stringResource(R.string.scan_detail_title), onBack = back) { ScanDetailScreen(onBack = back) }
                }
                composable<LearnTopic> { entry ->
                    val back = { entry.ifShown { nav.navigateUp() } }
                    StackScreen(learnTopicTitle(entry.toRoute<LearnTopic>().topic), onBack = back) {
                        LearnTopicScreen(
                            onBack = back,
                            onOpenAbout = { entry.ifShown { nav.navigate(About) } },
                            onOpenSettings = { nav.leaveForTab(Settings) },
                        )
                    }
                }
                composable<About> { entry ->
                    StackScreen(stringResource(R.string.about_title), onBack = { entry.ifShown { nav.navigateUp() } }) { AboutScreen() }
                }
                composable<Plots> { entry ->
                    StackScreen(stringResource(R.string.plots_title), onBack = { entry.ifShown { nav.navigateUp() } }) {
                        val session = appContainer().scanSession
                        PlotsScreen(
                            onOpenPlot = { sendRequest ->
                                entry.ifShown {
                                    // Opened from Result's save sheet: RN's leave guard stops the jump
                                    // to History and asks; here Result is shown again and asks there.
                                    if (!nav.returnToUnsavedResult(session)) {
                                        sendRequest()
                                        nav.leaveForTab(History)
                                    }
                                }
                            },
                        )
                    }
                }
                if (BuildConfig.DEBUG) {
                    composable<DevGallery> { GalleryScreen() }
                    composable<DevSeed> { entry ->
                        StackScreen(stringResource(R.string.dev_seed_title), onBack = { entry.ifShown { nav.navigateUp() } }) { SeedScreen() }
                    }
                    composable<DevInference> { entry ->
                        StackScreen(stringResource(R.string.settings_dev_inference), onBack = { entry.ifShown { nav.navigateUp() } }) {
                            InferenceScreen(Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
                        }
                    }
                }
            }
        }
        if (onTab) {
            AppTabBar(
                items = items,
                activeKey = activeTab,
                centerKey = ScanButton,
                onSelect = { key ->
                    when {
                        key == ScanButton -> entry?.ifShown { nav.navigate(Camera) }
                        key != activeTab -> nav.selectTab(key)
                    }
                },
            )
        }
    }
}

/**
 * RN: `<StatusBar style="light" />` on the camera screens: light bar icons over the dark camera,
 * in both themes. Leaving the camera puts back the theme's icons.
 */
@Composable
private fun CameraBars(onCamera: Boolean) {
    val view = LocalView.current
    val dark = YoloCTheme.colors.isDark
    LaunchedEffect(view, onCamera, dark) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark && !onCamera
            isAppearanceLightNavigationBars = !dark && !onCamera
        }
    }
}

