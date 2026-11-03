package com.yoloc.app.features.dev

import android.content.Context
import com.yoloc.app.features.detection.ReferenceImage
import java.io.File

/** Which Python run a reference came from. */
enum class ReferenceSource(val asset: String) {
    Tflite("reference/expected_tflite.json"),
    Pt("reference/expected_pt.json"),
}

sealed interface GoldenReference {
    data object Missing : GoldenReference
    data class Found(val image: ReferenceImage) : GoldenReference
}

/**
 * Golden-test images and reference detections from Python. Debug only: the files live in
 * app/src/androidTest/assets/reference, which only debug builds also package (app/build.gradle.kts).
 */
class GoldenSamples(private val context: Context) {
    private val references = mutableMapOf<ReferenceSource, List<ReferenceImage>>()

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
        val images = synchronized(references) {
            references.getOrPut(source) {
                context.assets.open(source.asset).use { ReferenceImage.parseAll(it.readBytes().decodeToString()) }
            }
        }
        return images.find { it.image == file }?.let { GoldenReference.Found(it) } ?: GoldenReference.Missing
    }

    companion object {
        const val GOLDEN_DIR = "reference/images"

        /** The confidence threshold, for every class, that the reference files were generated at. */
        const val REFERENCE_CONF = 0.25

        val FILES = listOf(
            "1-s2-0-S0261219417300200-gr21_jpg.rf.d41976294c816b12e4a3269547738fe9.jpg",
            "11_png.rf.20438d470ad521eee1b47ed5731e4c6d.jpg",
            "1515671450y_pod_rot_cacao_Mycologia_alme_et_al_jpg.rf.08b8f1bca148f47916ded74e31cc895c.jpg",
            "1728667862952_jpg.rf.06b99a9398a987016c3927fe2efb13f9.jpg",
            "20241011_105617_jpg.rf.4a8fb93987c3dc4419a2e787e3ed6510.jpg",
            "IMG_20241010_141553704_HDR_jpg.rf.0dbc43f20db1615d52d33f3883a446a3.jpg",
            "1b6d3dd979b5d3193270d803634494e6_jpg.rf.329c6bb70280b490d9cd93bc5e827280.jpg",
            "IMG_1629.JPG",
            "IMG_1631.JPG",
        )
    }
}
