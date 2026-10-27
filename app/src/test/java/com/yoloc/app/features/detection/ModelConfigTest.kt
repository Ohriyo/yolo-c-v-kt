package com.yoloc.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RN: __tests__/modelConfig.test.ts. Parses the real asset file. */
class ModelConfigTest {
    private val config = TestAssets.modelConfig

    @Test
    fun listsTheThreeClassesInModelIndexOrder() {
        assertEquals(listOf("early_stage", "fully_infected_pod", "healthy_pod"), config.classes)
    }

    @Test
    fun hasThresholdsBetweenZeroAndOne() {
        for (t in listOf(config.confThreshold, config.iouThreshold)) assertTrue("$t", t > 0 && t < 1)
    }

    @Test
    fun hasAPositiveInputSizeAValidPadValueAndATfliteFileName() {
        assertTrue(config.inputSize > 0)
        assertTrue(config.letterboxPadValue in 0..255)
        assertTrue(config.file.endsWith(".tflite"))
    }

    @Test
    fun namesAModelFileThatExists() {
        assertTrue(TestAssets.main("models/${config.file}").exists())
    }

    @Test
    fun hasALowConfidenceThresholdBetweenZeroAndOne() {
        assertTrue(config.lowConfidenceThreshold > 0 && config.lowConfidenceThreshold < 1)
    }
}
