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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.ujhhgtg.wekit.agent.data.WeAgentRepository
import dev.ujhhgtg.wekit.agent.data.entity.ModelEntity
import dev.ujhhgtg.wekit.agent.data.entity.ModelProviderType
import dev.ujhhgtg.wekit.agent.model.local.LocalLlamaModels
import dev.ujhhgtg.wekit.agent.data.WeAgentSettings
import dev.ujhhgtg.wekit.agent.model.LlmMessage
import dev.ujhhgtg.wekit.agent.model.LlmRole
import dev.ujhhgtg.wekit.agent.model.LlmStreamEvent
import dev.ujhhgtg.wekit.agent.model.ModelProviderManager
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.features.api.core.models.MessageInfo
import dev.ujhhgtg.wekit.features.api.ui.WeChatMessageContextMenuApi
import dev.ujhhgtg.wekit.features.core.FeatureCategoryIds
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs.Companion.prefOption
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button as DialogButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object GroupChatAnalysis : SwitchFeature(), WeChatMessageContextMenuApi.IMenuItemsProvider {
    override val technicalId = "群聊分析"
    override val nameRes = R.string.feature_group_chat_analysis_name
    override val categoryIds = listOf(FeatureCategoryIds.CHAT)
    override val descriptionRes = R.string.feature_group_chat_analysis_description
    override val defaultEnabled = false

    private const val MENU_ID = 777032
    private const val SAMPLE_LIMIT = 5000
    private var selectedModelId by prefOption("group_chat_analysis_model_id", "")
    private val blankIcon = ColorDrawable(Color.TRANSPARENT)

    override fun onEnable() = WeChatMessageContextMenuApi.addProvider(this)
    override fun onDisable() = WeChatMessageContextMenuApi.removeProvider(this)

    override fun getMenuItems() = listOf(
        WeChatMessageContextMenuApi.MenuItem(
            id = MENU_ID,
            text = localizedChatString(R.string.group_chat_analysis_menu),
            drawable = blankIcon,
            imageVector = MaterialSymbols.Outlined.Info,
            isSupported = { it.talker.endsWith("@chatroom") || it.talker.endsWith("@im.chatroom") },
            multiSelect = WeChatMessageContextMenuApi.MultiSelectSupport.Unsupported,
        ) { view, chattingContext, message ->
            showAnalysisDialog(view, chattingContext, message)
        },
    )

    private fun showAnalysisDialog(
        @Suppress("UNUSED_PARAMETER") anchor: View,
        chattingContext: WeChatMessageContextMenuApi.ChattingContext,
        message: MessageInfo,
    ) {
        showComposeDialog(chattingContext.activity) {
            val scope = rememberCoroutineScope()
            var range by remember { mutableStateOf(AnalysisRange.MONTH) }
            var models by remember { mutableStateOf(emptyList<ModelEntity>()) }
            var model by remember { mutableStateOf<ModelEntity?>(null) }
            var stats by remember { mutableStateOf<GroupAnalysisStats?>(null) }
            var report by remember { mutableStateOf("") }
            var extraRequirement by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                models = withContext(Dispatchers.IO) { WeAgentRepository.getAllModels() }
                model = models.firstOrNull { it.id == selectedModelId } ?: models.firstOrNull()
            }

            AlertDialogContent(
                title = { Text(stringResource(R.string.group_chat_analysis_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 620.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PickerButton(
                            text = stringResource(range.labelRes),
                            options = AnalysisRange.entries,
                            optionText = { stringResource(it.labelRes) },
                            onSelect = { range = it },
                        )
                        PickerButton(
                            text = model?.displayName ?: stringResource(R.string.group_chat_analysis_no_model),
                            options = models,
                            optionText = { it.displayName },
                            onSelect = {
                                model = it
                                selectedModelId = it.id
                            },
                        )
                        OutlinedTextField(
                            value = extraRequirement,
                            onValueChange = { extraRequirement = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 4,
                            label = { Text(stringResource(R.string.group_chat_analysis_extra_requirement)) },
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            onClick = {
                                busy = true
                                error = null
                                report = ""
                                scope.launch {
                                    runCatching {
                                        val loaded = withContext(Dispatchers.IO) {
                                            GroupChatAnalysisEngine.load(message.talker, range)
                                        }
                                        stats = loaded.stats
                                        model?.let { selected ->
                                            GroupChatAnalysisEngine.streamReport(
                                                selected,
                                                loaded.messages,
                                                extraRequirement,
                                            ) { delta -> report += delta }
                                        }
                                    }.onFailure { error = it.message ?: it.javaClass.simpleName }
                                    busy = false
                                }
                            },
                        ) {
                            if (busy) CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(stringResource(R.string.group_chat_analysis_generate))
                        }
                        stats?.let { StatsContent(it) }
                        if (report.isNotBlank()) {
                            HorizontalDivider()
                            Text(stringResource(R.string.group_chat_analysis_ai_report), style = MaterialTheme.typography.titleMedium)
                            Text(report)
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = { DialogButton(onDismiss) { Text(stringResource(R.string.dialog_close)) } },
            )
        }
    }

    @Composable
    private fun <T> PickerButton(
        text: String,
        options: List<T>,
        optionText: @Composable (T) -> String,
        onSelect: (T) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        Column {
            Button(modifier = Modifier.fillMaxWidth(), onClick = { expanded = true }) { Text(text) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionText(option)) },
                        onClick = { expanded = false; onSelect(option) },
                    )
                }
            }
        }
    }

    @Composable
    private fun StatsContent(stats: GroupAnalysisStats) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.group_chat_analysis_core_metrics), style = MaterialTheme.typography.titleMedium)
            MetricRow(
                stringResource(R.string.group_chat_analysis_total_messages), stats.totalMessages.toString(),
                stringResource(R.string.group_chat_analysis_active_users), stats.activeUsers.toString(),
            )
            MetricRow(
                stringResource(R.string.group_chat_analysis_text_messages), stats.textMessages.toString(),
                stringResource(R.string.group_chat_analysis_at_me), stats.atMeMessages.toString(),
            )
            Text(stringResource(R.string.group_chat_analysis_active_ranking), style = MaterialTheme.typography.titleMedium)
            stats.ranking.take(10).forEachIndexed { index, item -> Text("${index + 1}. ${item.first}: ${item.second}") }
            Text(stringResource(R.string.group_chat_analysis_routine), style = MaterialTheme.typography.titleMedium)
            MetricRow(
                stringResource(R.string.group_chat_analysis_early_bird), stats.earlyBird.toString(),
                stringResource(R.string.group_chat_analysis_night_owl), stats.nightOwl.toString(),
            )
            Text(stringResource(R.string.group_chat_analysis_emotion), style = MaterialTheme.typography.titleMedium)
            Text("${stringResource(R.string.group_chat_analysis_laugh)} ${stats.laugh} · ${stringResource(R.string.group_chat_analysis_question)} ${stats.question} · ${stringResource(R.string.group_chat_analysis_exclamation)} ${stats.exclamation} · ${stringResource(R.string.group_chat_analysis_speechless)} ${stats.speechless}")
            Text(stringResource(R.string.group_chat_analysis_length), style = MaterialTheme.typography.titleMedium)
            Text("1–5: ${stats.tiny} · 6–20: ${stats.short} · 21–50: ${stats.medium} · 50+: ${stats.long}")
            Text(stringResource(R.string.group_chat_analysis_content_preference), style = MaterialTheme.typography.titleMedium)
            Text(stats.typeStats.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key}: ${it.value}" })
        }
    }

    @Composable
    private fun MetricRow(firstLabel: String, firstValue: String, secondLabel: String, secondValue: String) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(firstLabel, firstValue, Modifier.weight(1f))
            MetricCard(secondLabel, secondValue, Modifier.weight(1f))
        }
    }

    @Composable
    private fun MetricCard(label: String, value: String, modifier: Modifier) {
        Card(modifier) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleLarge)
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private enum class AnalysisRange(val labelRes: Int, val days: Int) {
    TODAY(R.string.group_chat_analysis_today, 1),
    WEEK(R.string.group_chat_analysis_week, 7),
    MONTH(R.string.group_chat_analysis_month, 30),
    YEAR(R.string.group_chat_analysis_year, 365),
    ALL(R.string.group_chat_analysis_all, 0),
}

