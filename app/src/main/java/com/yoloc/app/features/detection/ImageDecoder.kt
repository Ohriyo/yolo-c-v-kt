package com.yoloc.app.features.detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Full-resolution bitmap with EXIF rotation/flip applied (RN: ImageManipulator.renderAsync), so
 * the caller can compute the letterbox from the upright size before resizing. `sampleSize` > 1
 * decodes at 1/n size (thumbnails); detection always decodes at full size.
 */
fun decodeOriented(file: File, sampleSize: Int = 1): Bitmap {
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val raw = BitmapFactory.decodeFile(file.path, options) ?: error("Could not decode ${file.name}")
    val exif = ExifInterface(file)
    val matrix = Matrix().apply {
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }
    return if (matrix.isIdentity) {
        raw
    } else {
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { raw.recycle() }
    }
}

/**
 * Resizes to exactly width x height (bilinear) and reads the pixels. RN resizes natively, then
 * re-encodes a JPEG and decodes it with jpeg-js; reading the bitmap directly skips that lossy,
 * slow round trip, so golden deltas and preprocess time differ slightly from RN.
 */
fun resizedPixels(source: Bitmap, width: Int, height: Int): DecodedImage {
    val scaled = source.scale(width, height) // Bitmap.createScaledBitmap, filter = true (bilinear)
    val argb = IntArray(width * height)
    scaled.getPixels(argb, 0, width, 0, 0, width, height)
    if (scaled !== source) scaled.recycle()
    return DecodedImage(width, height, argb)
}
