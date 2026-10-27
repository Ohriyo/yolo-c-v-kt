package com.yoloc.app.features.dev

import com.yoloc.app.db.repositories.ScanSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The fake-scan generator must match RN's for the same seed. Expected values were printed by RN's
 * fakeScans.ts (mulberry32 and buildFakeScans) under Node.
 */
class FakeScansTest {
    @Test
    fun mulberry32MatchesTheJsSequence() {
        val r = Mulberry32(42)
        assertEquals(0.6011037519201636, r.next(), 0.0)
        assertEquals(0.44829055899754167, r.next(), 0.0)
        assertEquals(0.8524657934904099, r.next(), 0.0)
        // A Date.now()-sized seed keeps only its low 32 bits, like `seed >>> 0`.
        val big = Mulberry32(1_792_227_600_000)
        assertEquals(0.17004980612546206, big.next(), 0.0)
        assertEquals(0.03875113697722554, big.next(), 0.0)
    }

    @Test
    fun buildsTheSameScansAsRnForTheSameSeed() {
        val image = File("/cache/sample.jpg")
        val scans = buildFakeScans(
            FakeScanOptions(
                count = 3, now = 1_792_227_600_000, modelVersionId = "m", classIds = listOf(0, 1, 2),
                plotIds = listOf("p1", "p2", null), imageWidth = 365, imageHeight = 547,
                confThreshold = 0.25, iouThreshold = 0.45, seed = 7,
            ),
            image,
        )
        assertEquals(listOf(null, "p2", "p1"), scans.map { it.plotId })
        assertEquals(listOf(ScanSource.Camera, ScanSource.Camera, ScanSource.Gallery), scans.map { it.source })
        assertEquals(listOf(1658, 1771, 1041), scans.map { it.inferenceMs })
        assertEquals(listOf(1_792_205_073_564, 1_790_496_380_819, 1_788_765_604_220), scans.map { it.createdAt })
        assertEquals(listOf(0, 3, 3), scans.map { it.detections.size })
        assertEquals(
            listOf(
                "2 0.4408 0.1291 0.5015 0.172 0.3437",
                "1 0.467 0.3981 0.2314 0.2555 0.219",
                "2 0.3912 0.0871 0.118 0.3934 0.2366",
                "2 0.712 0.6573 0.5065 0.2865 0.388",
                "1 0.5647 0.0179 0.0695 0.112 0.2112",
                "1 0.5968 0.6336 0.507 0.3304 0.2791",
            ),
            scans.flatMap { s -> s.detections.map { "${it.classId} ${it.confidence} ${it.x} ${it.y} ${it.w} ${it.h}" } },
        )
        assertEquals(listOf("[dev seed] Fake scan 1", "[dev seed] Fake scan 2", "[dev seed] Fake scan 3"), scans.map { it.notes })
        assertTrue(scans.all { it.sourceImage == image && it.imageWidth == 365 && it.imageHeight == 547 })
    }
}