private data class AnalysisMessage(val sender: String, val content: String, val createTime: Long)
private data class LoadedAnalysis(val stats: GroupAnalysisStats, val messages: List<AnalysisMessage>)
private data class GroupAnalysisStats(
    val totalMessages: Int,
    val textMessages: Int,
    val activeUsers: Int,
    val atMeMessages: Int,
    val ranking: List<Pair<String, Int>>,
    val earlyBird: Int,
    val nightOwl: Int,
    val laugh: Int,
    val question: Int,
    val exclamation: Int,
    val speechless: Int,
    val tiny: Int,
    val short: Int,
    val medium: Int,
    val long: Int,
    val typeStats: Map<String, Int>,
)

private object GroupChatAnalysisEngine {
    suspend fun load(talker: String, range: AnalysisRange): LoadedAnalysis {
        val start = if (range.days == 0) 0L else System.currentTimeMillis() - range.days * 86_400_000L
        val rows = ArrayList<AnalysisMessage>()
        val ranking = linkedMapOf<String, Int>()
        val typeStats = linkedMapOf<String, Int>()
        var total = 0
        var text = 0
        var atMe = 0
        var earlyBird = 0
        var nightOwl = 0
        var laugh = 0
        var question = 0
        var exclamation = 0
        var speechless = 0
        var tiny = 0
        var short = 0
        var medium = 0
        var long = 0
        val sql = buildString {
            append("SELECT content,createTime,type,isSend FROM message WHERE talker=?")
            if (start > 0) append(" AND createTime>=?")
            append(" ORDER BY createTime DESC LIMIT $SAMPLE_LIMIT")
        }
        val args = if (start > 0) arrayOf<Any>(talker, start) else arrayOf<Any>(talker)
        WeDatabaseApi.rawQuery(sql, args).use { cursor ->
            val contentIndex = cursor.getColumnIndexOrThrow("content")
            val timeIndex = cursor.getColumnIndexOrThrow("createTime")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val sendIndex = cursor.getColumnIndexOrThrow("isSend")
            while (cursor.moveToNext()) {
                total++
                val raw = cursor.getString(contentIndex).orEmpty()
                val createTime = cursor.getLong(timeIndex)
                val type = cursor.getInt(typeIndex)
                val isSend = cursor.getInt(sendIndex) != 0
                val sender = if (isSend) localizedSenderMe() else raw.substringBefore(":\n", raw.substringBefore(':', localizedSenderOther()))
                ranking[sender] = (ranking[sender] ?: 0) + 1
                val typeName = messageTypeName(type)
                typeStats[typeName] = (typeStats[typeName] ?: 0) + 1
                val hour = Calendar.getInstance().apply { timeInMillis = createTime }.get(Calendar.HOUR_OF_DAY)
                if (hour in 5..8) earlyBird++
                if (hour in 0..4 || hour in 19..23) nightOwl++
                if (raw.contains("@") && isSend.not()) atMe++
                if (type == 1) {
                    text++
                    val content = raw.substringAfter(":\n", raw)
                    if (content.contains("哈") || content.contains("笑")) laugh++
                    if (content.contains('?') || content.contains('？') || content.contains("吗")) question++
                    if (content.contains('!') || content.contains('！')) exclamation++
                    if (content.contains("...") || content.contains("。。。") || content.contains("无语")) speechless++
                    when (content.length) {
                        in 0..5 -> tiny++
                        in 6..20 -> short++
                        in 21..50 -> medium++
                        else -> long++
                    }
                    rows += AnalysisMessage(sender, content, createTime)
                }
            }
        }
        val stats = GroupAnalysisStats(
            total, text, ranking.size, atMe,
            ranking.entries.sortedByDescending { it.value }.map { it.key to it.value },
            earlyBird, nightOwl, laugh, question, exclamation, speechless,
            tiny, short, medium, long, typeStats,
        )
        return LoadedAnalysis(stats, rows.asReversed())
    }

