package dev.ujhhgtg.wekit.features.items.chat

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Info
import dev.ujhhgtg.wekit.R
import dev.ujhhgtg.wekit.features.api.core.WeApi
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.features.api.ui.WeChatMessageContextMenuApi
import dev.ujhhgtg.wekit.features.core.FeatureCategoryIds
import androidx.activity.ComponentActivity
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.preferences.WePrefs.Companion.prefOption
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import java.util.concurrent.TimeUnit

object GroupChatAnalysis : ClickableFeature(), WeChatMessageContextMenuApi.IMenuItemsProvider {
    override val technicalId = "群聊分析"
    override val nameRes = R.string.feature_group_chat_analysis_name
    override val categoryIds = listOf(FeatureCategoryIds.CHAT)
    override val descriptionRes = R.string.feature_group_chat_analysis_description

    private const val TAG = "GroupChatAnalysis"
    private const val MENU_ID = 777032
    private val blankIcon = ColorDrawable(Color.TRANSPARENT)
    private var apiUrl by prefOption("group_chat_analysis_api_url", "https://api.openai.com/v1/chat/completions")
    private var apiKey by prefOption("group_chat_analysis_api_key", "")
    private var model by prefOption("group_chat_analysis_model", "gpt-4o-mini")
    private var days by prefOption("group_chat_analysis_days", 7)
    private var messageLimit by prefOption("group_chat_analysis_message_limit", 1000)

    override fun onEnable() = WeChatMessageContextMenuApi.addProvider(this)
    override fun onDisable() = WeChatMessageContextMenuApi.removeProvider(this)

    override fun onClick(context: ComponentActivity) = showSettingsDialog(context)

    override fun getMenuItems() = listOf(
        WeChatMessageContextMenuApi.MenuItem(
            id = MENU_ID,
            text = localizedChatString(R.string.group_chat_analysis_menu),
            drawable = blankIcon,
            imageVector = MaterialSymbols.Outlined.Info,
            isSupported = { it.talker.endsWith("@chatroom") },
            multiSelect = WeChatMessageContextMenuApi.MultiSelectSupport.Unsupported,
        ) { view, chattingContext, message ->
            showAnalysisDialog(view, chattingContext.activity, message.talker)
        },
    )

    private fun showAnalysisDialog(view: View, context: android.content.Context, groupId: String) {
        showComposeDialog(context) {
            val scope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var summarizing by remember { mutableStateOf(false) }
            var stats by remember { mutableStateOf<GroupStats?>(null) }
            var summary by remember { mutableStateOf("") }
            var extraPrompt by remember { mutableStateOf("") }
            var loadStarted by remember { mutableStateOf(false) }

            if (!loadStarted) {
                loadStarted = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { loadStats(groupId) } }
                    loading = false
                    result.onSuccess { stats = it }.onFailure {
                        WeLogger.e(TAG, "failed to load group stats", it)
                        showToast(it.message ?: context.getString(R.string.group_chat_analysis_load_failed))
                    }
                }
            }

            fun summarize() {
                val current = stats ?: return
                if (apiKey.isBlank() || apiUrl.isBlank() || model.isBlank()) {
                    showToast(context.getString(R.string.group_chat_analysis_config_required))
                    return
                }
                scope.launch {
                    summarizing = true
                    val result = withContext(Dispatchers.IO) {
                        runCatching { GroupAnalysisApi.summarize(apiUrl, apiKey, model, current.transcript, extraPrompt) }
                    }
                    summarizing = false
                    val error = result.exceptionOrNull()
                    if (error is CancellationException) throw error
                    result.onSuccess { summary = it }.onFailure {
                        WeLogger.e(TAG, "AI summary failed", it)
                        showToast(it.message ?: context.getString(R.string.group_chat_analysis_summary_failed))
                    }
                }
            }

