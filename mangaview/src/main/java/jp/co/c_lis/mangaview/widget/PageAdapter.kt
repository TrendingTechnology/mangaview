package jp.co.c_lis.mangaview.widget

abstract class PageAdapter(val pageWidth: Int, val pageHeight: Int) {

    abstract val pageCount: Int

    abstract fun getPage(index: Int): Page
}
