package dev.ujhhgtg.wekit.features.api.net

import dev.ujhhgtg.wekit.constants.Preferences
import dev.ujhhgtg.wekit.features.api.net.abc.IWePacketInterceptor
import dev.ujhhgtg.wekit.utils.WeLogger
import java.util.concurrent.CopyOnWriteArrayList
object WePacketManager {

    private val listeners = CopyOnWriteArrayList<IWePacketInterceptor>()
    private val observers = CopyOnWriteArrayList<PacketObserver>()

    fun interface PacketObserver {
        fun onPacket(direction: String, uri: String, cgiId: Int, data: ByteArray, timestamp: Long)
    }

    fun addObserver(observer: PacketObserver) = observers.addIfAbsent(observer)
    fun removeObserver(observer: PacketObserver) = observers.remove(observer)
    fun hasObservers(): Boolean = observers.isNotEmpty()

    private fun notifyObservers(direction: String, uri: String, cgiId: Int, data: ByteArray) {
        if (observers.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        observers.forEach { observer ->
            runCatching { observer.onPacket(direction, uri, cgiId, data.copyOf(), timestamp) }
                .onFailure { WeLogger.e("WePacketObserver", "packet observer failed", it) }
        }
    }

    fun addInterceptor(interceptor: IWePacketInterceptor) = listeners.addIfAbsent(interceptor)

    fun removeInterceptor(interceptor: IWePacketInterceptor) = listeners.remove(interceptor)

    fun hasInterceptors(): Boolean = listeners.isNotEmpty()

    fun handleRequestTamper(uri: String, cgiId: Int, reqBytes: ByteArray): ByteArray? {
        notifyObservers("request", uri, cgiId, reqBytes)
        if (Preferences.verboseLog) {
            val data = WeProtoData.fromBytes(reqBytes)
            WeLogger.logChunkedI(
                "WePacketInterceptor.Request",
                "Request: $uri, CGI=$cgiId, LEN=${reqBytes.size}, Data=${data.toJsonObject()}, Stack=${WeLogger.currentStackTrace}"
            )
        }

        for (listener in listeners) {
            val tampered = listener.onRequest(uri, cgiId, reqBytes)
            if (tampered != null) return tampered
        }
        return null
    }

    fun handleResponseTamper(uri: String, cgiId: Int, respBytes: ByteArray): ByteArray? {
        notifyObservers("response", uri, cgiId, respBytes)
        if (Preferences.verboseLog) {
            val data = WeProtoData.fromBytes(respBytes)
            WeLogger.logChunkedI(
                "WePacketInterceptor.Response",
                "Response: $uri, CGI=$cgiId, LEN=${respBytes.size}, Data=${data.toJsonObject()}"
            )
        }
        for (listener in listeners) {
            val tampered = listener.onResponse(uri, cgiId, respBytes)
            if (tampered != null) return tampered
        }
        return null
    }
}
