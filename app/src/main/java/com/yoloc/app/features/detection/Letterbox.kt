package com.yoloc.app.features.detection

/**
 * Letterboxing exactly as Ultralytics does it for non-PyTorch models (auto=False, scaleup=True):
 * scale to fit `size` keeping aspect ratio, then pad to a square. Mirrors
 * ultralytics/data/augment.py LetterBox and ultralytics/utils/ops.py scale_boxes, including
 * Python's round-half-to-even, so boxes line up with the Python reference to the pixel.
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
 * Maps a box from model-input pixels back to original-image pixels, clipped to the image.
 * Like scale_boxes, the offset is recomputed from the exact gain rather than the rounded size.
 */
fun toOriginalPixels(box: PixelBox, lb: Letterbox): PixelBox {
    val padX = pythonRound((lb.size - lb.srcWidth * lb.scale) / 2 - 0.1)
    val padY = pythonRound((lb.size - lb.srcHeight * lb.scale) / 2 - 0.1)
    fun clip(v: Double, max: Int) = v.coerceIn(0.0, max.toDouble())
    return PixelBox(
        clip((box.x1 - padX) / lb.scale, lb.srcWidth),
        clip((box.y1 - padY) / lb.scale, lb.srcHeight),
        clip((box.x2 - padX) / lb.scale, lb.srcWidth),
        clip((box.y2 - padY) / lb.scale, lb.srcHeight),
    )
}

/** Inverse of [toOriginalPixels] (no clipping); used by tests and for drawing in input space. */
fun toInputPixels(box: PixelBox, lb: Letterbox): PixelBox {
    val padX = pythonRound((lb.size - lb.srcWidth * lb.scale) / 2 - 0.1)
    val padY = pythonRound((lb.size - lb.srcHeight * lb.scale) / 2 - 0.1)
    return PixelBox(
        box.x1 * lb.scale + padX,
        box.y1 * lb.scale + padY,
        box.x2 * lb.scale + padX,
        box.y2 * lb.scale + padY,
    )
}
