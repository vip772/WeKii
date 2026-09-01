package dev.ujhhgtg.wekit.features.items.chat

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Refresh
import com.composables.icons.materialsymbols.outlined.Settings
import dev.ujhhgtg.wekit.R
import dev.ujhhgtg.wekit.agent.data.WeAgentRepository
import dev.ujhhgtg.wekit.agent.data.WeAgentSettings
import dev.ujhhgtg.wekit.agent.data.entity.*
import dev.ujhhgtg.wekit.agent.model.*
import dev.ujhhgtg.wekit.agent.model.local.LocalLlamaModels
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
import java.util.*

object GroupChatAnalysis : SwitchFeature(), WeChatMessageContextMenuApi.IMenuItemsProvider {
    override val technicalId = "群聊分析"
    override val nameRes = R.string.feature_group_chat_analysis_name
    override val categoryIds = listOf(FeatureCategoryIds.CHAT)
    override val descriptionRes = R.string.feature_group_chat_analysis_description
    override val defaultEnabled = false
    private const val MENU_ID = 777032
    private var selectedModelId by prefOption("group_chat_analysis_model_id", "")
    private val blankIcon = ColorDrawable(Color.TRANSPARENT)

    override fun onEnable() = WeChatMessageContextMenuApi.addProvider(this)
    override fun onDisable() = WeChatMessageContextMenuApi.removeProvider(this)
    override fun getMenuItems() = listOf(WeChatMessageContextMenuApi.MenuItem(
        id = MENU_ID, text = localizedChatString(R.string.group_chat_analysis_menu), drawable = blankIcon,
        imageVector = MaterialSymbols.Outlined.Info,
        isSupported = { it.talker.endsWith("@chatroom") || it.talker.endsWith("@im.chatroom") },
        multiSelect = WeChatMessageContextMenuApi.MultiSelectSupport.Unsupported,
    ) { view, context, message -> showAnalysisDialog(view, context, message) })

