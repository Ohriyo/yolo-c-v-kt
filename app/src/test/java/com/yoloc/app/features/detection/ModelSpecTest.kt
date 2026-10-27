package com.yoloc.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** RN: __tests__/detection.math.test.ts (describeModel). */
class ModelSpecTest {
    private val config = TestAssets.modelConfig

    private fun f32(vararg shape: Int) = TensorInfo("t", "float32", shape.toList())

    @Test
    fun readsTheExportedModelNchwInputAndFeaturesFirstOutput() {
        val spec = describeModel(listOf(f32(1, 3, 640, 640)), listOf(f32(1, 7, 8400)), config)
        assertEquals(InputLayout.NCHW, spec.inputLayout)
        assertEquals(640, spec.inputSize)
        assertEquals(OutputLayout.FeaturesFirst, spec.outputLayout)
        assertEquals(3, spec.numClasses)
        assertEquals(8400, spec.numAnchors)
    }

    @Test
    fun supportsNhwcInputAndTransposedOutput() {
        val spec = describeModel(listOf(f32(1, 640, 640, 3)), listOf(f32(1, 8400, 7)), config)
        assertEquals(InputLayout.NHWC, spec.inputLayout)
        assertEquals(OutputLayout.AnchorsFirst, spec.outputLayout)
    }

    @Test
    fun failsClearlyWhenTheModelDoesNotMatchTheConfig() {
        fun messageOf(inputs: List<TensorInfo>, outputs: List<TensorInfo>) =
            assertThrows(IllegalArgumentException::class.java) { describeModel(inputs, outputs, config) }.message!!

        assertTrue(messageOf(listOf(f32(1, 3, 320, 320)), listOf(f32(1, 7, 2100))).contains("inputSize 640"))
        assertTrue(messageOf(listOf(f32(1, 3, 640, 640)), listOf(f32(1, 8, 8400))).contains("3 classes"))
        assertTrue(
            messageOf(listOf(TensorInfo("q", "uint8", listOf(1, 640, 640, 3))), listOf(f32(1, 7, 8400)))
                .contains("float32"),
        )
    }
}
