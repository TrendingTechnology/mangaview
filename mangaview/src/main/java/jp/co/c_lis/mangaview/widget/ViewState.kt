package jp.co.c_lis.mangaview.widget

import jp.co.c_lis.mangaview.Rectangle
import kotlin.math.max
import kotlin.math.min

data class ViewState(
    internal var viewWidth: Float = 0.0F,
    internal var viewHeight: Float = 0.0F,
    internal var currentX: Float = 0.0F,
    internal var currentY: Float = 0.0F,
    internal var currentScale: Float = 1.0F,
    internal val viewport: Rectangle = Rectangle(0.0F, 0.0F, viewWidth, viewHeight)
) {

    companion object {
        private val TAG = ViewState::class.java.simpleName
    }

    var minScale = 1.0F
    var maxScale = 5.0F

    val scaledWidth: Float
        get() = viewWidth / currentScale

    val scaledHeight: Float
        get() = viewHeight / currentScale

    fun setViewSize(w: Int, h: Int) {
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()

        viewport.left = 0.0F
        viewport.top = 0.0F
        viewport.right = viewWidth
        viewport.bottom = viewHeight
    }

    private fun validate(): Boolean {
        var result = true

        viewport.set(
            currentX,
            currentY,
            currentX + scaledWidth,
            currentY + scaledHeight
        )

        return result
    }

    fun scroll(
        distanceX: Float,
        distanceY: Float
    ): Boolean = offset(distanceX, distanceY)

    fun offset(
        offsetX: Float,
        offsetY: Float
    ) = offsetTo(currentX + offsetX, currentY + offsetY)

    fun offsetXTo(x: Float): Boolean {
        currentX = x
        return validate()
    }

    fun offsetYTo(y: Float): Boolean {
        currentY = y
        return validate()
    }

    fun offsetTo(x: Float, y: Float): Boolean {
        currentX = x
        currentY = y

        return validate()
    }

    fun scale(factor: Float, focusX: Float, focusY: Float): Boolean {
        val scale = currentScale * factor

        val result = scaleTo(
            scale,
            focusX,
            focusY
        )

        return result
    }

    fun scaleTo(
        scale: Float,
        focusX: Float,
        focusY: Float
    ): Boolean {
        val newScale = max(min(scale, maxScale), minScale)
        if (currentScale == newScale) {
            return false
        }

        val focusXRatio = focusX / viewWidth
        val focusYRatio = focusY / viewHeight

        val newViewportWidth = viewWidth / newScale
        val newViewportHeight = viewHeight / newScale

        val diffX = scaledWidth - newViewportWidth
        val diffY = scaledHeight - newViewportHeight

        offset(diffX * focusXRatio, diffY * focusYRatio)

        currentScale = newScale

        return validate()
    }

    fun canScrollLeft(rectangle: Rectangle, delta: Float = 0.0F): Boolean {
        return (rectangle.left - delta) < viewport.left
    }

    fun canScrollRight(rectangle: Rectangle, delta: Float = 0.0F): Boolean {
        return (rectangle.right + delta) > viewport.right
    }

    fun canScrollTop(rectangle: Rectangle, delta: Float = 0.0F): Boolean {
        return (rectangle.top - delta) < viewport.top
    }

    fun canScrollBottom(rectangle: Rectangle, delta: Float = 0.0F): Boolean {
        return (rectangle.bottom + delta) > viewport.bottom
    }
}