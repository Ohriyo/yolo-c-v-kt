package com.yoloc.app.features.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yoloc.app.components.Icon
import com.yoloc.app.components.IconName
import com.yoloc.app.components.RecallPods
import com.yoloc.app.theme.Palette
import com.yoloc.app.theme.Radii
import com.yoloc.app.theme.Spacing
import com.yoloc.app.theme.YoloCTheme

/*
 * RN: features/onboarding/Illustrations.tsx. First-run illustrations, drawn from the palette's own
 * shapes (overlapping capsules, a cream disc) instead of image assets, so they stay crisp, offline
 * and theme-aware. All decorative: the frame hides them from screen readers.
 */

private val cream = Palette.cream100
private val forest = Palette.forest700
private val deep = Palette.forest900

/** A cream disc holding one icon, with two soft capsules stacked behind it. */
@Composable
fun DiscIllustration(icon: IconName, flip: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val w = maxWidth
        // RN: absolute capsules pinned 128 / 96 dp past the panel's right or left edge.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = if (flip) (-128).dp else w - 180.dp + 128.dp, y = 62.dp)
                .size(180.dp, 56.dp)
                .alpha(0.4f)
                .background(deep, RoundedCornerShape(Radii.full)),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = if (flip) w - 140.dp + 96.dp else (-96).dp, y = 92.dp)
                .size(140.dp, 56.dp)
                .alpha(0.6f)
                .background(forest, RoundedCornerShape(Radii.full)),
        )
        Box(Modifier.size(112.dp).background(cream, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, size = 48.dp, color = deep)
        }
    }
}

/** A viewfinder framing one upright pod under an umbrella of shade: "fill the frame, use shade". */
@Composable
fun ViewfinderIllustration() {
    Box(Modifier.size(150.dp, 180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val arm = 34.dp.toPx()
            val r = 14.dp.toPx() - stroke / 2
            val i = stroke / 2
            fun corner(path: Path) = drawPath(path, cream, style = Stroke(stroke))
            // Each corner: a line, a rounded turn, a line (RN: two borders with a corner radius).
            corner(Path().apply { moveTo(i, arm); lineTo(i, i + r); arcTo(Rect(i, i, i + 2 * r, i + 2 * r), 180f, 90f, false); lineTo(arm, i) })
            val w = size.width
            val h = size.height
            corner(Path().apply { moveTo(w - arm, i); lineTo(w - i - r, i); arcTo(Rect(w - i - 2 * r, i, w - i, i + 2 * r), 270f, 90f, false); lineTo(w - i, arm) })
            corner(Path().apply { moveTo(i, h - arm); lineTo(i, h - i - r); arcTo(Rect(i, h - i - 2 * r, i + 2 * r, h - i), 180f, -90f, false); lineTo(arm, h - i) })
            corner(Path().apply { moveTo(w - arm, h - i); lineTo(w - i - r, h - i); arcTo(Rect(w - i - 2 * r, h - i - 2 * r, w - i, h - i), 90f, -90f, false); lineTo(w - i, h - arm) })
        }
        Column(
            Modifier
                .size(64.dp, 128.dp)
                .background(cream, RoundedCornerShape(Radii.full))
                .border(3.dp, deep, RoundedCornerShape(Radii.full))
                .padding(top = Spacing.md, bottom = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .weight(1f)
                    .alpha(0.35f)
                    .background(deep, RoundedCornerShape(Radii.full)),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 150.dp, y = (-12).dp)
                .size(40.dp)
                .background(cream, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconName.Umbrella, size = 20.dp, color = deep)
        }
    }
}

/** Six of ten pods found: the recall number as something a farmer can count. */
@Composable
fun RecallIllustration(found: Int, label: String) {
    Box(
        Modifier
            .fillMaxWidth(0.86f)
            .background(YoloCTheme.colors.surfaceRaised, RoundedCornerShape(Radii.md))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        RecallPods(found, accessibilityLabel = label)
    }
}
