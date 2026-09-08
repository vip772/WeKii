package me.hd.wauxv.data.bean

import androidx.annotation.Keep

/** PL-compatible result delivered by sendProtobufPacket callbacks. */
@Suppress("unused")
@Keep
class ProtobufSendResultBean(
    private val success: Boolean,
    private val message: String,
) {
    fun isSuccess(): Boolean = success
    fun getMessage(): String = message

    override fun toString(): String =
        "SendResult(success=$success, message=$message)"
}
