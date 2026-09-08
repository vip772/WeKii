package dev.ujhhgtg.wekit.features.items.scripting_java

import me.hd.wauxv.data.bean.MsgInfoBean
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MsgInfoBeanApiSurfaceTest {
    @Test
    fun plCompatibleMessageGettersRemainAvailable() {
        val methods = MsgInfoBean::class.java.methods.map { it.name }.toSet()
        val required = setOf(
            "getMsgId", "getType", "getMsgType", "getCreateTime", "getCreateTimeSeconds",
            "getMsgSvrId", "getTalker", "getTalkerId", "getSender", "getSendTalker",
            "getSenderId", "getContent", "getText", "getXml", "getMsgSource",
            "getAtUserList", "getSelfWxId", "getNativeUrl", "getMessage",
            "getStoredMessage", "getImageMsg", "getVideoMsg", "getQuoteMsg", "getPatMsg",
            "getFileMsg", "getTransferMsg",
        )
        assertTrue(methods.containsAll(required), "MsgInfoBean is missing PL-compatible getters: ${required - methods}")
    }

    @Test
    fun plCompatibleMessagePredicatesRemainAvailable() {
        val methods = MsgInfoBean::class.java.methods.map { it.name }.toSet()
        val required = setOf(
            "isSend", "isSelf", "isPrivateChat", "isGroupChat", "isChatroom",
            "isImChatroom", "isOfficialAccount", "isText", "isImage", "isVoice",
            "isVideo", "isEmoji", "isLocation", "isApp", "isAppMsg", "isSystem",
            "isRedPacket", "isRedBag", "isTransfer", "isQuote", "isFile", "isLink",
            "isMusic", "isNote", "isShareCard", "isPat", "isRecalled", "isAtMe",
            "isNotifyAll", "isAnnounceAll",
        )
        assertTrue(methods.containsAll(required), "MsgInfoBean is missing PL-compatible predicates: ${required - methods}")
    }
}
