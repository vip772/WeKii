package dev.ujhhgtg.wekit.features.items.chat

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Refresh
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Nights_stay
import com.composables.icons.materialsymbols.outlined.Wb_sunny
import com.composables.icons.materialsymbols.outlined.Local_cafe
import com.composables.icons.materialsymbols.outlined.Bedtime
import com.composables.icons.materialsymbols.outlined.Favorite
import com.composables.icons.materialsymbols.outlined.Warning
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Remove
import com.composables.icons.materialsymbols.outlined.Chat
import com.composables.icons.materialsymbols.outlined.Record_voice_over
import com.composables.icons.materialsymbols.outlined.Notes
import com.composables.icons.materialsymbols.outlined.Photo_library
import com.composables.icons.materialsymbols.outlined.Format_quote
import com.composables.icons.materialsymbols.outlined.Favorite
import com.composables.icons.materialsymbols.outlined.Mic
import com.composables.icons.materialsymbols.outlined.Gif_box
import com.composables.icons.materialsymbols.outlined.Videocam
import dev.ujhhgtg.wekit.R
import dev.ujhhgtg.wekit.agent.data.WeAgentRepository
import dev.ujhhgtg.wekit.agent.data.WeAgentSettings
import dev.ujhhgtg.wekit.agent.data.entity.*
import dev.ujhhgtg.wekit.agent.model.*
import dev.ujhhgtg.wekit.agent.model.local.LocalLlamaModels
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.features.api.core.WeGroupApi
import dev.ujhhgtg.wekit.features.api.core.models.MessageInfo
import dev.ujhhgtg.wekit.features.api.ui.WeChatMessageContextMenuApi
import dev.ujhhgtg.wekit.features.core.FeatureCategoryIds
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs.Companion.prefOption
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
            LaunchedEffect(Unit) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            }
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
            var samplingExpanded by remember { mutableStateOf(false) }
            var sampleLimit by remember { mutableStateOf(5000) }
            var contextCapacity by remember { mutableStateOf("自动") }
            var activityPeriod by remember { mutableStateOf(7) }
            var inactiveOpen by remember { mutableStateOf(false) }
            val darkTheme = MaterialTheme.colorScheme.background.red < 0.2f
            val accent = if (darkTheme) ComposeColor(0xFFFF9800) else ComposeColor(0xFFE91E63)
            val accentContainer = if (darkTheme) ComposeColor(0xFF5D3A00) else ComposeColor(0xFFFFD9E2)
            val onAccentContainer = if (darkTheme) ComposeColor(0xFFFFDDB5) else ComposeColor(0xFF3E0016)

            suspend fun reloadModels() {
                models = withContext(Dispatchers.IO) { WeAgentRepository.getAllModelsOnce() }
                model = models.firstOrNull { it.id == selectedModelId } ?: models.firstOrNull()
            }
            LaunchedEffect(Unit) { reloadModels() }
            LaunchedEffect(range) {
                runCatching { withContext(Dispatchers.IO) { GroupChatAnalysisEngine.load(message.talker, range).stats } }
                    .onSuccess { stats = it }.onFailure { error = it.message }
            }

            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(0.dp)) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("分析报告", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("2026/08/31 ~ 2026/09/01", color = accent, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { samplingExpanded = !samplingExpanded }) { Icon(MaterialSymbols.Outlined.Settings, stringResource(R.string.group_chat_analysis_sampling_settings), tint = accent) }
                    }
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("核心指标", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    if (samplingExpanded) SamplingSettings(sampleLimit, contextCapacity, { sampleLimit = it }, { contextCapacity = it })
                    stats?.let { it ->
                        MetricTripleRow(
                            "今日发言人数", it.todayActiveUsers.toString(),
                            "今日消息数", it.todayMessages.toString(),
                            "历史总消息", it.historyTotalMessages.toString(),
                        )
                    }
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
                                    ApiDraft(provider?.id.orEmpty(), provider?.baseUrl.orEmpty(), "/chat/completions", provider?.apiKey.orEmpty(), selected.modelIdRemote)
                                }
                                settingsOpen = true
                            } }) { Icon(MaterialSymbols.Outlined.Settings, stringResource(R.string.group_chat_analysis_api_settings)) }
                        }
                        AnalysisPeriodSelector(AnalysisRange.entries.toList(), range, { selected -> range = selected; report = "" }, accent)
                        OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(), minLines = 1, maxLines = 4, placeholder = { Text(stringResource(R.string.group_chat_analysis_extra_requirement_hint)) })
                        Button(
                            onClick = {
                                busy = true; error = null; report = ""
                                scope.launch { runCatching {
                                    val loaded = withContext(Dispatchers.IO) { GroupChatAnalysisEngine.load(message.talker, range) }
                                    stats = loaded.stats
                                    GroupChatAnalysisEngine.streamReport(model!!, loaded.messages, extra) { report += it }
                                }.onFailure { error = it.message ?: it.javaClass.simpleName }; busy = false }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && model != null,
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.group_chat_analysis_generate_summary))
                        }
                        if (report.isBlank() && !busy) Text(stringResource(R.string.group_chat_analysis_summary_hint), style = MaterialTheme.typography.bodySmall)
                        if (report.isNotBlank()) Card(colors = CardDefaults.cardColors(containerColor = accentContainer, contentColor = onAccentContainer)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.group_chat_analysis_ai_report), fontWeight = FontWeight.SemiBold); Text(report)
                            }
                        }
                    }
                    stats?.let { DeepCharts(it) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text(stringResource(R.string.dialog_close), color = accent) }
                }
            }
            }

            if (settingsOpen && settingsSeed != null) ApiSettingsDialog(settingsSeed!!, { settingsOpen = false }) { draft -> scope.launch {
                val providerId = draft.providerId.ifBlank { "group-analysis-${UUID.randomUUID()}" }
                val provider = ModelProviderEntity(providerId, ModelProviderType.OPENAI_CHAT_COMPLETION, "群聊分析 API", normalizeApiBase(draft.baseUrl, draft.apiPath), draft.apiKey)
                val row = ModelEntity("$providerId:${draft.modelName}", providerId, draft.modelName, null, null, draft.modelName)
                withContext(Dispatchers.IO) { WeAgentRepository.upsertModelProvider(provider); WeAgentRepository.upsertModel(row) }
                selectedModelId = row.id; reloadModels(); settingsOpen = false
            } }
        }
    }

    @Composable
    private fun SamplingSettings(limit: Int, capacity: String, onLimit: (Int) -> Unit, onCapacity: (String) -> Unit) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.group_chat_analysis_sampling_settings), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.group_chat_analysis_analysis_depth))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(500, 1000, 2000, 5000).forEach { n -> FilterChip(limit == n, { onLimit(n) }, { Text("${n}条") }) } }
            Text(stringResource(R.string.group_chat_analysis_word_cloud_count))
            Text(stringResource(R.string.group_chat_analysis_sampling_note), style = MaterialTheme.typography.bodySmall)
        } }
    }

    @Composable
    private fun ActivityDetection(talker: String, stats: GroupAnalysisStats, period: Int, onPeriod: (Int) -> Unit, inactiveOpen: Boolean, onInactive: () -> Unit) {
        val members = remember(talker) { runCatching { WeDatabaseApi.getGroupMembers(talker) }.getOrDefault(emptyList()) }
        val active = remember(stats) { stats.ranking.map { it.first }.toSet() }
        val inactive = members.filter { it.nickname !in active && it.displayName !in active }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.group_chat_analysis_activity_detection), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.group_chat_analysis_detection_period), fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(7, 14, 30, 0).forEach { d -> FilterChip(period == d, { onPeriod(d) }, { Text(if (d == 0) stringResource(R.string.group_chat_analysis_all) else "最近${d}天") }) } }
            Text(stringResource(R.string.group_chat_analysis_active_people, stats.activeUsers, members.size))
            TextButton(onClick = onInactive) { Text(stringResource(R.string.group_chat_analysis_view_inactive, inactive.size)) }
            if (inactiveOpen) inactive.forEach { member -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(member.nickname, Modifier.weight(1f)); TextButton(onClick = { WeGroupApi.delMember(talker, member.wxId) }) { Text(stringResource(R.string.group_chat_analysis_remove)) } } }
        } }
    }

    @Composable private fun DeepCharts(s: GroupAnalysisStats) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.group_chat_analysis_deep_charts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ExpandableSection(stringResource(R.string.group_chat_analysis_activity_detection)) { ActivityDetectionContent(s) }
        ExpandableSection(stringResource(R.string.group_chat_analysis_active_ranking)) {
            AnalysisPeriodSelector(AnalysisRange.entries.filter { it != AnalysisRange.ALL }, range = AnalysisRange.TODAY, onRangeChange = {})
            val maxCount = s.ranking.maxOfOrNull { it.second } ?: 1
            s.ranking.take(10).forEachIndexed { i, v ->
                RankingItem(i + 1, v.first, v.second, maxCount)
            }
        }
        ExpandableSection(stringResource(R.string.group_chat_analysis_routine)) { RoutineChart(s) }
        ExpandableSection(stringResource(R.string.group_chat_analysis_emotion)) { EmotionFingerprint(s) }
        ExpandableSection(stringResource(R.string.group_chat_analysis_length)) { MessageLengthChart(s) }
        ExpandableSection(stringResource(R.string.group_chat_analysis_content_preference)) { ContentPreferenceChart(s) }
    }

    @Composable
    private fun RoutineChart(s: GroupAnalysisStats) {
        val cards = listOf(
            RoutineData("熬夜修仙", "00:00 – 04:00", s.nightOwl, MaterialSymbols.Outlined.Nights_stay),
            RoutineData("早起鸟儿", "05:00 – 08:00", s.earlyBird, MaterialSymbols.Outlined.Wb_sunny),
            RoutineData("带薪摸鱼", "工作划水期", (s.totalMessages - s.nightOwl - s.earlyBird).coerceAtLeast(0), MaterialSymbols.Outlined.Local_cafe),
            RoutineData("夜生活", "19:00 – 23:00", s.nightOwl, MaterialSymbols.Outlined.Bedtime),
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            cards.chunked(2).forEach { rowCards ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowCards.forEach { card -> RoutineCard(card, Modifier.weight(1f)) }
                }
            }
        }
    }

    private data class RoutineData(val title: String, val period: String, val count: Int, val icon: ImageVector)
    @Composable
    private fun RoutineCard(data: RoutineData, modifier: Modifier) {
        val title = data.title
        val period = data.period
        val count = data.count
        val colors = listOf(ComposeColor(0xFF6D36C9), ComposeColor(0xFFFFA000), ComposeColor(0xFF00BCD4), ComposeColor(0xFFE91E63))
        val color = when (data.title) {
            "熬夜修仙" -> colors[0]
            "早起鸟儿" -> colors[1]
            "带薪摸鱼" -> colors[2]
            else -> colors[3]
        }
        Card(modifier.height(160.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(data.icon, contentDescription = title, tint = color, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(period, color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text("$count 条", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun AnalysisPeriodSelector(options: List<AnalysisRange>, range: AnalysisRange, onRangeChange: (AnalysisRange) -> Unit, selectedColor: ComposeColor) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                options.forEach { item ->
                    TextButton(onClick = { onRangeChange(item) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 10.dp)) {
                        Text(stringResource(item.labelRes), color = if (range == item) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }

    @Composable
    private fun RankingItem(rank: Int, name: String, count: Int, maxCount: Int) {
        val progress = (count.toFloat() / maxCount.coerceAtLeast(1)).coerceIn(0f, 1f)
        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(rank.toString(), fontWeight = FontWeight.Bold)
                }
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("$count 条", color = accentColor(), fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(start = 36.dp), color = accentColor(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }

    @Composable
    private fun accentColor() = if (MaterialTheme.colorScheme.background.red < 0.2f) ComposeColor(0xFFFF9800) else ComposeColor(0xFFE91E63)

    @Composable
    private fun EmotionFingerprint(s: GroupAnalysisStats) {
        data class Emotion(val title: String, val value: Int, val icon: ImageVector, val color: ComposeColor)
        val items = listOf(
            Emotion("快乐浓度", s.laugh, MaterialSymbols.Outlined.Favorite, ComposeColor(0xFFFFB800)),
            Emotion("激动暴躁", s.exclamation, MaterialSymbols.Outlined.Warning, ComposeColor(0xFFFF4038)),
            Emotion("疑惑指数", s.question, MaterialSymbols.Outlined.Info, ComposeColor(0xFF2196F3)),
            Emotion("荡漾撒娇", 0, MaterialSymbols.Outlined.Favorite, ComposeColor(0xFFE91E63)),
            Emotion("无语凝噎", s.speechless, MaterialSymbols.Outlined.More_vert, ComposeColor(0xFFAAAAAA)),
        )
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                Icon(item.icon, item.title, tint = item.color, modifier = Modifier.size(42.dp))
                            }
                            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(item.value.toString(), color = item.color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { (item.value / maxOf(21f, items.maxOf { it.value }.toFloat())).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(start = 72.dp), color = item.color,
                            trackColor = item.color.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MessageLengthChart(s: GroupAnalysisStats) {
        data class LengthItem(val title: String, val range: String, val value: Int, val icon: ImageVector, val color: ComposeColor)
        val items = listOf(
            LengthItem("情字如金", "1–5字", s.tiny, MaterialSymbols.Outlined.Remove, ComposeColor(0xFF4CAF50)),
            LengthItem("正常交流", "6–20字", s.short, MaterialSymbols.Outlined.Chat, ComposeColor(0xFF2196F3)),
            LengthItem("侃侃而谈", "20–50字", s.medium, MaterialSymbols.Outlined.Record_voice_over, ComposeColor(0xFFFF9800)),
            LengthItem("长篇大论", "50字+", s.long, MaterialSymbols.Outlined.Notes, ComposeColor(0xFF9C27B0)),
        )
        val maxValue = items.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                Icon(item.icon, item.title, tint = item.color, modifier = Modifier.size(42.dp))
                            }
                            Text("${item.title} (${item.range})", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(item.value.toString(), color = item.color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { item.value.toFloat() / maxValue },
                            modifier = Modifier.fillMaxWidth().padding(start = 72.dp), color = item.color,
                            trackColor = item.color.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ContentPreferenceChart(s: GroupAnalysisStats) {
        data class ContentItem(val name: String, val value: Int, val icon: ImageVector)
        val labels = listOf("文本", "图片", "引用回复", "表情包", "语音", "GIF动画", "其他未知", "视频")
        val items = labels.map { ContentItem(it, s.typeStats[it] ?: 0, when (it) {
            "文本" -> MaterialSymbols.Outlined.Notes
            "图片" -> MaterialSymbols.Outlined.Photo_library
            "引用回复" -> MaterialSymbols.Outlined.Format_quote
            "表情包" -> MaterialSymbols.Outlined.Favorite
            "语音" -> MaterialSymbols.Outlined.Mic
            "GIF动画" -> MaterialSymbols.Outlined.Gif_box
            "视频" -> MaterialSymbols.Outlined.Videocam
            else -> MaterialSymbols.Outlined.Info
        }) }
        val maxValue = items.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { Icon(item.icon, item.name, tint = accentColor(), modifier = Modifier.size(42.dp)) }
                            Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(item.value.toString(), color = accentColor(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { item.value.toFloat() / maxValue }, modifier = Modifier.fillMaxWidth().padding(start = 72.dp), color = accentColor(), trackColor = accentColor().copy(alpha = 0.12f))
                    }
                }
            }
        }
    }

    @Composable private fun ActivityDetectionContent(s: GroupAnalysisStats) {
        MetricRow(stringResource(R.string.group_chat_analysis_total_messages), s.totalMessages.toString(), stringResource(R.string.group_chat_analysis_active_users), s.activeUsers.toString())
        Text(stringResource(R.string.group_chat_analysis_active_people, s.activeUsers, s.ranking.size))
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
    @Composable
    private fun MetricTripleRow(a: String, av: String, b: String, bv: String, c: String, cv: String) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(34.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 30.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                MetricCard(a, av, Modifier.weight(1f))
                MetricCard(b, bv, Modifier.weight(1f))
                MetricCard(c, cv, Modifier.weight(1f))
            }
        }
    }
    @Composable
    private fun MetricRow(a: String, av: String, b: String, bv: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard(a, av, Modifier.weight(1f)); MetricCard(b, bv, Modifier.weight(1f)) }
    @Composable
    private fun MetricCard(label: String, value: String, modifier: Modifier) {
        Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        }
    }
}

private data class ApiDraft(val providerId: String = "", val baseUrl: String = "", val apiPath: String = "/chat/completions", val apiKey: String = "", val modelName: String = "")

private enum class AnalysisRange(val labelRes: Int, val days: Int) {
    TODAY(R.string.group_chat_analysis_today, 1), YESTERDAY(R.string.group_chat_analysis_yesterday, 2),
    WEEK(R.string.group_chat_analysis_week, 7), LAST_WEEK(R.string.group_chat_analysis_last_week, 14),
    MONTH(R.string.group_chat_analysis_month, 30), LAST_MONTH(R.string.group_chat_analysis_last_month, 60),
    ALL(R.string.group_chat_analysis_all, 0),
}

private data class AnalysisMessage(val sender: String, val content: String, val createTime: Long)
private data class LoadedAnalysis(val stats: GroupAnalysisStats, val messages: List<AnalysisMessage>)
private data class GroupAnalysisStats(
    val totalMessages: Int,
    val historyTotalMessages: Int,
    val todayMessages: Int,
    val todayActiveUsers: Int,
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
        val now = Calendar.getInstance()
        val todayStart = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val start = if (range.days == 0) 0L else System.currentTimeMillis() - range.days * 86_400_000L
        val rows = ArrayList<AnalysisMessage>()
        val ranking = linkedMapOf<String, Int>()
        val todayRanking = linkedMapOf<String, Int>()
        var todayMessages = 0
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
            append(" ORDER BY createTime DESC")
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
                if (createTime >= todayStart) { todayMessages++; todayRanking[sender] = (todayRanking[sender] ?: 0) + 1 }
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
            totalMessages = total,
            historyTotalMessages = total,
            todayMessages = todayMessages,
            todayActiveUsers = todayRanking.size,
            textMessages = text,
            activeUsers = ranking.size,
            atMeMessages = atMe,
            ranking = ranking.entries.sortedByDescending { it.value }.map { it.key to it.value },
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
