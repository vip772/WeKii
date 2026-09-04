package dev.ujhhgtg.wekit.features.items.scripting_java

/** Legacy callback types kept for old BeanShell source compatibility. */
public class PluginCallBack {
    public interface HttpCallback {
        fun onSuccess(statusCode: Int, response: String?)
        fun onError(error: Exception?)
    }
    public interface DownloadCallback {
        fun onSuccess(file: java.io.File?)
        fun onError(error: Exception?)
        fun onProgress(current: Long, total: Long)
    }
}
