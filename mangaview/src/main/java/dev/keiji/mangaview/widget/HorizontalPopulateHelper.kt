package dev.keiji.mangaview.widget

import dev.keiji.mangaview.Rectangle

class HorizontalPopulateHelper : PopulateHelper() {
    companion object {
        private val TAG = HorizontalPopulateHelper::class.java.simpleName
    }

    private val shouldPopulateHorizontal = fun(rect: Rectangle?): Boolean {
        rect ?: return false
        val diff = viewContext.viewport.width - rect.width
        return diff > (pagingTouchSlop / viewContext.currentScale)
    }

    private val calcDiffXToLeft = fun(pageLayout: PageLayout): Float {
        return pageLayout.getScaledScrollArea(viewContext).right - viewContext.viewport.right
    }

    private val calcDiffXToRight = fun(pageLayout: PageLayout): Float {
        return pageLayout.getScaledScrollArea(viewContext).left - viewContext.viewport.left
    }

    private val calcDiffXToGlobalLeft = fun(pageLayout: PageLayout): Float {
        return pageLayout.globalPosition.left - viewContext.viewport.left
    }

    private val calcDiffXToGlobalRight = fun(pageLayout: PageLayout): Float {
        return pageLayout.globalPosition.left - viewContext.viewport.left
    }

    override fun populateToLeft(leftRect: PageLayout): Operation? {
        val layoutManagerSnapshot = layoutManager ?: return null

        val currentRect = layoutManagerSnapshot.currentPageLayout(viewContext)
        val scrollArea = currentRect?.getScaledScrollArea(viewContext)

        val dx = if (resetScaleOnPageChanged) {
            calcDiffXToGlobalLeft
        } else {
            calcDiffXToLeft
        }
        val dy = if (resetScaleOnPageChanged) {
            calcDiffY
        } else {
            calcDiffVertical
        }

        return populateTo(
            scrollArea,
            leftRect,
            shouldPopulateHorizontal,
            dx, dy
        )
    }

    override fun populateToRight(rightRect: PageLayout): Operation? {
        val layoutManagerSnapshot = layoutManager ?: return null

        val currentRect = layoutManagerSnapshot.currentPageLayout(viewContext)
        val scrollArea = currentRect?.getScaledScrollArea(viewContext)

        val dx = if (resetScaleOnPageChanged) {
            calcDiffXToGlobalRight
        } else {
            calcDiffXToRight
        }
        val dy = if (resetScaleOnPageChanged) {
            calcDiffY
        } else {
            calcDiffVertical
        }

        return populateTo(
            scrollArea,
            rightRect,
            shouldPopulateHorizontal,
            dx, dy
        )
    }
}
