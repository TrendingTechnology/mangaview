package dev.keiji.mangaview.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ScaleGestureDetectorCompat
import androidx.core.view.ViewCompat
import dev.keiji.mangaview.Log
import dev.keiji.mangaview.Rectangle
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt

interface OnTapListener {
    fun onTap(mangaView: MangaView, x: Float, y: Float): Boolean = false
    fun onTap(page: Page, x: Float, y: Float): Boolean = false
    fun onTap(layer: ContentLayer, x: Float, y: Float): Boolean = false
}

interface OnDoubleTapListener {
    fun onDoubleTap(mangaView: MangaView, x: Float, y: Float): Boolean = false
    fun onDoubleTap(page: Page, x: Float, y: Float): Boolean = false
    fun onDoubleTap(layer: ContentLayer, x: Float, y: Float): Boolean = false
}

interface OnPageChangeListener {
    fun onScrollStateChanged(mangaView: MangaView, scrollState: Int) {}
    fun onPageLayoutSelected(mangaView: MangaView, pageLayout: PageLayout) {}
}

class MangaView(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int
) : View(context, attrs, defStyleAttr),
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener,
    ScaleGestureDetector.OnScaleGestureListener {

    companion object {
        private val TAG = MangaView::class.java.simpleName

        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2

        private const val SCROLLING_DURATION = 280
        private const val REVERSE_SCROLLING_DURATION = 350
        private const val SCALING_DURATION = 350L
    }

    constructor(context: Context) : this(context, null, 0x0)

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0x0)

    private var scrollState: Int = SCROLL_STATE_IDLE
        set(value) {
            if (field == value) {
                return
            }
            field = value
            onPageChangeListener.onScrollStateChanged(this, value)
        }

    private val viewConfiguration: ViewConfiguration = ViewConfiguration.get(context)
    private val density = context.resources.displayMetrics.scaledDensity

    private val overScrollDistance =
        (viewConfiguration.scaledOverscrollDistance * density).roundToInt()
    private val pagingTouchSlop = viewConfiguration.scaledPagingTouchSlop * density

    var onPageChangeListener = object : OnPageChangeListener {
        override fun onScrollStateChanged(mangaView: MangaView, scrollState: Int) {
            Log.d(TAG, "scrollState -> ${scrollState}")
        }

        override fun onPageLayoutSelected(mangaView: MangaView, pageLayout: PageLayout) {
            Log.d(TAG, "onPageLayoutSelected -> ${pageLayout.pages[0].index}")
        }
    }
    var layoutManager: LayoutManager? = null
        set(value) {
            field = value
            isInitialized = false
            postInvalidate()
        }

    var adapter: PageAdapter? = null
        set(value) {
            field = value
            isInitialized = false
            postInvalidate()
        }

    @Suppress("MemberVisibilityCanBePrivate")
    var pageLayoutManager: PageLayoutManager = DoublePageLayoutManager(isSpread = true)
        set(value) {
            field = value
            isInitialized = false
            postInvalidate()
        }


    private val viewState = ViewContext()

    private var isInitialized = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        viewState.setViewSize(w, h)
        layoutManager?.also {
            it.setViewSize(w, h)
            viewState.offsetTo(it.initialScrollX, it.initialScrollY)
        }

        isInitialized = false
    }

    private fun init() {
        val layoutManagerSnapshot = layoutManager ?: return
        val adapterSnapshot = adapter ?: return

        layoutManagerSnapshot.adapter = adapterSnapshot
        layoutManagerSnapshot.pageLayoutManager = pageLayoutManager
        pageLayoutManager.pageAdapter = adapterSnapshot

        isInitialized = true
    }

    @Suppress("MemberVisibilityCanBePrivate")
    var paint = Paint().also {
        it.isAntiAlias = true
        it.isDither = true
    }

    @Suppress("MemberVisibilityCanBePrivate")
    var coroutineScope = CoroutineScope(Dispatchers.Main)
        set(value) {
            field.cancel()
            field = value
        }

    private val visiblePageLayoutList = ArrayList<PageLayout>()
    private val recycleBin = ArrayList<Page>()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (!isInitialized) {
            init()
            postInvalidate()
            return
        }

        recycleBin.addAll(visiblePageLayoutList.flatMap { it.pages })

        layoutManager?.obtainVisiblePageLayout(viewState, visiblePageLayoutList)

        val result = visiblePageLayoutList
            .flatMap { it.pages }
            .map { page ->
                recycleBin.remove(page)
                if (!page.globalRect.intersect(viewState.viewport)) {
                    return@map true
                }
                page.draw(canvas, viewState, paint, coroutineScope)
            }.none { !it }

        coroutineScope.launch(Dispatchers.Unconfined) {
            recycleBin.forEach { page ->
                page.recycle()
            }
            recycleBin.clear()
        }

        if (!result) {
            postInvalidate()
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun showPage(pageIndex: Int, smoothScroll: Boolean = false) {
        val pageLayoutIndex = pageLayoutManager.calcPageLayoutIndex(pageIndex)
        val pageLayout = layoutManager?.getPageLayout(pageLayoutIndex, viewState)

        if (pageLayout == null) {
            Log.d(TAG, "pageIndex: ${pageIndex} -> pageLayoutIndex ${pageLayoutIndex} not found.")
            return
        }

        val scrollArea = pageLayout.globalPosition

        if (!smoothScroll) {
            scale(
                1.0F,
                viewState.viewport.centerY,
                viewState.viewport.centerY,
                smoothScale = false
            ) {}
            viewState.offsetTo(scrollArea.left, scrollArea.top)
            postInvalidate()
            return
        }

        scale(viewState.minScale, null, null, smoothScale = true) {
            val currentLeft = viewState.viewport.left.roundToInt()
            val currentTop = viewState.viewport.top.roundToInt()

            settleScroller.startScroll(
                currentLeft,
                currentTop,
                scrollArea.left.roundToInt() - currentLeft,
                scrollArea.top.roundToInt() - currentTop,
                SCROLLING_DURATION
            )
            startAnimation()
        }

        startAnimation()
    }

    private val gestureDetector = GestureDetectorCompat(context, this).also {
        it.setOnDoubleTapListener(this)
    }
    private val scaleGestureDetector = ScaleGestureDetector(context, this).also {
        ScaleGestureDetectorCompat.setQuickScaleEnabled(it, false)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return super.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                abortAnimation()
            }
            MotionEvent.ACTION_UP -> {
                if (scrollState != SCROLL_STATE_SETTLING) {
                    scrollState = SCROLL_STATE_IDLE
                }
                populate()
            }
        }

        if (scaleGestureDetector.onTouchEvent(event)) {
            postInvalidate()
        }

        if (gestureDetector.onTouchEvent(event)) {
            postInvalidate()
            return true
        }

        return super.onTouchEvent(event)
    }

    private fun populate() {
        val layoutManagerSnapshot = layoutManager ?: return

        val currentScrollArea = currentPageLayout
            ?.calcScrollArea(tmpCurrentScrollArea, viewState) ?: return

        layoutManagerSnapshot.populateHelper
            .init(
                viewState,
                layoutManagerSnapshot,
                settleScroller,
                pagingTouchSlop,
                SCROLLING_DURATION,
                REVERSE_SCROLLING_DURATION
            )
            .populateToCurrent(
                currentScrollArea, SCROLLING_DURATION
            )
        startAnimation()

        scalingState = ScalingState.Finish
    }

    private var settleScroller = OverScroller(context, DecelerateInterpolator())

    private var scroller = OverScroller(context, DecelerateInterpolator())

    private fun startAnimation() {
        ViewCompat.postInvalidateOnAnimation(this)
    }

    private fun abortAnimation() {
        scroller.abortAnimation()
        settleScroller.abortAnimation()
        scaleOperation = null
    }

    private var scaleInterpolator = DecelerateInterpolator()

    private var scaleOperation: ScaleOperation? = null

    override fun computeScroll() {
        super.computeScroll()

        if (!isInitialized) {
            return
        }

        val needPostInvalidateScale = scaleOperation?.let {
            val elapsed = System.currentTimeMillis() - it.startTimeMillis
            val input = elapsed.toFloat() / it.durationMillis
            val scaleFactor = scaleInterpolator.getInterpolation(input)
            val newScale = it.from + it.diff * scaleFactor
            val focusX = it.focusX ?: viewState.viewWidth / 2
            val focusY = it.focusY ?: viewState.viewHeight / 2
            viewState.scaleTo(newScale, focusX, focusY)
            populate()

            val needPostInvalidateScale = if (input > 1.0F || elapsed <= 0) {
                viewState.scaleTo(it.to, focusX, focusY)
                it.onScaleFinish()

                scaleOperation = null
                false
            } else {
                true
            }

            needPostInvalidateScale
        } ?: false

        val needPostInvalidateScroll =
            if (scroller.isFinished && !settleScroller.isFinished && settleScroller.computeScrollOffset()) {
                viewState.offsetTo(settleScroller.currX.toFloat(), settleScroller.currY.toFloat())
                scrollState = SCROLL_STATE_SETTLING
                !settleScroller.isFinished

            } else if (!scroller.isFinished && scroller.computeScrollOffset()) {
                viewState.offsetTo(scroller.currX.toFloat(), scroller.currY.toFloat())
                !scroller.isFinished
            } else {
                false
            }

        layoutManager?.currentPageLayout(viewState)?.also {
            currentPageLayout = it
        }

        if (!needPostInvalidateScroll && scrollState == SCROLL_STATE_SETTLING) {
            scrollState = SCROLL_STATE_IDLE
        }

        if (needPostInvalidateScale || needPostInvalidateScroll) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun onShowPress(e: MotionEvent?) {
        Log.d(TAG, "onShowPress")
        abortAnimation()
    }

    override fun onSingleTapUp(e: MotionEvent?) = false

    override fun onDown(e: MotionEvent?) = true

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val handled = fling(velocityX, velocityY)
        if (handled) {
            startAnimation()
        }
        return handled
    }

    var currentPageLayout: PageLayout? = null
        private set(value) {
            if (value == null || field == value) {
                return
            }
            field = value
            onPageChangeListener.onPageLayoutSelected(this, value)
        }

    private val tmpCurrentScrollArea = Rectangle()

    private fun fling(velocityX: Float, velocityY: Float): Boolean {
        val layoutManagerSnapshot = layoutManager ?: return false

        val scaledVelocityX = velocityX / viewState.currentScale
        val scaledVelocityY = velocityY / viewState.currentScale

        Log.d(TAG, "scaledVelocityX $scaledVelocityX")
        Log.d(TAG, "scaledVelocityY $scaledVelocityY")

        val currentPageLayoutSnapshot = currentPageLayout ?: return false

        val currentScrollArea =
            currentPageLayoutSnapshot.calcScrollArea(tmpCurrentScrollArea, viewState)

        Log.d(TAG, "fling ${currentPageLayoutSnapshot.pages[0].index}")

        val populateHelper = layoutManagerSnapshot.populateHelper
            .init(
                viewState,
                layoutManagerSnapshot,
                settleScroller,
                pagingTouchSlop,
                SCROLLING_DURATION,
                REVERSE_SCROLLING_DURATION
            )

        var handleHorizontal = false
        var handleVertical = false

        val horizontal = (abs(scaledVelocityX) > abs(scaledVelocityY))

        if (horizontal) {
            val leftRect = layoutManagerSnapshot.leftPageLayout(viewState)
            val rightRect = layoutManagerSnapshot.rightPageLayout(viewState)

            handleHorizontal = if (scaledVelocityX > 0.0F && leftRect != null
                && !viewState.canScrollLeft(currentScrollArea)
            ) {
                Log.d(TAG, "populateToLeft")
                populateHelper.populateToLeft(leftRect)
                true
            } else if (scaledVelocityX < 0.0F && rightRect != null
                && !viewState.canScrollRight(currentScrollArea)
            ) {
                Log.d(TAG, "populateToRight")
                populateHelper.populateToRight(rightRect)
                true
            } else {
                false
            }
        } else {
            val topRect = layoutManagerSnapshot.topPageLayout(viewState)
            val bottomRect = layoutManagerSnapshot.bottomPageLayout(viewState)

            handleVertical = if (scaledVelocityY > 0.0F && topRect != null
                && !viewState.canScrollTop(currentScrollArea)
            ) {
                populateHelper.populateToTop(topRect)
                true
            } else if (scaledVelocityY < 0.0F && bottomRect != null
                && !viewState.canScrollBottom(currentScrollArea)
            ) {
                populateHelper.populateToBottom(bottomRect)
                true
            } else {
                false
            }
        }

        if (handleHorizontal || handleVertical) {
            return true
        }

        val minX = currentScrollArea.left.roundToInt() - overScrollDistance
        val maxX =
            (currentScrollArea.right - viewState.viewport.width).roundToInt() + overScrollDistance

        val minY = currentScrollArea.top.roundToInt() - overScrollDistance
        val maxY =
            (currentScrollArea.bottom - viewState.viewport.height).roundToInt() + overScrollDistance

        Log.d(
            TAG, "fling " +
                    "currentX ${viewState.currentX}, currentY ${viewState.currentY}, " +
                    "scaledVelocityX ${scaledVelocityX}, scaledVelocityY ${scaledVelocityY}, " +
                    "minX ${minX}, maxX ${maxX}, " +
                    "minY ${minY}, maxY ${maxY}"
        )

        scroller.fling(
            viewState.currentX.roundToInt(),
            viewState.currentY.roundToInt(),
            -scaledVelocityX.roundToInt(),
            -scaledVelocityY.roundToInt(),
            minX, maxX,
            minY, maxY,
        )

        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        viewState.scroll(
            distanceX / viewState.currentScale,
            distanceY / viewState.currentScale
        )

        layoutManager?.currentPageLayout(viewState)?.also {
            currentPageLayout = it
        }

        scrollState = SCROLL_STATE_DRAGGING

        return true
    }

    override fun onLongPress(e: MotionEvent?) {
        Log.d(TAG, "onLongPress")
    }

    enum class ScalingState {
        Begin,
        Scaling,
        End,
        Finish
    }

    private var scalingState = ScalingState.Finish
        set(value) {
            if (field == value) {
                return
            }
            Log.d(TAG, "ScalingState changed: $field -> $value")
            field = value
        }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        detector ?: return false

        scalingState = ScalingState.Begin

        return true
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        detector ?: return false

        scalingState = ScalingState.Scaling
        viewState.scale(detector.scaleFactor, detector.focusX, detector.focusY)

        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        detector ?: return

        scalingState = ScalingState.End
    }

    private fun scale(
        scale: Float,
        focusX: Float?,
        focusY: Float?,
        smoothScale: Boolean = false,
        onScaleFinish: () -> Unit,
    ) {
        if (!smoothScale) {
            viewState.scaleTo(
                scale,
                focusX ?: viewState.viewport.centerX,
                focusY ?: viewState.viewport.centerY
            )
            postInvalidate()
            return
        }

        scaleOperation = ScaleOperation(
            viewState.currentScale, scale,
            System.currentTimeMillis(), SCALING_DURATION,
            focusX, focusY, onScaleFinish
        )

        startAnimation()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    var onDoubleTapListener = object : OnDoubleTapListener {
        override fun onDoubleTap(mangaView: MangaView, x: Float, y: Float): Boolean {
            return false
        }

        override fun onDoubleTap(page: Page, x: Float, y: Float): Boolean {
            return false
        }

        override fun onDoubleTap(layer: ContentLayer, x: Float, y: Float): Boolean {
            return false
        }
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        e ?: return false

        var handled = onDoubleTapListener.onDoubleTap(this, e.x, e.y)
        if (handled) {
            return true
        }

        // mapping global point
        val globalPosition = projectGlobalPosition(e.x, e.y)

        visiblePageLayoutList
            .flatMap { it.pages }
            .forEach pageLoop@{ page ->
                handled = page.requestHandleEvent(
                    globalPosition.centerX,
                    globalPosition.centerY,
                    onDoubleTapListener
                )
                if (handled) {
                    return@pageLoop
                }

                page.layers.forEach { layer ->
                    handled = layer.requestHandleEvent(
                        globalPosition.centerX,
                        globalPosition.centerY,
                        onDoubleTapListener
                    )
                    if (handled) {
                        return@pageLoop
                    }
                }
            }

        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
        e ?: return false
        return true
    }

    @Suppress("MemberVisibilityCanBePrivate")
    var onTapListener = object : OnTapListener {
        override fun onTap(page: Page, x: Float, y: Float): Boolean {
            Log.d(TAG, "onTap page:${page.index}, x:$x, y:$y")

            return false
        }

        override fun onTap(layer: ContentLayer, x: Float, y: Float): Boolean {
            Log.d(TAG, "onTap ${layer.page?.index}, layer, x:$x, y:$y")

            return false
        }
    }

    private val eventPointTmp = Rectangle()

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        e ?: return false
        Log.d(TAG, "onSingleTapConfirmed")

        var handled = onTapListener.onTap(this, e.x, e.y)
        if (handled) {
            return true
        }

        // mapping global point
        val globalPosition = projectGlobalPosition(e.x, e.y)

        visiblePageLayoutList
            .flatMap { it.pages }
            .forEach pageLoop@{ page ->
                handled = page.requestHandleEvent(
                    globalPosition.centerX,
                    globalPosition.centerY,
                    onTapListener
                )
                if (handled) {
                    return@pageLoop
                }

                page.layers.forEach { layer ->
                    handled = layer.requestHandleEvent(
                        globalPosition.centerX,
                        globalPosition.centerY,
                        onTapListener
                    )
                    if (handled) {
                        return@pageLoop
                    }
                }
            }
        return false
    }

    private fun projectGlobalPosition(x: Float, y: Float): Rectangle {
        val horizontalRatio = x / viewState.viewWidth
        val verticalRatio = y / viewState.viewHeight

        val globalX = viewState.viewport.left + viewState.viewport.width * horizontalRatio
        val globalY = viewState.viewport.top + viewState.viewport.height * verticalRatio

        return eventPointTmp
            .set(globalX, globalY, globalX, globalY)
    }

    private data class ScaleOperation(
        val from: Float,
        val to: Float,
        val startTimeMillis: Long,
        val durationMillis: Long,
        val focusX: Float?,
        val focusY: Float?,
        val onScaleFinish: () -> Unit
    ) {
        val diff: Float = to - from
    }
}
