package dev.ujhhgtg.wekit.features.items.scripting_java

/** Legacy callback types kept for old BeanShell source compatibility. */
object PluginCallBack {
    open class HttpCallback {
        open fun onSuccess(statusCode: Int, response: String?) {}
        open fun onError(error: Exception?) {}
    }
    open class DownloadCallback {
        open fun onSuccess(file: java.io.File?) {}
        open fun onError(error: Exception?) {}
    }
}
