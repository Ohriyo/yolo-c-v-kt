package com.yoloc.app.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yoloc.app.screens.dev.GalleryScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** RN: gallery.test.tsx. Every core component renders, with a sample of states. */
@RunWith(AndroidJUnit4::class)
class GalleryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val coreComponents = listOf(
        "Typography", "Color", "Button", "Card", "StatusChip", "VerdictLine", "ReadoutStrip", "DetectionListItem",
        "ImageWithBoxes", "EmptyState", "ErrorState", "BottomSheet", "Toast", "SectionHeader", "SegmentedControl", "TabBar",
    )

    @Test
    fun rendersEveryCoreComponentWithASampleOfStates() {
        compose.setContent { GalleryScreen() }
        compose.onNodeWithText("Component gallery").assertExists()
        for (component in coreComponents) compose.onNodeWithText(component).performScrollTo().assertExists()
        compose.onNodeWithText("Uncertain result: retake in better light", substring = true, useUnmergedTree = true).assertExists()
        // Disabled and loading states for each of the three button variants.
        val disabled = SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)
        assertEquals(3, compose.onAllNodes(hasContentDescription("Disabled") and disabled).fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodes(hasContentDescription("Saving") and disabled).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("Component gallery").fetchSemanticsNodes().size)
    }
}
