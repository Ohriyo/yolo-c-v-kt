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
        for (t in config.confThresholds.asList() + config.iouThreshold) assertTrue("$t", t > 0 && t < 1)
    }

    @Test
    fun hasAPositiveInputSizeAValidPadValueAndATfliteFileName() {
        assertTrue(config.inputSize > 0)
        assertTrue(config.letterboxPad in 0..255)
        assertTrue(config.file.endsWith(".tflite"))
    }

    @Test
    fun namesAModelFileThatExists() {
        assertTrue(TestAssets.main(config.file).exists())
    }

    @Test
    fun hasAnUncertainLimitBetweenZeroAndOne() {
        assertTrue(config.uncertainBelow > 0 && config.uncertainBelow < 1)
    }

    @Test
    fun carriesV3sValidationMetricsAndNoTestMetricsYet() {
        val validation = config.metrics.validation!!
        assertEquals("validation (243 images)", validation.label)
        assertEquals(ModelConfig.Scores(0.766, 0.682, 0.740, 0.635), validation.all)
        assertEquals(ModelConfig.Scores(0.632, 0.608, 0.609, 0.535), validation.classes["early_stage"])
        assertEquals(ModelConfig.Scores(0.839, 0.802, 0.863, 0.765), validation.classes["fully_infected_pod"])
        assertEquals(ModelConfig.Scores(0.827, 0.635, 0.747, 0.605), validation.classes["healthy_pod"])
        assertEquals(null, config.metrics.test)
        assertEquals(0.608, config.metrics.earlyStageRecall!!, 0.0)
    }
}
