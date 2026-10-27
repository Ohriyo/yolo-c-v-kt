package com.yoloc.app.features.dev

import com.yoloc.app.db.repositories.NewDetection
import com.yoloc.app.db.repositories.ScanSource
import com.yoloc.app.features.scans.SaveScanInput
import java.io.File
import kotlin.math.floor

/** RN: dev/fakeScans.ts. Every fake scan's notes start with this, so they can be found and removed. */
const val FAKE_SCAN_TAG = "[dev seed]"

/** Fake plots are named with this prefix. */
const val FAKE_PLOT_PREFIX = "[dev]"

private const val DAY_MS = 24 * 60 * 60 * 1000.0

data class FakeScanOptions(
    val count: Int,
    val now: Long,
    val modelVersionId: String,
    /** Class ids in model order; the generator picks among them. */
    val classIds: List<Int>,
    /** Plot ids to spread scans over; null means "no plot". */
    val plotIds: List<String?>,
    val imageWidth: Int,
    val imageHeight: Int,
    val confThreshold: Double,
    val iouThreshold: Double,
    /** Same seed, same scans: makes History screenshots and tests repeatable. */
    val seed: Long = 42,
    /** Spread over this many days before `now`. */
    val days: Int = 60,
)

/**
 * Fake scans that all use `sourceImage` (RN leaves the image to the caller). The random draws
 * happen in exactly RN's order, so the same seed gives the same scans in both apps.
 */
fun buildFakeScans(o: FakeScanOptions, sourceImage: File): List<SaveScanInput> {
    val random = Mulberry32(o.seed)
    fun <T> pick(items: List<T>): T = items[floor(random.next() * items.size).toInt()]

    return List(o.count) { i ->
        // About 1 in 7 scans has no pods, to exercise the "No cacao pods found" state.
        val podCount = if (random.next() < 0.15) 0 else 1 + floor(random.next() * 5).toInt()
        val detections = List(podCount) {
            val w = 0.1 + random.next() * 0.3
            val h = 0.15 + random.next() * 0.35
            val classId = pick(o.classIds)
            val confidence = round4(o.confThreshold + random.next() * (0.99 - o.confThreshold))
            val x = round4(random.next() * (1 - w))
            val y = round4(random.next() * (1 - h))
            NewDetection(classId, confidence, x, y, round4(w), round4(h))
        }
        val source = if (random.next() < 0.6) ScanSource.Camera else ScanSource.Gallery
        val plotId = pick(o.plotIds)
        val inferenceMs = 400 + floor(random.next() * 1800).toInt()
        // Newest first in creation order, spaced across the range with some jitter.
        val createdAt = Math.round(o.now - i.toDouble() / o.count * o.days * DAY_MS - random.next() * DAY_MS * 0.5)
        SaveScanInput(
            sourceImage = sourceImage,
            plotId = plotId,
            modelVersionId = o.modelVersionId,
            imageWidth = o.imageWidth,
            imageHeight = o.imageHeight,
            source = source,
            confThreshold = o.confThreshold,
            iouThreshold = o.iouThreshold,
            inferenceMs = inferenceMs,
            notes = "$FAKE_SCAN_TAG Fake scan ${i + 1}",
            createdAt = createdAt,
            detections = detections,
        )
    }
}

/** JS Math.round(v * 10000) / 10000 (Java's Math.round also rounds halves up). */
private fun round4(v: Double) = Math.round(v * 10000) / 10000.0

/**
 * Tiny seeded PRNG (mulberry32); deterministic across platforms. Int arithmetic wraps like JS's
 * `>>> 0` and Math.imul, and `ushr` is JS's `>>>`, so the sequence matches RN's bit for bit.
 */
internal class Mulberry32(seed: Long) {
    private var a = seed.toInt() // seed >>> 0 keeps the low 32 bits, as toInt() does

    fun next(): Double {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }
}
