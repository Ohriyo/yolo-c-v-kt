package com.yoloc.app.screens.navigation

import android.app.Activity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yoloc.app.BuildConfig
import com.yoloc.app.R
import com.yoloc.app.components.AppTabBar
import com.yoloc.app.components.AppText
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.TabBarItem
import com.yoloc.app.components.Tone
import com.yoloc.app.screens.dev.InferenceScreen
import com.yoloc.app.screens.home.HomeScreen
import com.yoloc.app.screens.learn.LearnScreen
import com.yoloc.app.screens.learn.LearnTopicScreen
import com.yoloc.app.screens.learn.learnTopicTitle
import com.yoloc.app.screens.onboarding.PermissionsScreen
import com.yoloc.app.screens.onboarding.WelcomeScreen
import com.yoloc.app.theme.Motion
import com.yoloc.app.theme.TypeVariant
import com.yoloc.app.theme.YoloCTheme

/** Not a tab: the center button opens the full-screen camera (CLAUDE.md 3.2). */
private data object ScanButton

private val tabRoutes = listOf(Home::class, History::class, Learn::class, Settings::class)

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
                composable<Welcome> { WelcomeScreen(onToPrimer = { nav.navigate(Permissions) }) }
                composable<Permissions> { PermissionsScreen() }
            }
            navigation<MainGraph>(startDestination = Home) {
                composable<Home> {
                    HomeScreen(
                        onScan = { nav.navigate(Camera) },
                        onOpenScan = { id -> nav.navigate(ScanDetail(id)) },
                        onOpenHistory = { nav.selectTab(History) },
                        onOpenLearn = { nav.selectTab(Learn) },
                    )
                }
                composable<History> { Placeholder(stringResource(R.string.tabs_history)) }
                composable<Learn> { LearnScreen(onOpenTopic = { topic -> nav.navigate(LearnTopic(topic.key)) }) }
                composable<Settings> { Placeholder(stringResource(R.string.tabs_settings)) }
                // The camera screens are dark in both themes, including during the fade.
                composable<Camera> { CameraPlaceholder() }
                composable<Live> { CameraPlaceholder() }
                stack<Preview>(nav, R.string.preview_title)
                stack<Processing>(nav, R.string.processing_title)
                stack<Result>(nav, R.string.result_title)
                stack<ScanDetail>(nav, R.string.scan_detail_title)
                composable<LearnTopic> { entry ->
                    StackScreen(learnTopicTitle(entry.toRoute<LearnTopic>().topic), onBack = { nav.navigateUp() }) {
                        LearnTopicScreen(
                            onBack = { nav.navigateUp() },
                            onOpenAbout = { nav.navigate(About) },
                            onOpenSettings = { nav.selectTab(Settings) },
                        )
                    }
                }
                stack<About>(nav, R.string.about_title)
                stack<Plots>(nav, R.string.plots_title)
                if (BuildConfig.DEBUG) {
                    composable<DevGallery> { Placeholder(null) }
                    stack<DevSeed>(nav, R.string.dev_seed_title)
                    composable<DevInference> {
                        StackScreen(stringResource(R.string.settings_dev_inference), onBack = { nav.navigateUp() }) {
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
                        key == ScanButton -> nav.navigate(Camera)
                        key != activeTab -> nav.selectTab(key)
                    }
                },
            )
        }
    }
}

/** A stack screen with its header; the body is a placeholder until the screen is ported. */
private inline fun <reified T : Any> androidx.navigation.NavGraphBuilder.stack(nav: NavHostController, title: Int) {
    composable<T> {
        StackScreen(stringResource(title), onBack = { nav.navigateUp() }) { Placeholder(null) }
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

@Composable
private fun Placeholder(title: String?) {
    ScreenContainer {
        if (title != null) AppText(title, variant = TypeVariant.Headline)
        AppText(stringResource(R.string.placeholder), tone = Tone.Muted)
    }
}

@Composable
private fun CameraPlaceholder() {
    Box(Modifier.fillMaxSize().background(YoloCTheme.colors.camera.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        AppText(stringResource(R.string.placeholder), color = YoloCTheme.colors.camera.lensText)
    }
}
