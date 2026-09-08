package me.hd.wauxv.data.bean.info

import androidx.annotation.Keep
import dev.ujhhgtg.wekit.features.api.core.models.WeGroup

@Keep
data class GroupInfo(
    var roomId: String = "",
    var remark: String = "",
    var name: String = "",
    var groupData: GroupData = GroupData()
) {
    // roomId, remark, and name already expose getRoomId(), getRemark(), and
    // getName() to Java/BeanShell. Keep only additional PL compatibility names.
    fun getChatroomId(): String = roomId
    fun getWxid(): String = roomId
    fun getWxId(): String = roomId
    fun getUserName(): String = roomId
    fun getNickname(): String = name
    fun getNickName(): String = name
    fun getRemarkName(): String = remark

    fun getDisplayName(): String = when {
        remark.isNotEmpty() && remark != name -> if (name.isEmpty()) remark else "$remark ($name)"
        name.isNotEmpty() -> name
        remark.isNotEmpty() -> remark
        else -> roomId
    }
    fun getOwner(): String = groupData.owner
    fun getMemberList(): List<String> = groupData.memberIds
    fun getMemberCount(): Int = groupData.memberCount
    fun memberCount(): Int = groupData.memberCount
    fun getRawDisplayNames(): String = groupData.memberNames.joinToString(",")

    constructor(group: WeGroup) : this(
        roomId = group.wxId,
        remark = "",
        name = group.nickname,
        groupData = GroupData(group.wxId)
    )
}
