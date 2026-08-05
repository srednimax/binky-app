package app.binky.tracker.ui.documents

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import kotlin.math.abs

/** How far in a document may be zoomed. Four times is enough to read small print on a phone. */
private const val MAX_SCALE = 4f

/** Double-tap toggles between fitting the page and this — a reading zoom, not the maximum. */
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * One page of a document, **pinch-zoomable**.
 *
 * The photo pager did not need this and this does: the entire value of a document is legible small
 * print, and a batch number on a vaccination card rendered at phone width is a smudge. Zoom is also
 * what the document media spec's 3000 px at q92 is *for* (ADR-0020) — without somewhere to spend
 * those pixels the spec would be bytes nobody can see.
 *
 * **Its second job is not stealing the pager's swipe**, which is why the gesture loop is written by
 * hand rather than using `detectTransformGestures`: that helper consumes every drag past touch
 * slop, which would leave a flat document with no way to reach page two. The rule here instead:
 *
 * - one finger on an unzoomed page — left unconsumed, so the pager swipes as it always did;
 * - two fingers, or any drag while zoomed in — this handles it and consumes;
 * - a drag that has run the page against its own edge — handed back, so swiping past the right edge
 *   of a zoomed page still turns it rather than dead-ending.
 */
@Composable
fun ZoomablePage(
    model: Any?,
    contentDescription: String?,
    placeholder: Painter,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            // In and back out on a double tap. A zoom that can only be undone by
                            // pinching is one an owner holding a rabbit in the other hand cannot
                            // undo.
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = DOUBLE_TAP_SCALE
                                }
                            },
                        )
                    }.pointerInput(Unit) {
                        awaitEachGesture {
                            // `requireUnconsumed = false`: the pager may already have seen this
                            // down event, and refusing to start on that basis would make the first
                            // pinch after a page turn do nothing.
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.count { it.pressed }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()

                                val ours = fingers > 1 || scale > 1f
                                if (ours) {
                                    val nextScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                                    if (nextScale == 1f) {
                                        // Zooming back out re-centres, so a page cannot be left
                                        // parked off-screen with nothing on it left to drag back.
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        val maxX = (widthPx * (nextScale - 1f) / 2f).coerceAtLeast(0f)
                                        val maxY = (heightPx * (nextScale - 1f) / 2f).coerceAtLeast(0f)
                                        val wantedX = offsetX + pan.x
                                        val clampedX = wantedX.coerceIn(-maxX, maxX)
                                        // At the edge, with one finger dragging sideways, this is
                                        // the pager's gesture rather than ours — so it is neither
                                        // applied nor consumed below.
                                        val atEdge =
                                            fingers == 1 &&
                                                abs(pan.x) > abs(pan.y) &&
                                                clampedX != wantedX
                                        if (atEdge) {
                                            scale = nextScale
                                            continue
                                        }
                                        offsetX = clampedX
                                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                    }
                                    scale = nextScale
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                // Fit, not Crop: a document is read whole before it is read closely.
                contentScale = ContentScale.Fit,
                // **Missing media renders as a placeholder, never a crash** (house rule): a restore
                // may legitimately lack the images (ADR-0005).
                error = placeholder,
                fallback = placeholder,
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )
        }
    }
}
