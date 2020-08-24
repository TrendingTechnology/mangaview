package jp.co.c_lis.mangaview.android

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import dev.keiji.mangaview.widget.ContentLayer
import dev.keiji.mangaview.widget.Page
import dev.keiji.mangaview.widget.ViewContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AssetBitmapLayer(
    private val assetManager: AssetManager,
    private val fileName: String,
    private val coroutineScope: CoroutineScope,
) : ContentLayer() {

    companion object {
        private val TAG = AssetBitmapLayer::class.java.simpleName
    }

    @Volatile
    private var bitmap: Bitmap? = null

    override val contentWidth: Float
        get() = bitmap?.width?.toFloat() ?: 0.0F
    override val contentHeight: Float
        get() = bitmap?.height?.toFloat() ?: 0.0F

    override val isContentPrepared: Boolean
        get() = bitmap != null

    override fun onContentPrepared(viewContext: ViewContext, page: Page): Boolean {
        coroutineScope.launch(Dispatchers.IO) {
            bitmap = assetManager.open(fileName).use {
                BitmapFactory.decodeStream(it)
            }
        }

        return false
    }

    override fun onDraw(
        canvas: Canvas?,
        srcRect: Rect,
        dstRect: RectF,
        viewContext: ViewContext,
        paint: Paint
    ): Boolean {
        val bitmapSnapshot = bitmap ?: return false

        canvas?.drawBitmap(
            bitmapSnapshot,
            srcRect,
            dstRect,
            paint
        )

        return true
    }

    override fun onRecycled() {
        super.onRecycled()

        bitmap?.recycle()
        bitmap = null
    }
}
