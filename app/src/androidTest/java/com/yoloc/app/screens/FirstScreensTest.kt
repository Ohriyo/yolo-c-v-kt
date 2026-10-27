package com.yoloc.app.screens

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.R
import com.yoloc.app.db.MemoryFileStore
import com.yoloc.app.db.TestDb
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.ScanRepository
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.features.detection.Verdict
import com.yoloc.app.features.learn.LearnTopic
import com.yoloc.app.features.scans.SaveScanInput
import com.yoloc.app.features.scans.saveScan
import com.yoloc.app.screens.home.HomeScreen
import com.yoloc.app.screens.home.HomeViewModel
import com.yoloc.app.screens.learn.LearnScreen
import com.yoloc.app.screens.learn.LearnTopicScreen
import com.yoloc.app.screens.learn.LearnTopicViewModel
import com.yoloc.app.screens.onboarding.OnboardingViewModel
import com.yoloc.app.screens.onboarding.PermissionsScreen
import com.yoloc.app.screens.onboarding.WelcomeScreen
import com.yoloc.app.store.HistoryRequest
import com.yoloc.app.store.HistoryRequests
import com.yoloc.app.store.ScanLibrary
import com.yoloc.app.store.SettingsStore
import com.yoloc.app.theme.YoloCTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Step 5 screens, group 1 (Onboarding, Home, Learn), against the real Room database in memory.
 * RN: navigation.test.tsx "Onboarding gate", history.test.tsx "Empty states" and "Home",
 * settings.screens.test.tsx "opens each Learn topic".
 */
