package dev.keiji.mangaview.widget

import androidx.collection.SparseArrayCompat
import dev.keiji.mangaview.Log
import kotlin.math.max
import kotlin.math.min

abstract class LayoutManager {

    companion object {
        private val TAG = LayoutManager::class.java.simpleName
    }

    internal abstract val populateHelper: PopulateHelper

    internal lateinit var adapter: PageAdapter
    internal lateinit var pageLayoutManager: PageLayoutManager

    var viewWidth: Int = 0
    var viewHeight: Int = 0

    fun setViewSize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    abstract fun currentPageLayoutIndex(viewContext: ViewContext): Int

    fun getPageLayout(index: Int, viewContext: ViewContext): PageLayout? {
        if (index < 0) {
            return null
        }
        if (index >= pageLayoutManager.getCount()) {
            return null
        }

        val pageLayout = caches[index] ?: layout(
            index,
            pageLayoutManager.createPageLayout(),
            viewContext
        )
        caches.put(index, pageLayout)
        return pageLayout
    }

    fun currentPageLayout(viewContext: ViewContext): PageLayout? {
        return getPageLayout(currentPageLayoutIndex(viewContext), viewContext)
    }

    open fun leftPageLayout(viewContext: ViewContext): PageLayout? = null
    open fun rightPageLayout(viewContext: ViewContext): PageLayout? = null
    open fun topPageLayout(viewContext: ViewContext): PageLayout? = null
    open fun bottomPageLayout(viewContext: ViewContext): PageLayout? = null

    private val caches = SparseArrayCompat<PageLayout>()

    fun obtainVisiblePageLayout(
        viewContext: ViewContext,
        resultList: ArrayList<PageLayout> = ArrayList(),
        offsetScreenPageLimit: Int = 1
    ): List<PageLayout> {
        val firstVisiblePageLayoutIndex = calcFirstVisiblePageLayoutIndex(viewContext)
        val endVisiblePageLayoutIndex = calcLastVisiblePageLayoutIndex(viewContext)

        Log.d(
            TAG,
            "firstVisiblePageLayoutIndex:$firstVisiblePageLayoutIndex, endVisiblePageLayoutIndex:$endVisiblePageLayoutIndex"
        )
        var startIndex = min(endVisiblePageLayoutIndex, firstVisiblePageLayoutIndex)
        var endIndex = max(endVisiblePageLayoutIndex, firstVisiblePageLayoutIndex)

        startIndex -= offsetScreenPageLimit
        endIndex += offsetScreenPageLimit

        startIndex = max(0, startIndex)
        endIndex = min(
            endIndex,
            pageLayoutManager.getCount() - 1
        )

        resultList.clear()

        (startIndex..endIndex).forEach { index ->
            val pageLayout = getPageLayout(index, viewContext) ?: return@forEach
            if (!pageLayout.isFilled) {
                pageLayoutManager.layout(pageLayout, index)
            }
            resultList.add(pageLayout)
        }

        return resultList
    }

    abstract val initialScrollX: Float
    abstract val initialScrollY: Float

    abstract fun layout(index: Int, pageLayout: PageLayout, viewContext: ViewContext): PageLayout

    abstract fun calcFirstVisiblePageLayoutIndex(viewContext: ViewContext): Int

    abstract fun calcLastVisiblePageLayoutIndex(viewContext: ViewContext): Int
}
