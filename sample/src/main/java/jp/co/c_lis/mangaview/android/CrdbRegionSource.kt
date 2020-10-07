package jp.co.c_lis.mangaview.android

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.util.JsonReader
import android.util.Log
import dev.keiji.mangaview.Region
import dev.keiji.mangaview.source.RegionSource
import dev.keiji.mangaview.widget.ViewContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL

/**
 * CRDB(Comic Region Database).
 */
class CrdbRegionSource(
    private val assetManager: AssetManager,
    private val catalogFileName: String,
    private val fileName: String,
    private val tmpDir: File,
    private val coroutineScope: CoroutineScope
) : RegionSource() {
    companion object {
        val TAG = CrdbRegionSource::class.java.simpleName
    }

    override val regionList = ArrayList<Region>();

    override val contentWidth: Float
        get() = bitmapWidth

    override val contentHeight: Float
        get() = bitmapHeight

    @Volatile
    private var bitmapWidth: Float = -1.0F

    @Volatile
    private var bitmapHeight: Float = -1.0F

    override fun getState(viewContext: ViewContext): State {
        return State.Prepared
    }

    private val options = BitmapFactory.Options().also {
        it.inJustDecodeBounds = true
    }

    private var job: Job? = null

    private fun getRegionList() {

        // All CrdbRegionSource will read whole file every loading.
        // This implementation is completely for test use.
        val jsonStr = assetManager.open(catalogFileName)
            .bufferedReader()
            .readText()

        val jsonObj = JSONObject(jsonStr)
        if (!jsonObj.has(fileName)) {
            return
        }

        val fileObj = jsonObj.getJSONObject(fileName)
        val url = URL(fileObj.getString("url"))

        val imageId = url.file.split("/").last()
        val tmpFilePath = File(tmpDir, imageId)

        if (!tmpFilePath.exists() || tmpFilePath.length() == 0L) {
            FileOutputStream(tmpFilePath).use { outputStream ->
                val conn = url.openConnection().also {
                    it.connect()
                }
                conn.getInputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
            }
        }

        val regionsStr = FileInputStream(tmpFilePath)
            .bufferedReader()
            .readText()
        val regionArray = JSONObject(regionsStr)
            .getJSONArray("regions")

        for (index in (0 until regionArray.length())) {
            val regionObj = regionArray.getJSONObject(index)
            val categoryId = regionObj.getInt("category_id")
            val label = regionObj.getInt("label")
            val lineArray = regionObj.getJSONArray("lines")

            val pointArray = ArrayList<PointF>()
            var point: PointF? = null

            for (j in (0 until lineArray.length())) {
                val lineObj = lineArray.getJSONObject(j)
                if (point == null) {
                    point = PointF(
                        lineObj.getDouble("from_x").toFloat(),
                        lineObj.getDouble("from_y").toFloat()
                    )
                    pointArray.add(point)
                }

                point = PointF(
                    lineObj.getDouble("to_x").toFloat(),
                    lineObj.getDouble("to_y").toFloat()
                )
                pointArray.add(point)
            }

            regionList.add(Region(categoryId, label, pointList = pointArray))
        }
    }

    override fun prepare(viewContext: ViewContext, onImageSourceLoaded: () -> Unit): Boolean {

        if (bitmapWidth < 0 || bitmapHeight < 0) {
            if (job != null) {
                return false
            }

            job = coroutineScope.launch(Dispatchers.IO) {
                assetManager.open(fileName).use {
                    BitmapFactory.decodeStream(it, null, options)
                    bitmapWidth = options.outWidth.toFloat()
                    bitmapHeight = options.outHeight.toFloat()
                }

                getRegionList();

                job = null
            }

            return false
        }

        onImageSourceLoaded()

        return true
    }

    override fun recycle() {
        job?.cancel()
        job = null

        bitmapWidth = -1.0F
        bitmapHeight = -1.0F
        pathList.clear()
    }
}
