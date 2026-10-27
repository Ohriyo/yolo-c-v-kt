package com.yoloc.app.features.camera

import kotlin.math.floor
import kotlin.math.min

/**
 * Width / height of the viewfinder. Phone sensors take 4:3 photos, held upright that is 3:4, so
 * the frame shows the same field of view the photo (and the model) gets.
 */
const val VIEWFINDER_ASPECT = 3f / 4f

data class FrameSize(val width: Float, val height: Float)

/** RN: viewfinderMath.ts fitAspect. The largest `aspect` (width / height) rectangle that fits. */
fun fitAspect(availableWidth: Float, availableHeight: Float, aspect: Float): FrameSize {
    val w = maxOf(0f, availableWidth)
    val h = maxOf(0f, availableHeight)
    if (w == 0f || h == 0f || !(aspect > 0f)) return FrameSize(0f, 0f)
    val width = min(w, h * aspect)
    return FrameSize(floor(width), floor(width / aspect))
}
