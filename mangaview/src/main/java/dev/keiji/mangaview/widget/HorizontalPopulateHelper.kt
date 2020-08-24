package dev.keiji.mangaview.widget

import dev.keiji.mangaview.Log
import dev.keiji.mangaview.Rectangle
import kotlin.math.roundToInt

class HorizontalPopulateHelper : PopulateHelper() {
    companion object {
        private val TAG = HorizontalPopulateHelper::class.java.simpleName
    }

    private val shouldPopulateHorizontal = fun(rect: Rectangle?): Boolean {
        rect ?: return false
        val diff = viewContext.viewport.width - rect.width
        return diff > (pagingTouchSlop / viewContext.currentScale)
    }

    private val calcDiffXToLeft = fun(rect: Rectangle): Float {
        return (rect.right - viewContext.viewport.right)
    }

    private val calcDiffXToRight = fun(rect: Rectangle): Float {
        return (rect.left - viewContext.viewport.left)
    }

    override fun populate() {
        Log.d(TAG, "populate!")

        val layoutManagerSnapshot = layoutManager ?: return

        val currentPageLayout = layoutManagerSnapshot.currentPageLayout(viewContext)
        val scrollArea = currentPageLayout
            ?.getScaledScrollArea(viewContext) ?: return

        // detect overscroll
        if (scrollArea.contains(viewContext.viewport)) {
            Log.d(TAG, "no overscroll detected.")
            return
        }

        val toLeft = (viewContext.viewport.centerX < scrollArea.centerX)

        val handled = if (toLeft) {
            val leftRect = layoutManagerSnapshot.leftPageLayout(viewContext)
            val leftArea =
                leftRect?.calcScrollArea(
                    viewContext,
                    tmpLeftScrollArea
                )
            populateTo(
                scrollArea,
                leftArea,
                shouldPopulateHorizontal,
                calcDiffXToLeft, calcDiffVertical,
                populateDuration
            )
        } else {
            val rightRect = layoutManagerSnapshot.rightPageLayout(viewContext)
            val rightArea =
                rightRect?.calcScrollArea(
                    viewContext,
                    tmpRightScrollArea
                )
            populateTo(
                scrollArea,
                rightArea,
                shouldPopulateHorizontal,
                calcDiffXToRight, calcDiffVertical,
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

    override fun populateToLeft(leftRect: PageLayout) {
        val layoutManagerSnapshot = layoutManager ?: return

        val currentRect = layoutManagerSnapshot.currentPageLayout(viewContext)
        val scrollArea = currentRect
            ?.getScaledScrollArea(viewContext)

        populateTo(
            scrollArea,
            leftRect.calcScrollArea(viewContext, tmpLeftScrollArea),
            shouldPopulateHorizontal,
            calcDiffXToLeft, calcDiffVertical,
            populateDuration
        )
    }

    override fun populateToRight(rightRect: PageLayout) {
        val layoutManagerSnapshot = layoutManager ?: return

        val currentRect = layoutManagerSnapshot.currentPageLayout(viewContext)
        val scrollArea = currentRect
            ?.getScaledScrollArea(viewContext)

        populateTo(
            scrollArea,
            rightRect.calcScrollArea(
                viewContext, tmpRightScrollArea
            ),
            shouldPopulateHorizontal,
            calcDiffXToRight, calcDiffVertical,
            populateDuration
        )
    }
}
