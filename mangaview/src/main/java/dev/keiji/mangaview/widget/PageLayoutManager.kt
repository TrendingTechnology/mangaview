package dev.keiji.mangaview.widget

abstract class PageLayoutManager {

    companion object {
        private val TAG = PageLayoutManager::class.java.simpleName
    }

    lateinit var viewContext: ViewContext
    lateinit var pageAdapter: PageAdapter

    abstract fun getCount(pageCount: Int = pageAdapter.pageCount): Int

    internal open fun layout(pageLayout: PageLayout, index: Int): PageLayout {
        val firstPageIndex = calcFirstPageIndex(index)
        val lastPageIndex = calcLastPageIndex(index)

        (firstPageIndex..lastPageIndex).forEach { pageIndex ->
            pageLayout.add(pageAdapter.getPage(pageIndex))
        }

        return pageLayout
    }

    abstract fun calcPageLayoutIndex(pageIndex: Int): Int

    abstract fun calcFirstPageIndex(pageLayoutIndex: Int): Int
    abstract fun calcLastPageIndex(pageLayoutIndex: Int): Int

    internal abstract fun createPageLayout(index: Int): PageLayout
}
