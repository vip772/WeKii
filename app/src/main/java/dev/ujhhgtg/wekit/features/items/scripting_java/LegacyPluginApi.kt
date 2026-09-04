package dev.ujhhgtg.wekit.features.items.scripting_java

/** Legacy callback types kept for old BeanShell source compatibility. */
object PluginCallBack {
    open class HttpCallback {
        @JvmOverloads open fun onSuccess(statusCode: Int, response: String?) {}
        @JvmOverloads open fun onError(error: Exception?) {}
    }
    open class DownloadCallback {
        @JvmOverloads open fun onSuccess(file: java.io.File?) {}
        @JvmOverloads open fun onError(error: Exception?) {}
    }
}
