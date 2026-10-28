package com.yoloc.app.screens.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.yoloc.app.screens.scan.ResultViewModel
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.ScanSession

/*
 * RN router calls, as the guide's table (5.3) maps them. Screens call these instead of building
 * navigate options themselves, so every exit behaves the same wherever it starts.
 */

/**
 * RN: (tabs) backBehavior="firstRoute". Tabs sit on Home and keep their state, so back from any
 * tab lands on Home and back from Home leaves the app. Selecting the current tab does nothing.
 */
fun NavController.selectTab(tab: Any) {
    navigate(tab) {
        popUpTo<Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * RN: router.navigate('/settings') from a screen over the tabs. The stack screens are popped
 * first, so the tab they were opened from is saved (and later restored) without them; otherwise
 * coming back to that tab would show a stack screen with no tab bar.
 */
fun NavController.leaveForTab(tab: Any) {
    while (currentDestination?.let { d -> tabRoutes.any { d.hasRoute(it) } } == false && popBackStack()) Unit
    selectTab(tab)
}

/**
 * Runs a forward navigation only while this screen is the one shown (RESUMED), so a second tap
 * during the transition cannot push the next screen twice.
 */
fun NavBackStackEntry.ifShown(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}

/** RN: Home chip / Plots row -> navigate('/history', params). History consumes the request. */
fun NavController.openHistory(requests: HistoryRequests, request: HistoryRequest) {
    requests.send(request)
    selectTab(History)
}

/** RN: camera close, `canGoBack() ? back() : navigate('/')`. */
fun NavController.closeCamera() {
    if (!popBackStack()) navigate(Home)
}

/** RN: ModeSwitch, router.replace('/scan/live'). */
fun NavController.switchToLive() = navigate(Live) { popUpTo<Camera> { inclusive = true } }

/** RN: ModeSwitch, router.replace('/scan/camera'). */
fun NavController.switchToPhoto() = navigate(Camera) { popUpTo<Live> { inclusive = true } }

/** RN: live capture, router.replace('/scan/preview'). */
fun NavController.liveToPreview() = navigate(Preview) { popUpTo<Live> { inclusive = true } }

/** RN: processing done, router.replace('/scan/result'). */
fun NavController.processingToResult() = navigate(Result) { popUpTo<Processing> { inclusive = true } }

/** RN: router.dismissTo('/scan/camera') (Retake, Discard, missing photo). */
fun NavController.dismissToCamera() {
    if (!popBackStack<Camera>(inclusive = false)) navigate(Camera) { popUpTo<Home>() }
}

/**
 * RN: leaving a stack above an unsaved Result is stopped by its usePreventRemove, which asks Stay /
 * Discard / Save. If Result is below with unsaved pods, it is shown again with that question and
 * true is returned (the caller does not leave); otherwise false.
 */
fun NavController.returnToUnsavedResult(session: ScanSession): Boolean {
    val entry = runCatching { getBackStackEntry<Result>() }.getOrNull() ?: return false
    // Result guards while its scan has pods; the session holds that scan until Result exits.
    if (session.state.value.result?.detections.isNullOrEmpty()) return false
    popBackStack<Result>(inclusive = false)
    // Result is shown now, so its ViewModel is the one its screen uses (made here if it was not yet).
    ViewModelProvider.create(entry, ResultViewModel.Factory, entry.defaultViewModelCreationExtras)[ResultViewModel::class].onLeaveRequested()
    return true
}

/** RN: saved, dismissTo('/history') then push /history/[id]?saved=1. */
fun NavController.openSavedScan(id: String) {
    navigate(History) {
        popUpTo<Home>()
        launchSingleTop = true
    }
    navigate(ScanDetail(id, saved = true))
}
