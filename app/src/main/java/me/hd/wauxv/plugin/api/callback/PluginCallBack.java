package me.hd.wauxv.plugin.api.callback;

import java.io.File;

/**
 * Public callback API kept source-compatible with the pl script runtime.
 * BeanShell plugins can instantiate the nested callback types directly.
 */
public final class PluginCallBack {
    private PluginCallBack() {
    }

    public interface HttpCallback {
        void onSuccess(int statusCode, String response);
        void onError(Exception e);
    }

    public interface DownloadCallback {
        void onSuccess(File file);
        void onError(Exception e);
        default void onProgress(int progress) {
        }
    }
}