    suspend fun streamReport(
        model: ModelEntity,
        messages: List<AnalysisMessage>,
        extraRequirement: String,
        onDelta: (String) -> Unit,
    ) {
        if (messages.isEmpty()) return
        val sampled = uniformlySample(messages, 1000)
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val transcript = sampled.joinToString("\n") {
            "[${formatter.format(Date(it.createTime))}] ${it.sender}: ${it.content}"
        }
        val prompt = buildString {
            append("请分析以下微信群聊记录，总结主要话题、重点事件、整体氛围和有趣观点，语言简洁生动、排版清晰。不要编造记录中没有的信息。")
            if (extraRequirement.isNotBlank()) append("\n用户额外要求：").append(extraRequirement.trim())
            append("\n\n聊天记录：\n").append(transcript)
        }
        val provider = WeAgentRepository.getModelProvider(model.providerId)
            ?: error("Model provider not found")
        val client = if (provider.type == ModelProviderType.LOCAL_LLAMA) {
            ModelProviderManager.localClientFor(
                provider,
                model.modelIdRemote,
                model.contextWindow ?: LocalLlamaModels.defaultContextWindow(model.modelIdRemote) ?: 32768,
                WeAgentSettings.localComputeBackend(),
            )
        } else {
            ModelProviderManager.clientFor(provider)
        }
        val request = ModelProviderManager.buildRequest(
            model,
            messages = listOf(
                LlmMessage(LlmRole.SYSTEM, "你是一个微信聊天分析助手。"),
                LlmMessage(LlmRole.USER, prompt),
            ),
            tools = emptyList(),
        )
        client.stream(request).collect { event ->
            when (event) {
                is LlmStreamEvent.TextDelta -> onDelta(event.text)
                is LlmStreamEvent.Failed -> throw event.error
                else -> Unit
            }
        }
    }

    private fun uniformlySample(source: List<AnalysisMessage>, limit: Int): List<AnalysisMessage> {
        if (source.size <= limit) return source
        return List(limit) { index ->
            source[((index.toLong() * source.lastIndex) / (limit - 1)).toInt()]
        }
    }

    private fun messageTypeName(type: Int): String = when (type) {
        1 -> "文本"
        3 -> "图片"
        34 -> "语音"
        43, 62 -> "视频"
        47 -> "表情"
        49 -> "卡片/文件"
        10000 -> "系统"
        else -> "其他"
    }

    private fun localizedSenderMe() = "我"
    private fun localizedSenderOther() = "对方"
}
