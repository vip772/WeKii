package me.hd.wauxv.data.bean.info

import androidx.annotation.Keep
import dev.ujhhgtg.wekit.features.api.core.models.WeContact

@Keep
data class FriendInfo(
    var wxid: String = "",
    var alias: String = "",
    var remark: String = "",
    var nickname: String = "",
    var type: Int = 0,
    var sourceExtInfo: String = "",
    var createTime: Long = 0,
    var avatarUrl: String = "",
    var avatarBackupUrl: String = "",
    var province: String = "",
    var city: String = "",
    var gender: Int = 0
) {
    // Kotlin properties already generate JavaBean getters such as getWxid(),
    // getNickname(), and getAvatarUrl(). Keep only compatibility aliases whose
    // JVM names differ from those generated accessors.
    fun getWxId(): String = wxid
    fun getUserName(): String = wxid
    fun getUsername(): String = wxid
    fun getNickName(): String = nickname
    fun getRemarkName(): String = remark
    fun getEncryptedUsername(): String = sourceExtInfo
    fun getRegion(): String = listOf(province, city).filter { it.isNotEmpty() }.joinToString(" ")
    fun getSex(): Int = gender

    fun getName(): String = when {
        remark.isNotEmpty() && nickname.isNotEmpty() -> "$remark ($nickname)"
        remark.isNotEmpty() -> remark
        nickname.isNotEmpty() -> nickname
        else -> wxid
    }
    fun getDisplayName(): String = getName()

    fun isGroup(): Boolean = wxid.endsWith("@chatroom") || wxid.endsWith("@im.chatroom")
    fun isOfficialAccount(): Boolean = wxid.startsWith("gh_")

    constructor(contact: WeContact) : this(
        wxid = contact.wxId,
        alias = contact.customWxId,
        remark = contact.remarkName,
        nickname = contact.nickname,
        type = contact.type,
        sourceExtInfo = contact.encryptedUsername,
        createTime = 0L,
        avatarUrl = contact.avatarUrl
    )
}