    private fun showAnalysisDialog(@Suppress("UNUSED_PARAMETER") anchor: View, chattingContext: WeChatMessageContextMenuApi.ChattingContext, message: MessageInfo) {
        showComposeDialog(chattingContext.activity) {
            val scope = rememberCoroutineScope()
            var range by remember { mutableStateOf(AnalysisRange.MONTH) }
            var models by remember { mutableStateOf(emptyList<ModelEntity>()) }
            var model by remember { mutableStateOf<ModelEntity?>(null) }
            var stats by remember { mutableStateOf<GroupAnalysisStats?>(null) }
            var report by remember { mutableStateOf("") }
            var extra by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            var insightExpanded by remember { mutableStateOf(true) }
            var settingsOpen by remember { mutableStateOf(false) }
            var settingsSeed by remember { mutableStateOf<ApiDraft?>(null) }

            suspend fun reloadModels() {
                models = withContext(Dispatchers.IO) { WeAgentRepository.getAllModelsOnce() }
                model = models.firstOrNull { it.id == selectedModelId } ?: models.firstOrNull()
            }
            LaunchedEffect(Unit) { reloadModels() }
            LaunchedEffect(range) {
                runCatching { withContext(Dispatchers.IO) { GroupChatAnalysisEngine.load(message.talker, range).stats } }
                    .onSuccess { stats = it }.onFailure { error = it.message }
            }

            AlertDialogContent(
                title = { Text(stringResource(R.string.group_chat_analysis_title)) },
                text = { Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stats?.let { CoreMetrics(it) }
                    ExpandableSection(stringResource(R.string.group_chat_analysis_smart_insight), insightExpanded, { insightExpanded = !insightExpanded }) {
                        Text(stringResource(R.string.group_chat_analysis_smart_summary), fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.group_chat_analysis_select_period), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { GroupChatAnalysisEngine.load(message.talker, range).stats } }.onSuccess { stats = it }.onFailure { error = it.message }; busy = false } }) {
                                Icon(MaterialSymbols.Outlined.Refresh, stringResource(R.string.group_chat_analysis_refresh))
                            }
                            IconButton(onClick = { scope.launch {
                                val selected = model
                                settingsSeed = if (selected == null) ApiDraft() else withContext(Dispatchers.IO) {
                                    val provider = WeAgentRepository.getModelProvider(selected.providerId)
                                    ApiDraft(provider?.id.orEmpty(), provider?.baseUrl.orEmpty(), provider?.apiKey.orEmpty(), selected.modelIdRemote)
                                }
                                settingsOpen = true
                            } }) { Icon(MaterialSymbols.Outlined.Settings, stringResource(R.string.group_chat_analysis_api_settings)) }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AnalysisRange.entries.forEach { item -> FilterChip(range == item, { range = item }, { Text(stringResource(item.labelRes)) }) }
                        }
                        OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(), minLines = 1, maxLines = 4, placeholder = { Text(stringResource(R.string.group_chat_analysis_extra_requirement_hint)) })
                        Button(Modifier.fillMaxWidth(), enabled = !busy && model != null, onClick = {
                            busy = true; error = null; report = ""
                            scope.launch { runCatching {
                                val loaded = withContext(Dispatchers.IO) { GroupChatAnalysisEngine.load(message.talker, range) }
                                stats = loaded.stats
                                GroupChatAnalysisEngine.streamReport(model!!, loaded.messages, extra) { report += it }
                            }.onFailure { error = it.message ?: it.javaClass.simpleName }; busy = false }
                        }) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.group_chat_analysis_generate_summary))
                        }
                        if (report.isBlank() && !busy) Text(stringResource(R.string.group_chat_analysis_summary_hint), style = MaterialTheme.typography.bodySmall)
                        if (report.isNotBlank()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.group_chat_analysis_ai_report), fontWeight = FontWeight.SemiBold); Text(report)
                            }
                        }
                    }
                    stats?.let { DeepCharts(it) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { DialogButton(onDismiss) { Text(stringResource(R.string.dialog_close)) } },
            )

            if (settingsOpen && settingsSeed != null) ApiSettingsDialog(settingsSeed!!, { settingsOpen = false }) { draft -> scope.launch {
                val providerId = draft.providerId.ifBlank { "group-analysis-${UUID.randomUUID()}" }
                val provider = ModelProviderEntity(providerId, ModelProviderType.OPENAI_CHAT_COMPLETION, "群聊分析 API", normalizeApiBase(draft.baseUrl, draft.apiPath), draft.apiKey)
                val row = ModelEntity("$providerId:${draft.modelName}", providerId, draft.modelName, null, null, draft.modelName)
                withContext(Dispatchers.IO) { WeAgentRepository.upsertModelProvider(provider); WeAgentRepository.upsertModel(row) }
                selectedModelId = row.id; reloadModels(); settingsOpen = false
            } }
        }
    }

    @Composable private fun CoreMetrics(s: GroupAnalysisStats) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.group_chat_analysis_core_metrics), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        MetricRow(stringResource(R.string.group_chat_analysis_total_messages), s.totalMessages.toString(), stringResource(R.string.group_chat_analysis_active_users), s.activeUsers.toString())
        MetricRow(stringResource(R.string.group_chat_analysis_text_messages), s.textMessages.toString(), stringResource(R.string.group_chat_analysis_at_me), s.atMeMessages.toString())
    }

    @Composable private fun DeepCharts(s: GroupAnalysisStats) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.group_chat_analysis_deep_charts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ExpandableSection(stringResource(R.string.group_chat_analysis_activity_detection)) { MetricRow(stringResource(R.string.group_chat_analysis_total_messages), s.totalMessages.toString(), stringResource(R.string.group_chat_analysis_active_users), s.activeUsers.toString()) }
        ExpandableSection(stringResource(R.string.group_chat_analysis_active_ranking)) { s.ranking.take(10).forEachIndexed { i, v -> Text("${i + 1}. ${v.first}: ${v.second}") } }
        ExpandableSection(stringResource(R.string.group_chat_analysis_routine)) { MetricRow(stringResource(R.string.group_chat_analysis_early_bird), s.earlyBird.toString(), stringResource(R.string.group_chat_analysis_night_owl), s.nightOwl.toString()) }
        ExpandableSection(stringResource(R.string.group_chat_analysis_emotion)) { Text("${stringResource(R.string.group_chat_analysis_laugh)} ${s.laugh} · ${stringResource(R.string.group_chat_analysis_question)} ${s.question} · ${stringResource(R.string.group_chat_analysis_exclamation)} ${s.exclamation} · ${stringResource(R.string.group_chat_analysis_speechless)} ${s.speechless}") }
        ExpandableSection(stringResource(R.string.group_chat_analysis_length)) { Text("1–5: ${s.tiny} · 6–20: ${s.short} · 21–50: ${s.medium} · 50+: ${s.long}") }
        ExpandableSection(stringResource(R.string.group_chat_analysis_content_preference)) { Text(s.typeStats.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key}: ${it.value}" }) }
    }

    @Composable private fun ExpandableSection(title: String, controlledExpanded: Boolean? = null, onControlledToggle: (() -> Unit)? = null, content: @Composable () -> Unit) {
        var local by remember { mutableStateOf(false) }; val expanded = controlledExpanded ?: local
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Column {
            Row(Modifier.fillMaxWidth().clickable { onControlledToggle?.invoke() ?: run { local = !local } }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(if (expanded) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) { HorizontalDivider(); Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
        } }
    }

    @Composable private fun ApiSettingsDialog(seed: ApiDraft, onDismiss: () -> Unit, onSave: (ApiDraft) -> Unit) {
        var base by remember(seed) { mutableStateOf(seed.baseUrl) }; var path by remember(seed) { mutableStateOf(seed.apiPath) }
        var key by remember(seed) { mutableStateOf(seed.apiKey) }; var name by remember(seed) { mutableStateOf(seed.modelName.ifBlank { "auto" }) }
        AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.group_chat_analysis_api_settings)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(base, { base = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_chat_analysis_api_address)) }, singleLine = true)
                OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_chat_analysis_api_path)) }, singleLine = true)
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_chat_analysis_api_key)) }, singleLine = true)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_chat_analysis_model_name)) }, singleLine = true)
            } },
            confirmButton = { TextButton(enabled = base.isNotBlank() && name.isNotBlank(), onClick = { onSave(seed.copy(baseUrl = base, apiPath = path, apiKey = key, modelName = name)) }) { Text(stringResource(R.string.group_chat_analysis_save)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.group_chat_analysis_cancel)) } })
    }

    private fun normalizeApiBase(base: String, path: String): String { val b = base.trim().trimEnd('/'); val p = path.trim().let { if (it.startsWith('/')) it else "/$it" }; return if (b.endsWith(p)) b.removeSuffix(p) else b }
    @Composable private fun MetricRow(a: String, av: String, b: String, bv: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard(a, av, Modifier.weight(1f)); MetricCard(b, bv, Modifier.weight(1f)) }
    @Composable private fun MetricCard(label: String, value: String, modifier: Modifier) = Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.bodySmall) } }
}

private data class ApiDraft(val providerId: String = "", val baseUrl: String = "https://api.openai.com/v1", val apiPath: String = "/chat/completions", val apiKey: String = "", val modelName: String = "auto")

private enum class AnalysisRange(val labelRes: Int, val days: Int) {
    TODAY(R.string.group_chat_analysis_today, 1),
    YESTERDAY(R.string.group_chat_analysis_yesterday, 2),
    WEEK(R.string.group_chat_analysis_week, 7),
    LAST_WEEK(R.string.group_chat_analysis_last_week, 14),
    MONTH(R.string.group_chat_analysis_month, 30),
    LAST_MONTH(R.string.group_chat_analysis_last_month, 60),
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
            append(" ORDER BY createTime DESC LIMIT 5000")
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