            AlertDialogContent(
                title = { Text(stringResource(R.string.feature_group_chat_analysis_name)) },
                text = {
                    Column(
                        Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            stats?.let { data ->
                                Text(data.groupName, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.group_chat_analysis_period, days))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Metric(stringResource(R.string.group_chat_analysis_total_members), data.totalMembers.toString())
                                    Metric(stringResource(R.string.group_chat_analysis_speaking_members), data.speakingMembers.toString())
                                    Metric(stringResource(R.string.group_chat_analysis_messages), data.messageCount.toString())
                                }
                                Text(stringResource(R.string.group_chat_analysis_activity, data.activityRate))
                                if (data.ranking.isNotEmpty()) {
                                    Text(stringResource(R.string.group_chat_analysis_ranking), style = MaterialTheme.typography.titleSmall)
                                    data.ranking.forEachIndexed { index, item ->
                                        Text("${index + 1}. ${item.name} · ${item.count}")
                                    }
                                }
                                OutlinedTextField(
                                    value = extraPrompt,
                                    onValueChange = { extraPrompt = it.take(500) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.group_chat_analysis_extra_prompt)) },
                                    minLines = 2,
                                )
                                Button(::summarize, enabled = !summarizing && data.transcript.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.group_chat_analysis_generate_summary))
                                }
                                if (summarizing) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                                if (summary.isNotBlank()) {
                                    Text(stringResource(R.string.group_chat_analysis_summary), style = MaterialTheme.typography.titleSmall)
                                    Text(summary)
                                }
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton({
                        onDismiss()
                        view.postOnAnimation { showSettingsDialog(context) }
                    }) { Text(stringResource(R.string.group_chat_analysis_settings)) }
                },
                confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.dialog_close)) } },
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun Metric(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }

    private fun showSettingsDialog(context: android.content.Context) {
        showComposeDialog(context) {
            var draftUrl by remember { mutableStateOf(apiUrl) }
            var draftKey by remember { mutableStateOf(apiKey) }
            var draftModel by remember { mutableStateOf(model) }
            var draftDays by remember { mutableStateOf(days.toString()) }
            var draftLimit by remember { mutableStateOf(messageLimit.toString()) }
            AlertDialogContent(
                title = { Text(stringResource(R.string.group_chat_analysis_settings)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(draftUrl, { draftUrl = it.trim() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_chat_analysis_api_url)) })
                        OutlinedTextField(draftKey, { draftKey = it.trim() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_chat_analysis_api_key)) })
                        OutlinedTextField(draftModel, { draftModel = it.trim() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_chat_analysis_model)) })
                        OutlinedTextField(draftDays, { draftDays = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_chat_analysis_days)) })
                        OutlinedTextField(draftLimit, { draftLimit = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_chat_analysis_limit)) })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
                confirmButton = {
                    Button({
                        apiUrl = draftUrl
                        apiKey = draftKey
                        model = draftModel
                        days = draftDays.toIntOrNull()?.coerceIn(1, 365) ?: 7
                        messageLimit = draftLimit.toIntOrNull()?.coerceIn(100, 10000) ?: 1000
                        onDismiss()
                    }) { Text(stringResource(R.string.dialog_confirm)) }
                },
            )
        }
    }

    private fun loadStats(groupId: String): GroupStats {
        check(WeDatabaseApi.isReady) { "微信数据库尚未就绪" }
        val startTime = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days.coerceIn(1, 365)) }.timeInMillis
        val escaped = groupId.replace("'", "''")
        val roomRows = WeDatabaseApi.executeQuery(
            "SELECT memberlist FROM chatroom WHERE chatroomname = '$escaped'",
        )
        check(roomRows.isNotEmpty()) { "无法读取群成员数据" }
        val memberIds = roomRows.first()["memberlist"]?.toString().orEmpty()
            .split(';').filter(String::isNotBlank)
        val totalMembers = memberIds.size
        val rows = WeDatabaseApi.executeQuery(
            "SELECT content, isSend, createTime FROM message WHERE talker = '$escaped' AND type = 1 AND createTime >= $startTime ORDER BY createTime ASC",
        )
        val counts = linkedMapOf<String, Int>()
        val lines = ArrayList<String>(rows.size)
        rows.forEach { row ->
            val isSend = (row["isSend"] as? Number)?.toInt() ?: 0
            val raw = row["content"]?.toString().orEmpty()
            val senderId = if (isSend != 0) WeApi.selfWxId else extractGroupSender(raw)
            if (senderId.isBlank()) return@forEach
            val text = if (isSend != 0) raw else stripGroupPrefix(raw)
            if (text.isBlank()) return@forEach
            counts[senderId] = (counts[senderId] ?: 0) + 1
            lines += "${displayName(groupId, senderId)}: $text"
        }
        val sampled = uniformSample(lines, messageLimit.coerceIn(100, 10000))
        val ranking = counts.entries.sortedByDescending { it.value }.take(10).map {
            Ranking(displayName(groupId, it.key), it.value)
        }
        val speakingMembers = counts.keys.count { id -> id == WeApi.selfWxId || memberIds.contains(id) }
        val rate = if (totalMembers == 0) 0 else (speakingMembers * 100 / totalMembers).coerceIn(0, 100)
        return GroupStats(
            groupName = WeDatabaseApi.getGroup(groupId)?.nickname?.ifBlank { groupId } ?: groupId,
            totalMembers = totalMembers,
            speakingMembers = speakingMembers,
            messageCount = rows.size,
            activityRate = rate,
            ranking = ranking,
            transcript = sampled.joinToString("\n"),
        )
    }

    private fun extractGroupSender(content: String): String {
        val separator = content.indexOf(":\n").takeIf { it > 0 } ?: content.indexOf(':').takeIf { it > 0 } ?: return ""
        return content.substring(0, separator).trim()
    }

    private fun stripGroupPrefix(content: String): String {
        val marker = content.indexOf(":\n")
        if (marker > 0) return content.substring(marker + 2)
        val colon = content.indexOf(':')
        return if (colon > 0) content.substring(colon + 1).trimStart() else content
    }

    private fun displayName(groupId: String, senderId: String): String =
        if (senderId == WeApi.selfWxId) "我" else WeDatabaseApi.getGroupMemberDisplayName(groupId, senderId)
            .ifBlank { WeDatabaseApi.getFriend(senderId)?.remarkName.orEmpty() }
            .ifBlank { WeDatabaseApi.getFriend(senderId)?.nickname.orEmpty() }
            .ifBlank { senderId }

    private fun <T> uniformSample(source: List<T>, limit: Int): List<T> {
        if (source.size <= limit) return source
        if (limit <= 1) return listOf(source.last())
        val last = source.lastIndex.toDouble()
        return List(limit) { source[(it * last / (limit - 1)).toInt()] }
    }

    private data class Ranking(val name: String, val count: Int)
    private data class GroupStats(
        val groupName: String,
        val totalMembers: Int,
        val speakingMembers: Int,
        val messageCount: Int,
        val activityRate: Int,
        val ranking: List<Ranking>,
        val transcript: String,
    )
}

