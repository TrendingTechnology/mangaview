package dev.keiji.mangaview.widget

import dev.keiji.mangaview.Log
import dev.keiji.mangaview.Rectangle
import kotlin.math.max
import kotlin.math.min

class DoublePageLayout(
    private val isSpread: Boolean
) : PageLayout() {

    companion object {
        private val TAG = DoublePageLayout::class.java.simpleName
    }

    override val isFilled: Boolean
        get() = (leftPage != null && rightPage != null)

    var leftPage: Page? = null
        private set

    var rightPage: Page? = null
        private set

    override val primaryPage: Page?
        get() {
            return if (isFlip) {
                rightPage
            } else {
                leftPage
            }
        }

    override fun add(page: Page) {
        val layoutWidth = globalPosition.width / 2

        val value = page.index + if (isFlip) {
            1
        } else {
            0
        }

        if (value % 2 == 0) {
            addRightPage(page, layoutWidth)
        } else {
            addLeftPage(page, layoutWidth)
        }

        initScrollArea()
    }

    override fun replace(targetPage: Page, newPage: Page?) {
        if (leftPage == targetPage) {
            leftPage = newPage
        } else if (rightPage == targetPage) {
            rightPage = newPage
        }
    }

    override fun initScrollArea() {
        val evenPagePosition = rightPage?.globalRect ?: return
        val oddPagePosition = leftPage?.globalRect ?: return

        scrollArea.set(
            min(evenPagePosition.left, oddPagePosition.left),
            min(evenPagePosition.top, oddPagePosition.top),
            max(evenPagePosition.right, oddPagePosition.right),
            max(evenPagePosition.bottom, oddPagePosition.bottom)
        )
    }


    private fun addRightPage(page: Page, pageWidth: Float) {
        page.baseScale = min(
            pageWidth / page.width,
            globalPosition.height / page.height
        )

        val paddingHorizontal = pageWidth - page.scaledWidth
        val paddingVertical = globalPosition.height - page.scaledHeight

        var paddingLeft = 0.0F
        var paddingRight = 0.0F
        val paddingTop = paddingVertical / 2
        val paddingBottom = paddingVertical - paddingTop

        if (isSpread) {
            page.horizontalAlign = PageHorizontalAlign.Left
            paddingRight = paddingHorizontal
        } else {
            paddingRight = paddingHorizontal / 3 * 2
            paddingLeft = paddingHorizontal - paddingRight
        }

        page.globalRect.also {
            it.left = globalPosition.left + pageWidth + paddingLeft
            it.top = globalPosition.top + paddingTop
            it.right = globalPosition.right - paddingRight
            it.bottom = globalPosition.bottom - paddingBottom
        }

        rightPage = page
    }

    private fun addLeftPage(page: Page, pageWidth: Float) {
        page.baseScale = min(
            pageWidth / page.width,
            globalPosition.height / page.height
        )

        val paddingHorizontal = pageWidth - page.scaledWidth
        val paddingVertical = globalPosition.height - page.scaledHeight

        Log.d(TAG, "${paddingHorizontal}")

        var paddingLeft = pageWidth
        var paddingRight = 0.0F
        val paddingTop = paddingVertical / 2
        val paddingBottom = paddingVertical - paddingTop

        if (isSpread) {
            page.horizontalAlign = PageHorizontalAlign.Right
            paddingLeft = paddingHorizontal
        } else {
            paddingLeft = paddingHorizontal / 3 * 2
            paddingRight = paddingHorizontal - paddingLeft
        }

        page.globalRect.also {
            it.left = globalPosition.left + paddingLeft
            it.top = globalPosition.top + paddingTop
            it.right = globalPosition.right - pageWidth - paddingRight
            it.bottom = globalPosition.bottom - paddingBottom
        }

        leftPage = page
    }

    override val pages: List<Page>
        get() {
            val evenPageSnapshot = rightPage ?: return emptyList<Page>()
            val oddPageSnapshot = leftPage ?: return emptyList<Page>()

            return if (!isFlip) {
                listOf(oddPageSnapshot, evenPageSnapshot)
            } else {
                listOf(evenPageSnapshot, oddPageSnapshot)
            }
        }

    private var isFlip = false

    override fun flip(): PageLayout {
        val evenPageSnapshot = rightPage ?: return this
        val oddPageSnapshot = leftPage ?: return this

        val tmp = Rectangle()

        tmp.copyFrom(oddPageSnapshot.globalRect)
        oddPageSnapshot.globalRect.copyFrom(evenPageSnapshot.globalRect)
        evenPageSnapshot.globalRect.copyFrom(tmp)

        if (isSpread) {
            oddPageSnapshot.horizontalAlign = PageHorizontalAlign.Left
            evenPageSnapshot.horizontalAlign = PageHorizontalAlign.Right
        }

        isFlip = !isFlip

        return this
    }

    override fun calcScrollArea(
        rectangle: Rectangle,
        viewContext: ViewContext
    ): Rectangle {
        val scale = viewContext.currentScale

        val scaledScrollWidth = scrollArea.width * scale
        val scaledScrollHeight = scrollArea.height * scale

        val marginHorizontal = max(globalPosition.width - scaledScrollWidth, 0.0F)
        val marginVertical = max(globalPosition.height - scaledScrollHeight, 0.0F)

        rectangle.copyFrom(scrollArea).also {
            it.left -= marginHorizontal / 2
            it.right += marginHorizontal - marginHorizontal / 2
            it.top -= marginVertical / 2
            it.bottom += marginVertical - marginVertical / 2
        }

        return rectangle
    }
}
