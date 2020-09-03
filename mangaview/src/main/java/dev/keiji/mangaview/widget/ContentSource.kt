package dev.keiji.mangaview.widget

abstract class ContentSource {

    enum class State {
        NA,
        Preparing,
        Prepared,
    }

    abstract val contentWidth: Float
    abstract val contentHeight: Float

    abstract fun getState(viewContext: ViewContext): State

    abstract fun prepare(viewContext: ViewContext, onImageSourceLoaded: () -> Unit): Boolean

    abstract fun recycle()
}
