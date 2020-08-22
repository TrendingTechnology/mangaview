package jp.co.c_lis.mangaview.widget

import jp.co.c_lis.mangaview.Rectangle

abstract class PageLayout {

    val position = Rectangle()

    val scrollArea = Rectangle()

    abstract val isFilled: Boolean

    abstract fun add(page: Page)

    abstract val pages: List<Page>

    open fun flip(): PageLayout {
        return this
    }

    abstract fun initScrollArea()

    private var cachedScaledScrollAreaScale = 1.0F
    private val cachedScaledScrollArea = Rectangle()

    fun getScaledScrollArea(scale: Float): Rectangle {
        if (cachedScaledScrollAreaScale == scale) {
            return cachedScaledScrollArea
        }

        return calcScrollArea(cachedScaledScrollArea, scale).also {
            cachedScaledScrollAreaScale = scale
        }
    }

    abstract fun calcScrollArea(rectangle: Rectangle, scale: Float): Rectangle
}
