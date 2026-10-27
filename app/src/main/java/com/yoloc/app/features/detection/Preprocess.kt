package com.yoloc.app.features.detection

/** NCHW = [1, 3, H, W] (planar, channels first); NHWC = [1, H, W, 3] (interleaved). */
enum class InputLayout { NCHW, NHWC }

/**
 * 8-bit ARGB pixels as `Bitmap.getPixels` returns them, row-major. Unlike RN (bytes decoded by
 * jpeg-js), Kotlin reads the bitmap directly, so there is no RGB/RGBA channel count.
 */
class DecodedImage(val width: Int, val height: Int, val argb: IntArray)

/**
 * Builds the float32 model input: the already-resized image placed at the letterbox offset,
 * padding filled with `padValue` (114, as Ultralytics), every value divided by 255.
 * `image` must be exactly lb.resizedWidth x lb.resizedHeight.
 */
fun buildInputTensor(image: DecodedImage, lb: Letterbox, padValue: Int, layout: InputLayout): FloatArray {
    require(image.width == lb.resizedWidth && image.height == lb.resizedHeight) {
        "Resized image is ${image.width}x${image.height}, expected ${lb.resizedWidth}x${lb.resizedHeight}"
    }
    val size = lb.size
    val plane = size * size
    val out = FloatArray(plane * 3) { padValue / 255f }
    for (y in 0 until image.height) {
        val row = (y + lb.padTop) * size + lb.padLeft
        for (x in 0 until image.width) {
            val p = image.argb[y * image.width + x]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val pixel = row + x
            if (layout == InputLayout.NCHW) {
                out[pixel] = r
                out[plane + pixel] = g
                out[2 * plane + pixel] = b
            } else {
                val o = pixel * 3
                out[o] = r
                out[o + 1] = g
                out[o + 2] = b
            }
        }
    }
    return out
}
