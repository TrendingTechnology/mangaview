package dev.keiji.mangaview.widget

import dev.keiji.mangaview.Rectangle

abstract class PageLayout {

    val globalPosition = Rectangle()

    val scrollArea = Rectangle()

    abstract val isFilled: Boolean

    abstract fun add(page: Page)

    abstract val pages: List<Page>

    open fun flip(): PageLayout {
        return this
    }

    abstract fun initScrollArea()

    private var cachedScaledScrollAreaScale: Float? = null
    private val cachedScaledScrollArea = Rectangle()

    fun getScaledScrollArea(viewContext: ViewContext): Rectangle {
        val scale = viewContext.currentScale

        if (cachedScaledScrollAreaScale == scale) {
            return cachedScaledScrollArea
        }

        return calcScrollArea(cachedScaledScrollArea, viewContext).also {
            cachedScaledScrollAreaScale = scale
        }
    }

    abstract fun calcScrollArea(rectangle: Rectangle, viewContext: ViewContext): Rectangle
}
