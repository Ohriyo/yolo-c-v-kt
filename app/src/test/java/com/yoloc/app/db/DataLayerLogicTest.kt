package com.yoloc.app.db

import com.yoloc.app.db.repositories.SettingCodecs
import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.db.repositories.escapeLike
import com.yoloc.app.db.repositories.parseActions
import com.yoloc.app.features.scans.sampleSizeFor
import com.yoloc.app.util.toJsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure helpers of the data layer that run on the JVM; the Room tests are in androidTest. */
class DataLayerLogicTest {
    @Test
    fun printsNumbersLikeJavaScript() {
        assertEquals("7", 7.0.toJsString())
        assertEquals("0.25", 0.25.toJsString())
        assertEquals("-1.25", (-1.25).toJsString())
        assertEquals("0.0001", 0.0001.toJsString())
        assertEquals("0", 0.0.toJsString())
    }

    @Test
    fun fingerprintIsFnv1aLikeSeedTs() {
        // Standard FNV-1a 32-bit test vectors.
        assertEquals("811c9dc5", fingerprintOf(""))
        assertEquals("1a47e90b", fingerprintOf("abc"))
    }

    @Test
    fun parsesRecommendedActionsAndFallsBackToEmpty() {
        assertEquals(listOf("a", "b"), parseActions("""["a","b"]"""))
        assertEquals(listOf("a"), parseActions("""["a",1,null]"""))
        assertEquals(emptyList<String>(), parseActions("not json"))
        assertEquals(emptyList<String>(), parseActions("""{"a":1}"""))
        assertEquals(emptyList<String>(), parseActions(null))
    }

    @Test
    fun escapesLikeWildcards() {
        assertEquals("""100\%""", escapeLike("100%"))
        assertEquals("""a\_b\\c""", escapeLike("""a_b\c"""))
    }

    @Test
    fun settingCodecsUseRnKeysFormatsAndFallbacks() {
        assertEquals("onboarding_done", SettingCodecs.onboardingDone.key)
        assertTrue(SettingCodecs.onboardingDone.parse("1"))
        assertFalse(SettingCodecs.onboardingDone.parse("0"))
        assertEquals("1", SettingCodecs.onboardingDone.format(true))
        assertEquals(0.25, SettingCodecs.confThreshold.parse(null), 0.0)
        assertEquals(0.25, SettingCodecs.confThreshold.parse("abc"), 0.0)
        assertEquals("0.5", SettingCodecs.confThreshold.format(0.5))
        assertEquals(ThemeSetting.Dark, SettingCodecs.theme.parse("dark"))
        assertEquals(ThemeSetting.System, SettingCodecs.theme.parse("purple"))
        assertTrue(SettingCodecs.keepOriginalImages.parse(null))
        assertEquals("", SettingCodecs.activeModelId.parse(null))
    }

    @Test
    fun thumbnailSampleSizeKeepsTheShortSideAtLeast240() {
        assertEquals(1, sampleSizeFor(240))
        assertEquals(1, sampleSizeFor(479))
        assertEquals(2, sampleSizeFor(480))
        assertEquals(8, sampleSizeFor(3000))
        assertEquals(1, sampleSizeFor(100))
    }
}
