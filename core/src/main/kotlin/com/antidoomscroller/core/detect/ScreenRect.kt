package com.antidoomscroller.core.detect

/**
 * An on-screen box, in absolute screen pixels.
 *
 * A plain value type rather than `android.graphics.Rect` so region maths stays in the core module
 * and can be tested without a device.
 */
data class ScreenRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height.toLong()
    val isEmpty: Boolean get() = width == 0 || height == 0

    fun intersect(other: ScreenRect): ScreenRect {
        val result = ScreenRect(
            left = maxOf(left, other.left),
            top = maxOf(top, other.top),
            right = minOf(right, other.right),
            bottom = minOf(bottom, other.bottom),
        )
        return if (result.width <= 0 || result.height <= 0) EMPTY else result
    }

    fun overlaps(other: ScreenRect): Boolean = !intersect(other).isEmpty

    companion object {
        val EMPTY = ScreenRect(0, 0, 0, 0)
    }
}
