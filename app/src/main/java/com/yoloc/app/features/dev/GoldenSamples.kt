package com.yoloc.app.features.dev

import android.content.Context
import com.yoloc.app.features.detection.ExpectedFile
import com.yoloc.app.features.detection.ExpectedImage
import java.io.File

/** Which Python run a reference came from (RN: expected_tflite.json / expected_pt.json). */
enum class ReferenceSource(val asset: String) {
    Tflite("sample/expected_tflite.json"),
    Pt("sample/expected_pt.json"),
}

sealed interface GoldenReference {
    /** The reference file is a placeholder until the real Python output is added. */
    data object Pending : GoldenReference
    data object Missing : GoldenReference
    data class Found(val image: ExpectedImage) : GoldenReference
}

/**
 * Golden-test images and reference detections from Python. Debug only: the files live in
 * app/src/debug/assets/sample, so a release build has none of them.
 */
class GoldenSamples(private val context: Context) {
    private val references = mutableMapOf<ReferenceSource, ExpectedFile>()

    /**
     * The runner takes a file (as RN's runDetection takes a URI), so copy the asset into the cache
     * once. RN: Asset.loadAsync gives a local file the same way.
     */
    fun file(name: String): File {
        val dest = File(context.cacheDir, "golden/$name")
        if (!dest.exists()) {
            dest.parentFile?.mkdirs()
            context.assets.open("$GOLDEN_DIR/$name").use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }
        return dest
    }

    fun reference(source: ReferenceSource, file: String): GoldenReference {
        val data = synchronized(references) {
            references.getOrPut(source) {
                context.assets.open(source.asset).use { ExpectedFile.parse(it.readBytes().decodeToString()) }
            }
        }
        if (data.pending == true) return GoldenReference.Pending
        return data.images.find { it.file == file }?.let { GoldenReference.Found(it) } ?: GoldenReference.Missing
    }

    companion object {
        const val GOLDEN_DIR = "sample/golden"
        val FILES = listOf("golden_01.jpg", "golden_02.jpg", "golden_03.jpg", "golden_04.jpg", "golden_05.jpg")
    }
}