@RunWith(AndroidJUnit4::class)
class FirstScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val holder = TestDb.holder()
    private val scans = ScanRepository(holder)
    private val classes = ClassRepository(holder)
    private val files = MemoryFileStore()
    private val library = ScanLibrary(scans)
    private val requests = HistoryRequests()
    private val config = TestDb.config
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun open() = runBlocking { holder.get(); Unit }

    @After
    fun close() {
        scope.cancel()
        holder.close()
    }

    private fun text(@StringRes id: Int, vararg args: Any) = TestDb.context.getString(id, *args)

    private fun onboarding(): Pair<OnboardingViewModel, SettingsStore> {
        val store = SettingsStore(SettingsRepository(holder), config, scope)
        return OnboardingViewModel(store, config) to store
    }

    @Test
    fun welcomeStepsForwardAndBackAndSkipsToThePrimer() {
        val (vm, _) = onboarding()
        var toPrimer = 0
        compose.setContent { YoloCTheme { WelcomeScreen(onToPrimer = { toPrimer++ }, vm = vm) } }

        compose.onNodeWithText(text(R.string.onboarding_scan_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(text(R.string.onboarding_offline_title)).assertIsDisplayed()
        // Android back steps back a slide instead of leaving.
        Espresso.pressBack()
        compose.onNodeWithText(text(R.string.onboarding_scan_title)).assertIsDisplayed()

        compose.onNodeWithContentDescription(text(R.string.onboarding_next)).performClick()
        compose.onNodeWithContentDescription(text(R.string.onboarding_next)).performClick()
        // The tips slide shows the first four photo tips.
        compose.onNodeWithText(text(R.string.learn_photo_tip_fill)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.onboarding_next)).performClick()
        // The aid slide counts the model's misses from the config's recall (0.596: 4 in 10 missed).
        compose.onNodeWithText(text(R.string.onboarding_aid_detail, 4)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.about_recall_a11y, 6)).assertExists()
        // No Skip on the last slide; Get started goes to the primer.
        assertTrue(compose.onAllNodesWithContentDescription(text(R.string.onboarding_skip)).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription(text(R.string.onboarding_get_started)).performClick()
        assertEquals(1, toPrimer)
    }

    @Test
    fun skipGoesToThePrimer() {
        val (vm, _) = onboarding()
        var toPrimer = 0
        compose.setContent { YoloCTheme { WelcomeScreen(onToPrimer = { toPrimer++ }, vm = vm) } }
        compose.onNodeWithContentDescription(text(R.string.onboarding_skip)).performClick()
        assertEquals(1, toPrimer)
    }

    @Test
    fun thePrimerAsksForNothingAndContinueFinishesOnboarding() {
        val (vm, store) = onboarding()
        runBlocking { store.hydrate() }
        compose.setContent { YoloCTheme { PermissionsScreen(vm = vm) } }
        compose.onNodeWithText(text(R.string.onboarding_permissions_location)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.onboarding_continue)).performClick()
        compose.waitUntil(5_000) { store.state.value.onboardingDone }
    }

    @Test
    fun homeInvitesAFirstScanInsteadOfAnEmptyList() {
        var scanned = 0
        val vm = HomeViewModel(scans, library, requests)
        compose.setContent {
            YoloCTheme {
                HomeScreen(onScan = { scanned++ }, onOpenScan = {}, onOpenHistory = {}, onOpenLearn = {}, vm = vm)
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.home_no_scans_title)).fetchSemanticsNodes().isNotEmpty() }
        // "See all" only once there is something to see.
        assertTrue(compose.onAllNodesWithContentDescription(text(R.string.home_see_all)).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription(text(R.string.home_scan_cta)).performClick()
        assertEquals(1, scanned)
    }

    @Test
    fun homeShowsTheNewestScansAndCountsThatOpenAFilteredHistory() {
        val now = System.currentTimeMillis()
        runBlocking {
            for ((i, cls) in listOf(0, 1, 2, 2).withIndex()) {
                saveScan(
                    scans,
                    files,
                    SaveScanInput(
                        sourceImage = File("/cache/photo.jpg"),
                        modelVersionId = config.id,
                        imageWidth = 800,
                        imageHeight = 600,
                        source = ScanSource.Camera,
                        confThreshold = 0.25,
                        iouThreshold = 0.45,
                        notes = "Tree $i",
                        createdAt = now - i * 60_000L,
                        detections = listOf(NewDetection(cls, 0.9, 0.1, 0.1, 0.2, 0.2)),
                    ),
                    config.lowConfidenceThreshold,
                )
            }
        }
        var opened: String? = null
        var history = 0
        val vm = HomeViewModel(scans, library, requests)
        compose.setContent {
            YoloCTheme {
                HomeScreen(
                    onScan = {},
                    onOpenScan = { opened = it },
                    onOpenHistory = { history++ },
                    onOpenLearn = {},
                    vm = vm,
                )
            }
        }
        // Rows are read as one label, notes last ("…, Notes: Tree 0").
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Notes: Tree 0", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Three newest only.
        compose.onNodeWithContentDescription("Notes: Tree 2", substring = true).assertExists()
        assertTrue(compose.onAllNodesWithContentDescription("Notes: Tree 3", substring = true).fetchSemanticsNodes().isEmpty())

        val infected = TestDb.context.resources.getQuantityString(R.plurals.home_count_a11y, 2, text(R.string.verdict_short_infected), 2)
        compose.onNodeWithContentDescription(infected).performClick()
        assertEquals(1, history)
        assertEquals(HistoryRequest.ByVerdict(Verdict.Infected), requests.request.value)

        compose.onNodeWithContentDescription(text(R.string.home_see_all)).performClick()
        assertEquals(2, history)
        compose.onNodeWithContentDescription("Notes: Tree 0", substring = true).performScrollTo().performClick()
        assertTrue(opened != null)
    }

    @Test
    fun learnListsThreeTopicsAndOpensOne() {
        var topic: LearnTopic? = null
        compose.setContent { YoloCTheme { LearnScreen(onOpenTopic = { topic = it }) } }
        compose.onNodeWithContentDescription(text(R.string.learn_topic_photos_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.learn_topic_how_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.learn_topic_stages_title)).performClick()
        assertEquals(LearnTopic.Stages, topic)
    }

    @Test
    fun stagesComeFromTheClassesTable() {
        val names = runBlocking { classes.listClasses().sortedBy { it.severity }.map { it.displayName } }
        val vm = LearnTopicViewModel("stages", classes)
        compose.setContent {
            YoloCTheme { LearnTopicScreen(onBack = {}, onOpenAbout = {}, onOpenSettings = {}, vm = vm) }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(names.first()).fetchSemanticsNodes().isNotEmpty() }
        for (name in names) compose.onNodeWithContentDescription(name).assertExists()
    }

    @Test
    fun howItWorksLinksToAboutAndSettings() {
        var about = 0
        var settings = 0
        val vm = LearnTopicViewModel("how", classes)
        compose.setContent {
            YoloCTheme {
                LearnTopicScreen(onBack = {}, onOpenAbout = { about++ }, onOpenSettings = { settings++ }, vm = vm)
            }
        }
        compose.onNodeWithText(text(R.string.learn_how_trust_body, 4)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.learn_see_accuracy)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.learn_threshold_link)).performScrollTo().performClick()
        assertEquals(1, about)
        assertEquals(1, settings)
    }

    @Test
    fun anUnknownTopicSaysSoAndGoesBack() {
        var back = 0
        val vm = LearnTopicViewModel("nope", classes)
        compose.setContent {
            YoloCTheme { LearnTopicScreen(onBack = { back++ }, onOpenAbout = {}, onOpenSettings = {}, vm = vm) }
        }
        compose.onNodeWithContentDescription(text(R.string.error_back)).performClick()
        assertEquals(1, back)
    }
}
