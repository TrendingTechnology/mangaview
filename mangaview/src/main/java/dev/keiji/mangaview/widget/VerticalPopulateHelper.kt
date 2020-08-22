package dev.keiji.mangaview.widget

import dev.keiji.mangaview.Log
import dev.keiji.mangaview.Rectangle
import kotlin.math.roundToInt


class VerticalPopulateHelper : PopulateHelper() {

    companion object {
        private val TAG = VerticalPopulateHelper::class.java.simpleName
    }

    private val shouldPopulateVertical = fun(rect: Rectangle?): Boolean {
        rect ?: return false
        val diff = viewContext.viewport.height - rect.height
        return diff > (pagingTouchSlop / viewContext.currentScale)
    }

    private val calcDiffYToTop = fun(rect: Rectangle): Int {
        return (rect.bottom - viewContext.viewport.bottom).roundToInt()
    }

    private val calcDiffYToBottom = fun(rect: Rectangle): Int {
        return (rect.top - viewContext.viewport.top).roundToInt()
    }

    override fun populate() {
        Log.d(TAG, "populate!")

        val layoutManagerSnapshot = layoutManager ?: return

        val currentRect = layoutManagerSnapshot.currentPageLayout(viewContext)
        val scrollArea = currentRect
            ?.getScaledScrollArea(viewContext) ?: return

        // detect overscroll
        if (scrollArea.contains(viewContext.viewport)) {
            return
        }

        val toTop = (viewContext.viewport.centerY < scrollArea.centerY)

        val handled = if (toTop) {
            val topRect = layoutManagerSnapshot.topPageLayout(viewContext)
            val topArea =
                topRect?.calcScrollArea(
                    tmpTopScrollArea,
                    viewContext
                )
            populateTo(
                scrollArea,
                topArea,
                shouldPopulateVertical,
                calcDiffHorizontal, calcDiffYToTop,
                populateDuration
            )
        } else {
            val bottomRect = layoutManagerSnapshot.bottomPageLayout(viewContext)
            val bottomArea =
                bottomRect?.calcScrollArea(
                    tmpBottomScrollArea,
                    viewContext
                )
            populateTo(
                scrollArea,
                bottomArea,
                shouldPopulateVertical,
                calcDiffHorizontal, calcDiffYToBottom,
                populateDuration
            )
        }

        if (!handled) {
            populateToCurrent(
                scrollArea,
                reverseScrollDuration
            )
        }
    }

    override fun populateToTop(topRect: PageLayout) {
        val layoutManagerSnapshot = layoutManager ?: return

        val currentRect = layoutManagerSnapshot.currentPageLayout(viewContext)
        val scrollArea = currentRect
            ?.getScaledScrollArea(viewContext) ?: return

        populateTo(
            scrollArea,
            topRect.calcScrollArea(tmpLeftScrollArea, viewContext),
            shouldPopulateVertical,
            calcDiffHorizontal, calcDiffYToTop,
            populateDuration
        )
    }

    override fun populateToBottom(bottomRect: PageLayout) {
        val layoutManagerSnapshot = layoutManager ?: return

        val currentRect = layoutManagerSnapshot.currentPageLayout(viewContext)
        val scrollArea = currentRect
            ?.getScaledScrollArea(viewContext) ?: return

        populateTo(
            scrollArea,
            bottomRect.calcScrollArea(tmpLeftScrollArea, viewContext),
            shouldPopulateVertical,
            calcDiffHorizontal, calcDiffYToBottom,
            populateDuration
        )
    }

}
