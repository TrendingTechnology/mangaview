package dev.keiji.mangaview.widget

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class HorizontalRtlLayoutManager : LayoutManager() {

    companion object {
        private val TAG = HorizontalRtlLayoutManager::class.java.simpleName
    }

    override val populateHelper: PopulateHelper = HorizontalPopulateHelper()

    override val initialScrollX: Float
        get() = -viewWidth.toFloat()

    override val initialScrollY: Float
        get() = 0.0F

    override fun currentPageLayoutIndex(
        viewContext: ViewContext
    ): Int = abs(viewContext.viewport.centerX / viewContext.viewWidth).toInt()

    override fun leftPageLayout(
        viewContext: ViewContext,
        basePageLayout: PageLayout?
    ): PageLayout? {
        val currentIndex = (basePageLayout?.index ?: currentPageLayoutIndex(viewContext))
        val leftIndex = currentIndex + 1
        if (leftIndex >= pageLayoutManager.getCount()) {
            return null
        }
        return getPageLayout(leftIndex, viewContext)

    }

    override fun rightPageLayout(
        viewContext: ViewContext,
        basePageLayout: PageLayout?
    ): PageLayout? {
        val currentIndex = (basePageLayout?.index ?: currentPageLayoutIndex(viewContext))
        val rightIndex = currentIndex - 1
        if (rightIndex < 0) {
            return null
        }
        return getPageLayout(rightIndex, viewContext)
    }

    override fun layout(index: Int, pageLayout: PageLayout, viewContext: ViewContext): PageLayout {
        val positionLeft = viewContext.viewWidth * -(index + 1)

        pageLayout.globalPosition.also {
            it.left = positionLeft
            it.right = it.left + viewContext.viewWidth
            it.top = 0.0F
            it.bottom = viewContext.viewHeight
        }

        return pageLayout.flip()
    }

    override fun calcLastVisiblePageLayoutIndex(viewContext: ViewContext): Int {
        return abs(floor(viewContext.viewport.right / viewContext.viewWidth)).toInt()
    }

    override fun calcFirstVisiblePageLayoutIndex(viewContext: ViewContext): Int {
        return abs(ceil(viewContext.viewport.right / viewContext.viewWidth)).toInt() - 1
    }

    override fun initWith(viewContext: ViewContext) {
        super.initWith(viewContext)

        viewContext.setScrollableAxis(
            horizontal = ViewContext.SCROLL_POLICY_UNLIMITED,
            vertical = ViewContext.SCROLL_POLICY_STRICT_SCROLL_AREA
        )
    }

    override fun isOverScrolled(viewContext: ViewContext, dx: Float, dy: Float): Boolean {
        val firstPagePosition = firstPageLayout(viewContext)
        val lastPagePosition = lastPageLayout(viewContext)

        return when {
            dx > 0 && firstPagePosition != null && viewContext.viewport.right > firstPagePosition.globalPosition.right -> true
            dx < 0 && lastPagePosition != null && viewContext.viewport.left < lastPagePosition.globalPosition.left -> true
            else -> false
        }
    }
}

