package dev.keiji.mangaview.widget

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class VerticalLayoutManager : LayoutManager() {

    companion object {
        private val TAG = VerticalLayoutManager::class.java.simpleName
    }

    override val populateHelper: PopulateHelper = VerticalPopulateHelper()

    override val initialScrollX: Float
        get() = 0.0F

    override val initialScrollY: Float
        get() = 0.0F

    override fun currentPageLayoutIndex(
        viewContext: ViewContext
    ): Int = abs(viewContext.viewport.centerY / viewContext.viewHeight).toInt()

    override fun topPageLayout(viewContext: ViewContext): PageLayout? {
        val topIndex = currentPageLayoutIndex(viewContext) - 1
        if (topIndex < 0) {
            return null
        }
        return getPageLayout(topIndex, viewContext)

    }

    override fun bottomPageLayout(viewContext: ViewContext): PageLayout? {
        val bottomIndex = currentPageLayoutIndex(viewContext) + 1
        if (bottomIndex >= pageLayoutManager.getCount()) {
            return null
        }
        return getPageLayout(bottomIndex, viewContext)
    }

    override fun layout(index: Int, pageLayout: PageLayout, viewContext: ViewContext): PageLayout {
        val positionTop = viewContext.viewHeight * index

        pageLayout.globalPosition.also {
            it.left = 0.0F
            it.right = viewContext.viewWidth
            it.top = positionTop
            it.bottom = it.top + viewContext.viewHeight
        }

        return pageLayout
    }

    override fun calcFirstVisiblePageLayoutIndex(viewContext: ViewContext): Int {
        return abs(floor(viewContext.viewport.top / viewContext.viewHeight)).toInt()
    }

    override fun calcLastVisiblePageLayoutIndex(viewContext: ViewContext): Int {
        return abs(ceil(viewContext.viewport.top / viewContext.viewHeight)).toInt()
    }

    override fun setScrollableAxis(viewContext: ViewContext) {
        viewContext.setScrollableAxis(
            horizontal = ViewContext.SCROLL_POLICY_LIMIT,
            vertical = ViewContext.SCROLL_POLICY_UNLIMITED
        )
    }
}

