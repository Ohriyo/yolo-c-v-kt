package com.yoloc.app.features.detection

/**
 * Letterboxing exactly as Ultralytics does it for non-PyTorch models (auto=False, scaleup=True):
 * scale to fit `size` keeping aspect ratio, then pad to a square. Mirrors
 * ultralytics/data/augment.py LetterBox, including Python's round-half-to-even.
 */
data class Letterbox(
    /** Original image size. */
    val srcWidth: Int,
    val srcHeight: Int,
    /** Square model input size. */
    val size: Int,
    /** Scale factor original -> input (Ultralytics "gain"). */
    val scale: Double,
    /** Size of the scaled image inside the square, before padding. */
    val resizedWidth: Int,
    val resizedHeight: Int,
    /** Padding added on the left and top, in input pixels. */
    val padLeft: Int,
    val padTop: Int,
)

/** Corner box in pixels. */
data class PixelBox(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

/** Python 3 round(): halves go to the nearest even integer. kotlin.math.round already does this. */
fun pythonRound(value: Double): Int = kotlin.math.round(value).toInt()

fun computeLetterbox(srcWidth: Int, srcHeight: Int, size: Int): Letterbox {
    require(srcWidth > 0 && srcHeight > 0 && size > 0) {
        "Invalid letterbox sizes: ${srcWidth}x$srcHeight -> $size"
    }
    val scale = minOf(size.toDouble() / srcHeight, size.toDouble() / srcWidth)
    val resizedWidth = pythonRound(srcWidth * scale)
    val resizedHeight = pythonRound(srcHeight * scale)
    // LetterBox: top = round(dh - 0.1), left = round(dw - 0.1) where dw, dh are half the padding.
    val padLeft = pythonRound((size - resizedWidth) / 2.0 - 0.1)
    val padTop = pythonRound((size - resizedHeight) / 2.0 - 0.1)
    return Letterbox(srcWidth, srcHeight, size, scale, resizedWidth, resizedHeight, padLeft, padTop)
}

/**
 * Maps a box from model-input pixels to 0-1 on the original image, clamped: subtract the padding
 * that was actually applied, divide by the scale, then by the original size. Ultralytics'
 * scale_boxes recomputes the padding instead, which can differ by a pixel on non-square images.
 */
fun toNormalizedOriginal(box: PixelBox, lb: Letterbox): PixelBox {
    fun x(v: Double) = ((v - lb.padLeft) / lb.scale / lb.srcWidth).coerceIn(0.0, 1.0)
    fun y(v: Double) = ((v - lb.padTop) / lb.scale / lb.srcHeight).coerceIn(0.0, 1.0)
    return PixelBox(x(box.x1), y(box.y1), x(box.x2), y(box.y2))
}
