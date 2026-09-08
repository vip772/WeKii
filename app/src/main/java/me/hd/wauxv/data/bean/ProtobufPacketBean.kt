package me.hd.wauxv.data.bean

import androidx.annotation.Keep
import dev.ujhhgtg.wekit.features.api.net.WeProtoData
import org.json.JSONObject

@Suppress("unused")
@Keep
class ProtobufPacketBean(
    private val direction: String,
    private val uri: String,
    private val cgiId: Int,
    data: ByteArray,
    private val timestamp: Long = System.currentTimeMillis(),
) {
    private val data = data.copyOf()

    fun getDirection(): String = direction
    fun getUri(): String = if (uri == "null") "" else uri
    fun getCgiId(): Int = cgiId
    fun getData(): ByteArray = data.copyOf()
    fun getLength(): Int = data.size
    fun getJson(): String = runCatching { WeProtoData.fromBytes(data).toJsonObject().toString() }.getOrDefault("{}")
    fun getJsonObject(): JSONObject = runCatching { JSONObject(getJson()) }.getOrDefault(JSONObject())
    fun getTimestamp(): Long = timestamp
    fun isRequest(): Boolean = direction == DIRECTION_REQUEST
    fun isResponse(): Boolean = direction == DIRECTION_RESPONSE

    companion object {
        const val DIRECTION_REQUEST = "request"
        const val DIRECTION_RESPONSE = "response"
    }
}
