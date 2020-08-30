package dev.keiji.mangaview.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ScaleGestureDetectorCompat
import androidx.core.view.ViewCompat
import dev.keiji.mangaview.Log
import dev.keiji.mangaview.Rectangle
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

interface OnReadCompleteListener {
    fun onReadCompleted(mangaView: MangaView)
}

interface OnContentViewportChangeListener {
    fun onViewportChanged(mangaView: MangaView, layer: ContentLayer, viewport: RectF) = false
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

        private const val SCROLLING_DURATION = 280L
        private const val SCALING_DURATION = 250L
    }

    constructor(context: Context) : this(context, null, 0x0)

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0x0)

    private var scrollState: Int = SCROLL_STATE_IDLE
        set(value) {
            if (field != value) {
                Log.d(TAG, "scrollState $field -> $value")

                field = value
                onPageChangeListenerList.forEach {
                    it.onScrollStateChanged(this, value)
                }
            }

            if (value == SCROLL_STATE_IDLE) {
                currentPageLayout = layoutManager?.currentPageLayout(viewContext)
            }
        }

    private val viewConfiguration: ViewConfiguration = ViewConfiguration.get(context)
    private val density = context.resources.displayMetrics.scaledDensity

    private val pagingTouchSlop = viewConfiguration.scaledPagingTouchSlop * density

    private val onTapListenerList = ArrayList<OnTapListener>()

    fun addOnTapListener(onTapListener: OnTapListener) {
        onTapListenerList.add(onTapListener)
    }

    fun removeOnTapListener(onTapListener: OnTapListener) {
        onTapListenerList.remove(onTapListener)
    }

    private val onPageChangeListenerList = ArrayList<OnPageChangeListener>()

    fun addOnPageChangeListener(onPageChangeListener: OnPageChangeListener) {
        onPageChangeListenerList.add(onPageChangeListener)
    }

    fun removeOnPageChangeListener(onPageChangeListener: OnPageChangeListener) {
        onPageChangeListenerList.add(onPageChangeListener)
    }

    private val onReadCompleteListenerList = ArrayList<OnReadCompleteListener>()

    fun addOnReadCompleteListener(onReadCompleteListener: OnReadCompleteListener) {
        onReadCompleteListenerList.add(onReadCompleteListener)
    }

    fun removeOnReadCompleteListener(onReadCompleteListener: OnReadCompleteListener) {
        onReadCompleteListenerList.remove(onReadCompleteListener)
    }

    private val onDoubleTapListenerList = ArrayList<OnDoubleTapListener>()

    fun addOnDoubleTapListener(onDoubleTapListener: OnDoubleTapListener) {
        onDoubleTapListenerList.add(onDoubleTapListener)
    }

    fun removeOnDoubleTapListener(onDoubleTapListener: OnDoubleTapListener) {
        onDoubleTapListenerList.remove(onDoubleTapListener)
    }

    private var onContentViewportChangeListenerList = ArrayList<OnContentViewportChangeListener>()

    fun addOnContentViewportChangeListener(onContentViewportChangeListener: OnContentViewportChangeListener) {
        onContentViewportChangeListenerList.add(onContentViewportChangeListener)
    }

    fun removeOnContentViewportChangeListener(onContentViewportChangeListener: OnContentViewportChangeListener) {
        onContentViewportChangeListenerList.remove(onContentViewportChangeListener)
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

    internal val viewContext = ViewContext()

    private var isInitialized = false

    private val gestureDetector = GestureDetectorCompat(context, this).also {
        it.setOnDoubleTapListener(this)
    }
    private val scaleGestureDetector = ScaleGestureDetector(context, this).also {
        ScaleGestureDetectorCompat.setQuickScaleEnabled(it, false)
    }

    private val visiblePageLayoutList = ArrayList<PageLayout>()
    private val recycleBin = ArrayList<Page>()

    @Suppress("MemberVisibilityCanBePrivate")
    var paint = Paint().also {
        it.isAntiAlias = true
        it.isDither = true
    }

    private var scroller = OverScroller(context, DecelerateInterpolator())

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

    private var translateInterpolator = DecelerateInterpolator()
    private var scaleInterpolator = DecelerateInterpolator()

    private var operation: Operation? = null
        set(value) {
            if (value == null) {
                field = null
                return
            }

            if (field == null) {
                field = value
                return
            }

            val op = field ?: return

            if (op.isFinished || op.priority >= value.priority) {
                field = value
            }
        }

    var currentPageIndex: Int = 0

    private val tmpCurrentScrollArea = Rectangle()
    private val tmpEventPoint = Rectangle()

    private val currentScrollableArea: Rectangle?
        get() {
            return currentPageLayout?.calcScrollArea(viewContext, tmpCurrentScrollArea)
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        viewContext.setViewSize(w, h)

        isInitialized = false
    }

    private fun init() {
        val layoutManagerSnapshot = layoutManager ?: return
        val adapterSnapshot = adapter ?: return

        layoutManagerSnapshot.adapter = adapterSnapshot
        layoutManagerSnapshot.pageLayoutManager = pageLayoutManager
        layoutManagerSnapshot.initWith(viewContext)

        pageLayoutManager.pageAdapter = adapterSnapshot

        isInitialized = true
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (!isInitialized) {
            init()
            showPage(currentPageIndex)
            return
        }

        recycleBin.addAll(visiblePageLayoutList.flatMap { it.pages })

        layoutManager?.obtainVisiblePageLayout(viewContext, visiblePageLayoutList)

        val result = visiblePageLayoutList
            .flatMap { it.pages }
            .map { page ->
                recycleBin.remove(page)
                if (!page.globalRect.intersect(viewContext.viewport)) {
                    return@map true
                }
                page.draw(
                    canvas,
                    viewContext,
                    paint
                ) { layer: ContentLayer, viewport: RectF ->
                    onContentViewportChangeListenerList.forEach {
                        it.onViewportChanged(this, layer, viewport)
                    }
                }
            }.none { !it }

        recycleBin.forEach { page ->
            page.recycle()
        }
        recycleBin.clear()

        if (!result) {
            postInvalidate()
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        return super.onSaveInstanceState()
        // TODO
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
        // TODO
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun showPage(pageIndex: Int, smoothScroll: Boolean = false) {
        val pageLayoutIndex = pageLayoutManager.calcPageLayoutIndex(pageIndex)
        val pageLayout = layoutManager?.getPageLayout(pageLayoutIndex, viewContext)

        if (pageLayout == null) {
            Log.d(TAG, "pageIndex: ${pageIndex} -> pageLayoutIndex ${pageLayoutIndex} not found.")
            return
        }

        val scrollArea = pageLayout.globalPosition

        if (!smoothScroll) {
            scale(
                1.0F,
                viewContext.viewport.centerY,
                viewContext.viewport.centerY,
                smoothScale = false
            )
            viewContext.offsetTo(scrollArea.left, scrollArea.top)
            layoutManager?.obtainVisiblePageLayout(viewContext, visiblePageLayoutList)

            postInvalidate()
            scrollState = SCROLL_STATE_IDLE

            return
        }

        val currentLeft = viewContext.viewport.left
        val currentTop = viewContext.viewport.top

        val translateOperation = Operation.Translate(
            currentLeft,
            currentTop,
            scrollArea.left,
            scrollArea.top
        )
        operation = Operation(
            translate = translateOperation,
            scale = Operation.Scale(
                viewContext.currentScale,
                viewContext.minScale,
                null,
                null
            ),
            startTimeMillis = System.currentTimeMillis(),
            durationMillis = SCROLLING_DURATION
        )
        scrollState = SCROLL_STATE_SETTLING
        startAnimation()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return super.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                abortAnimation()
                scrollState = SCROLL_STATE_IDLE
            }
            MotionEvent.ACTION_UP -> {
                populateToCurrent()
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

    private fun populateToCurrent() {
        val layoutManagerSnapshot = layoutManager ?: return
        val currentScrollableAreaSnapshot = currentScrollableArea ?: return

        val lastPageLayout = layoutManagerSnapshot.lastPageLayout(viewContext) ?: return
        if (currentPageLayout == lastPageLayout) {
            handleReadCompleteEvent()
        }

        val translateOperation = layoutManagerSnapshot.populateHelper
            .init(
                viewContext,
                layoutManagerSnapshot,
                pagingTouchSlop
            )
            .populateToCurrent(currentScrollableAreaSnapshot)

        if (translateOperation != null) {
            operation = Operation(
                translate = translateOperation,
                startTimeMillis = System.currentTimeMillis(),
                durationMillis = SCALING_DURATION
            )
            scrollState = SCROLL_STATE_SETTLING
            startAnimation()
        }

        scalingState = ScalingState.Finish
    }

    private fun handleReadCompleteEvent(): Boolean {
        val layoutManagerSnapshot = layoutManager ?: return false
        val currentScrollableAreaSnapshot = currentScrollableArea ?: return false
        val viewport = viewContext.viewport

        if (layoutManagerSnapshot.leftPageLayout(viewContext) == null
            && viewport.left < currentScrollableAreaSnapshot.left
        ) {
            return fireEventReadComplete(
                abs(currentScrollableAreaSnapshot.left - viewport.left)
            )
        }

        if (layoutManagerSnapshot.rightPageLayout(viewContext) == null
            && viewport.right > currentScrollableAreaSnapshot.right
        ) {
            return fireEventReadComplete(
                abs(viewport.right - currentScrollableAreaSnapshot.right)
            )
        }

        if (layoutManagerSnapshot.topPageLayout(viewContext) == null
            && viewport.top < currentScrollableAreaSnapshot.top
        ) {
            return fireEventReadComplete(
                abs(currentScrollableAreaSnapshot.top - viewport.top)
            )
        }

        if (layoutManagerSnapshot.bottomPageLayout(viewContext) == null
            && viewport.bottom > currentScrollableAreaSnapshot.bottom
        ) {
            return fireEventReadComplete(
                abs(viewport.bottom - currentScrollableAreaSnapshot.bottom)
            )
        }
        return false
    }

    internal fun fireEventReadComplete(overScroll: Float = pagingTouchSlop): Boolean {
        if (overScroll < pagingTouchSlop) {
            return false
        }

        onReadCompleteListenerList.forEach {
            it.onReadCompleted(this)
        }

        return true
    }

    private fun startAnimation() {
        ViewCompat.postInvalidateOnAnimation(this)
    }

    private fun abortAnimation() {
        scroller.abortAnimation()
        operation = null
    }

    override fun computeScroll() {
        super.computeScroll()

        if (!isInitialized) {
            return
        }

        if (!scroller.isFinished && scroller.computeScrollOffset()) {
            viewContext.offsetTo(scroller.currX.toFloat(), scroller.currY.toFloat())
        }

        val needPostInvalidate = operateAnimate(operation)

        val needPostInvalidateScroll = !scroller.isFinished || needPostInvalidate

        if (!needPostInvalidateScroll && scrollState == SCROLL_STATE_SETTLING) {
            scrollState = SCROLL_STATE_IDLE
        }

        if (needPostInvalidate || needPostInvalidateScroll) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun operateAnimate(operation: Operation?): Boolean {
        operation ?: return false
        if (operation.scale == null && operation.translate == null) {
            return false
        }

        val input = operation.elapsed.toFloat() / operation.durationMillis

        operation.scale?.also { scaleOperation ->
            val focusX: Float
            val focusY: Float

            if (scaleOperation.focusX != null && scaleOperation.focusY != null) {
                focusX = scaleOperation.focusX
                focusY = scaleOperation.focusY

            } else {
                viewContext.projectToScreenPosition(
                    viewContext.viewport.centerX,
                    viewContext.viewport.centerY,
                    tmpEventPoint
                )
                focusX = tmpEventPoint.left
                focusY = tmpEventPoint.top
            }

            if (input >= 1.0F) {
                viewContext.scaleTo(
                    scaleOperation.to,
                    focusX,
                    focusY,
                    currentScrollableArea,
                    applyImmediately = false
                )
                operation.scale = null
            } else {
                val factor = scaleInterpolator.getInterpolation(input)
                val newScale = scaleOperation.from + scaleOperation.diff * factor
                viewContext.scaleTo(
                    newScale,
                    focusX,
                    focusY,
                    currentScrollableArea,
                    applyImmediately = false
                )
            }
        }

        operation.translate?.also { translateOperation ->
            if (input >= 1.0F) {
                viewContext.offsetTo(
                    translateOperation.destX,
                    translateOperation.destY,
                    currentScrollableArea,
                    applyImmediately = false
                )
                operation.translate = null
            } else {
                val factor = translateInterpolator.getInterpolation(input)
                val newX = translateOperation.startX + translateOperation.diffX * factor
                val newY = translateOperation.startY + translateOperation.diffY * factor
                viewContext.offsetTo(newX, newY, currentScrollableArea, applyImmediately = false)
            }
        }

        viewContext.applyViewport()

        if (operation.isFinished) {
            operation.onOperationEnd()
        }

        return !operation.isFinished
    }

    override fun onShowPress(e: MotionEvent?) {
        Log.d(TAG, "onShowPress")
        abortAnimation()
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        e ?: return false

        // mapping global point
        val globalPosition = viewContext.projectToGlobalPosition(x, y, tmpEventPoint)

        onTapListenerList.forEach {
            handleOnTapListener(it, e.x, e.y, globalPosition)
        }

        return true
    }

    private fun handleOnTapListener(
        onTapListener: OnTapListener,
        x: Float,
        y: Float,
        globalPosition: Rectangle
    ) {
        var handled = onTapListener.onTap(this, x, y)
        if (handled) {
            return
        }

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
    }

    override fun onDown(e: MotionEvent?): Boolean = true

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val handled = fling(velocityX, velocityY)
        if (!handled) {
            populateToCurrent()
        }

        return handled
    }

    internal var currentPageLayout: PageLayout? = null
        private set(value) {
            if (value == null || field == value) {
                return
            }

            field = value

            if (!value.containsPage(currentPageIndex)) {
                currentPageIndex = value.keyPage?.index ?: 0
                Log.d(TAG, "Update currentPageIndex $currentPageIndex")
            }

            onPageChangeListenerList.forEach {
                it.onPageLayoutSelected(this, value)
            }
        }

    private fun fling(velocityX: Float, velocityY: Float): Boolean {
        val layoutManagerSnapshot = layoutManager ?: return false

        val scaledVelocityX = velocityX / viewContext.currentScale
        val scaledVelocityY = velocityY / viewContext.currentScale

        val currentScrollAreaSnapshot = currentScrollableArea ?: return false

        val populateHelper = layoutManagerSnapshot.populateHelper
            .init(
                viewContext,
                layoutManagerSnapshot,
                pagingTouchSlop
            )

        val horizontal = (abs(scaledVelocityX) > abs(scaledVelocityY))

        val translateOperation = if (horizontal) {
            val leftRect = layoutManagerSnapshot.leftPageLayout(viewContext, currentPageLayout)
            val rightRect = layoutManagerSnapshot.rightPageLayout(viewContext, currentPageLayout)

            if (scaledVelocityX > 0.0F && leftRect != null
                && !viewContext.canScrollLeft(currentScrollAreaSnapshot)
            ) {
                populateHelper.populateToLeft(leftRect)
            } else if (scaledVelocityX < 0.0F && rightRect != null
                && !viewContext.canScrollRight(currentScrollAreaSnapshot)
            ) {
                populateHelper.populateToRight(rightRect)
            } else {
                null
            }
        } else {
            val topRect = layoutManagerSnapshot.topPageLayout(viewContext, currentPageLayout)
            val bottomRect = layoutManagerSnapshot.bottomPageLayout(viewContext, currentPageLayout)

            if (scaledVelocityY > 0.0F && topRect != null
                && !viewContext.canScrollTop(currentScrollAreaSnapshot)
            ) {
                populateHelper.populateToTop(topRect)
            } else if (scaledVelocityY < 0.0F && bottomRect != null
                && !viewContext.canScrollBottom(currentScrollAreaSnapshot)
            ) {
                populateHelper.populateToBottom(bottomRect)
            } else {
                null
            }
        }

        if (translateOperation != null) {
            operation = Operation(
                translate = translateOperation,
                scale = Operation.Scale(
                    viewContext.currentScale,
                    viewContext.minScale,
                    null,
                    null
                ),
                startTimeMillis = System.currentTimeMillis(),
                SCROLLING_DURATION
            )
            scrollState = SCROLL_STATE_SETTLING
            startAnimation()
            return true
        }

        val viewport = viewContext.viewport

        val minX = currentScrollAreaSnapshot.left.roundToInt()
        val maxX = (currentScrollAreaSnapshot.right - viewport.width).roundToInt()

        val minY = currentScrollAreaSnapshot.top.roundToInt()
        val maxY = (currentScrollAreaSnapshot.bottom - viewport.height).roundToInt()

        // Do not fling if over-scrolled
        if (horizontal
            && (viewport.left < currentScrollAreaSnapshot.left
                    || viewport.right > currentScrollAreaSnapshot.right)
        ) {
            return false
        } else if (viewport.top < currentScrollAreaSnapshot.top
            || viewport.bottom > currentScrollAreaSnapshot.bottom
        ) {
            return false
        }

        scroller.fling(
            viewContext.currentX.roundToInt(),
            viewContext.currentY.roundToInt(),
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
        viewContext.scroll(
            distanceX / viewContext.currentScale,
            distanceY / viewContext.currentScale,
            currentScrollableArea
        )

        scrollState = SCROLL_STATE_DRAGGING

        return true
    }

    override fun onLongPress(e: MotionEvent?) {
        Log.d(TAG, "onLongPress")
    }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        detector ?: return false

        scalingState = ScalingState.Begin

        return true
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        detector ?: return false

        Log.d(TAG, "onScale focusX:${detector.focusX} ,focusY:${detector.focusY}")
        scalingState = ScalingState.Scaling
        viewContext.scale(
            detector.scaleFactor,
            detector.focusX, detector.focusY,
            currentScrollableArea
        )

        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        detector ?: return

        scalingState = ScalingState.End
        populateToCurrent()
    }

    internal fun scale(
        scale: Float,
        focusX: Float?,
        focusY: Float?,
        smoothScale: Boolean = false,
    ) {
        if (!smoothScale) {
            viewContext.projectToScreenPosition(
                viewContext.viewport.centerX,
                viewContext.viewport.centerY,
                tmpEventPoint
            )
            viewContext.scaleTo(
                scale,
                focusX ?: tmpEventPoint.centerX,
                focusY ?: tmpEventPoint.centerY,
                currentScrollableArea
            )
            postInvalidate()
            return
        }

        operation = Operation(
            scale = Operation.Scale(
                viewContext.currentScale, scale, focusX, focusY
            ),
            startTimeMillis = System.currentTimeMillis(),
            durationMillis = SCALING_DURATION,
            priority = -1
        ) {
            populateToCurrent()
        }
        startAnimation()
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        e ?: return false

        // mapping global point
        val globalPosition = viewContext.projectToGlobalPosition(e.x, e.y, tmpEventPoint)

        onDoubleTapListenerList.forEach {
            handleOnDoubleTapListener(it, e.x, e.y, globalPosition)
        }

        return true
    }

    private fun handleOnDoubleTapListener(
        onDoubleTapListener: OnDoubleTapListener,
        x: Float,
        y: Float,
        globalPosition: Rectangle
    ) {
        var handled = onDoubleTapListener.onDoubleTap(this, x, y)
        if (handled) {
            return
        }

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
    }

    override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
        e ?: return false
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        e ?: return false

        return true
    }
}