private object GroupAnalysisApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    @Serializable private data class ChatRequest(val model: String, val messages: List<ChatMessage>, val temperature: Double = 0.7)
    @Serializable private data class ChatMessage(val role: String, val content: String)
    @Serializable private data class ChatResponse(val choices: List<Choice> = emptyList())
    @Serializable private data class Choice(val message: ChatMessage)

    fun summarize(url: String, key: String, model: String, transcript: String, extra: String): String {
        val prompt = buildString {
            append("你是一个微信聊天分析助手。请根据以下聊天记录，总结这段时间内大家讨论的主要内容、重点话题、整体氛围，并提取有趣的点。语言幽默生动、排版清晰；记录较少时请简短回复。")
            if (extra.isNotBlank()) append("\n【用户额外要求】：").append(extra)
            append("\n\n聊天记录：\n").append(transcript)
        }
        val payload = json.encodeToString(ChatRequest(model, listOf(ChatMessage("user", prompt))))
        val request = Request.Builder().url(url).header("Authorization", "Bearer $key").post(payload.toRequestBody(mediaType)).build()
        val body = client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}: ${response.body.string().take(300)}" }
            response.body.string()
        }
        return json.decodeFromString<ChatResponse>(body).choices.firstOrNull()?.message?.content?.trim()
            ?.takeIf { it.isNotEmpty() } ?: error("AI 返回内容为空")
    }
}
