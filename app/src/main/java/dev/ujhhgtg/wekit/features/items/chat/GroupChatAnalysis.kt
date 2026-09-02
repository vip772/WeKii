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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
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
import com.composables.icons.materialsymbols.outlined.Tune
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Search
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
import com.composables.icons.materialsymbols.outlined.Groups
import com.composables.icons.materialsymbols.outlined.Bar_chart
import com.composables.icons.materialsymbols.outlined.Auto_awesome
import com.composables.icons.materialsymbols.outlined.Schedule
import com.composables.icons.materialsymbols.outlined.Mood
import com.composables.icons.materialsymbols.outlined.Text_fields
import com.composables.icons.materialsymbols.outlined.Category
import com.composables.icons.materialsymbols.outlined.History
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
            var range by remember { mutableStateOf(AnalysisRange.TODAY) }
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
            var modelSamplingOpen by remember { mutableStateOf(false) }
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
            LaunchedEffect(Unit) {
                runCatching { withContext(Dispatchers.IO) { GroupChatAnalysisEngine.load(message.talker, AnalysisRange.ALL).stats } }
                    .onSuccess { stats = it }.onFailure { error = it.message }
            }

            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(0.dp)) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("分析报告", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(analysisDateRange(range), color = accent, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { samplingExpanded = !samplingExpanded }) {
                            Icon(MaterialSymbols.Outlined.Tune, stringResource(R.string.group_chat_analysis_sampling_settings), tint = accent)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = samplingExpanded, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                        SamplingSettings(sampleLimit, contextCapacity, { sampleLimit = it }, { contextCapacity = it })
                    }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                    stats?.let { it ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("核心指标", color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        MetricTripleRow(
                            MetricData("今日发言人数", it.todayActiveUsers.toString(), MaterialSymbols.Outlined.Groups),
                            MetricData("今日消息数", it.todayMessages.toString(), MaterialSymbols.Outlined.Chat),
                            MetricData("历史总消息", it.historyTotalMessages.toString(), MaterialSymbols.Outlined.History),
                        )
                        }
                    }
                    Text(
                        stringResource(R.string.group_chat_analysis_smart_insight),
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    ExpandableSection(MaterialSymbols.Outlined.Auto_awesome, stringResource(R.string.group_chat_analysis_smart_summary), insightExpanded, { insightExpanded = !insightExpanded }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.group_chat_analysis_select_period), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { modelSamplingOpen = true }) {
                                Icon(MaterialSymbols.Outlined.Tune, "模型容量与采样", tint = accent)
                            }
                            IconButton(onClick = { scope.launch {
                                val selected = model
                                settingsSeed = if (selected == null) ApiDraft() else withContext(Dispatchers.IO) {
                                    val provider = WeAgentRepository.getModelProvider(selected.providerId)
                                    ApiDraft(provider?.id.orEmpty(), provider?.baseUrl.orEmpty(), "/chat/completions", provider?.apiKey.orEmpty(), selected.modelIdRemote)
                                }
                                settingsOpen = true
                            } }) { Icon(MaterialSymbols.Outlined.Settings, stringResource(R.string.group_chat_analysis_api_settings), tint = accent) }
                        }
                        AnalysisPeriodSelector(AnalysisRange.entries.toList(), range, { selected -> range = selected; report = "" }, accent)
                            OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(), minLines = 1, maxLines = 4, placeholder = { Text(stringResource(R.string.group_chat_analysis_extra_requirement_hint)) })
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccentContainer),
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
                    stats?.let { DeepCharts(message.talker, it) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text(stringResource(R.string.dialog_close), color = accent) }
                }
            }
            }

            if (modelSamplingOpen) AlertDialog(
                onDismissRequest = { modelSamplingOpen = false },
                title = { Text("模型容量与采样") },
                text = { SamplingSettings(sampleLimit, contextCapacity, { sampleLimit = it }, { contextCapacity = it }) },
                confirmButton = { TextButton(onClick = { modelSamplingOpen = false }) { Text("完成") } },
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

    @Composable
    private fun SamplingSettings(limit: Int, capacity: String, onLimit: (Int) -> Unit, onCapacity: (String) -> Unit) {
        val accent = accentColor()
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("采样设置", color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SamplingSlider("分析深度", limit, "条", 500, 5000, accent, onLimit)
                SamplingSlider("词云提取数", capacity.toIntOrNull() ?: 40, "个", 0, 100, accent, { onCapacity(it.toString()) })
                SamplingSlider("最小词长", 10, "字", 1, 20, accent, {})
            }
        }
    }
    @Composable
    private fun SamplingSlider(title: String, value: Int, unit: String, min: Int, max: Int, accent: ComposeColor, onValueChange: (Int) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$value $unit", color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Slider(value = ((value - min).toFloat() / (max - min).coerceAtLeast(1)).coerceIn(0f, 1f), onValueChange = { onValueChange(min + (it * (max - min)).toInt()) }, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = accent.copy(alpha = 0.16f)))
        }
    }
    @Composable
    private fun ActivityDetection(talker: String, stats: GroupAnalysisStats, period: Int, onPeriod: (Int) -> Unit, inactiveOpen: Boolean, onInactive: () -> Unit) {
        val members = remember(talker) { runCatching { WeDatabaseApi.getGroupMembers(talker) }.getOrDefault(emptyList()) }
        val active = remember(stats) { stats.ranking.map { it.senderId }.toSet() }
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

    @Composable private fun DeepCharts(talker: String, s: GroupAnalysisStats) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.group_chat_analysis_deep_charts), color = accentColor(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ExpandableSection(MaterialSymbols.Outlined.Groups, stringResource(R.string.group_chat_analysis_activity_detection)) { ActivityDetectionContent(talker, s) }
        var rankingRange by remember { mutableStateOf(AnalysisRange.TODAY) }
        var rankingStats by remember { mutableStateOf(s) }
        LaunchedEffect(rankingRange) {
            if (rankingRange != AnalysisRange.TODAY) {
                runCatching { GroupChatAnalysisEngine.load(talker, rankingRange).stats }.onSuccess { rankingStats = it }
            } else rankingStats = s
        }
        ExpandableSection(MaterialSymbols.Outlined.Bar_chart, stringResource(R.string.group_chat_analysis_active_ranking)) {
            AnalysisPeriodSelector(AnalysisRange.entries.toList(), rankingRange, { rankingRange = it }, accentColor())
            val maxCount = rankingStats.ranking.maxOfOrNull { it.count } ?: 1
            val members = remember(talker) { runCatching { WeDatabaseApi.getGroupMembers(talker) }.getOrDefault(emptyList()) }
            rankingStats.ranking.take(10).forEachIndexed { i, v ->
                val member = members.firstOrNull { it.wxId == v.senderId }
                if (member != null) RankingItem(i + 1, member.displayName, member.avatarUrl, v.count, maxCount)
            }
        }
        ExpandableSection(MaterialSymbols.Outlined.Schedule, stringResource(R.string.group_chat_analysis_routine)) { RoutineChart(s) }
        ExpandableSection(MaterialSymbols.Outlined.Mood, stringResource(R.string.group_chat_analysis_emotion)) { EmotionFingerprint(s) }
        ExpandableSection(MaterialSymbols.Outlined.Text_fields, stringResource(R.string.group_chat_analysis_length)) { MessageLengthChart(s) }
        ExpandableSection(MaterialSymbols.Outlined.Category, stringResource(R.string.group_chat_analysis_content_preference)) { ContentPreferenceChart(s) }
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
        Card(modifier.height(128.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(data.icon, contentDescription = title, tint = color, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(period, color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("$count 条", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    private fun RankingItem(rank: Int, name: String, avatarUrl: String, count: Int, maxCount: Int) {
        val progress = (count.toFloat() / maxCount.coerceAtLeast(1)).coerceIn(0f, 1f)
        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(rank.toString(), fontWeight = FontWeight.Bold)
                }
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(CircleShape))
                } else {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text(name.firstOrNull()?.toString().orEmpty(), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(10.dp))
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
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(item.icon, item.title, tint = item.color, modifier = Modifier.size(22.dp))
                            }
                            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(item.value.toString(), color = item.color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { (item.value / maxOf(21f, items.maxOf { it.value }.toFloat())).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(start = 48.dp), color = item.color,
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
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(item.icon, item.title, tint = item.color, modifier = Modifier.size(22.dp))
                            }
                            Text("${item.title} (${item.range})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(item.value.toString(), color = item.color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { item.value.toFloat() / maxValue },
                            modifier = Modifier.fillMaxWidth().padding(start = 48.dp), color = item.color,
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
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(item.icon, item.name, tint = accentColor(), modifier = Modifier.size(22.dp)) }
                            Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(item.value.toString(), color = accentColor(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { item.value.toFloat() / maxValue }, modifier = Modifier.fillMaxWidth().padding(start = 48.dp), color = accentColor(), trackColor = accentColor().copy(alpha = 0.12f))
                    }
                }
            }
        }
    }

    @Composable private fun ActivityDetectionContent(talker: String, s: GroupAnalysisStats) {
        val accent = accentColor()
        val members = remember(talker) { runCatching { WeDatabaseApi.getGroupMembers(talker) }.getOrDefault(emptyList()) }
        var period by remember { mutableStateOf(7) }; var memberDialog by remember { mutableStateOf(false) }; var loaded by remember { mutableStateOf<GroupAnalysisStats?>(null) }
        LaunchedEffect(talker, period) { loaded = runCatching { GroupChatAnalysisEngine.load(talker, if (period == 0) AnalysisRange.ALL else AnalysisRange.entries.minByOrNull { kotlin.math.abs(it.days - period) } ?: AnalysisRange.WEEK).stats }.getOrNull() }
        val activeIds = loaded?.ranking?.map { it.senderId }?.toSet() ?: emptySet(); val inactive = members.filter { it.wxId !in activeIds }
        val percent = if (members.isEmpty()) 0.0 else (loaded?.activeUsers ?: 0).toDouble() * 100 / members.size
        Text("检测周期", color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(7,14,30,0).forEach { d -> FilterChip(period == d, { period=d }, { Text(if(d==0) "全部" else "最近${d}天") }) } }
        Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(22.dp), colors=CardDefaults.cardColors(containerColor=accent.copy(alpha=.08f))) { Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha=.12f)), contentAlignment=Alignment.Center) { Icon(MaterialSymbols.Outlined.Groups,null,tint=accent,modifier=Modifier.size(32.dp)) }; Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text("活跃发言人数: ${loaded?.activeUsers ?: 0} 人",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold); Text("当前群聊总人数为 ${members.size} 人",style=MaterialTheme.typography.bodyLarge) }; Text(String.format(Locale.getDefault(),"%.1f%%",percent),color=accent,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold) }
            HorizontalDivider(modifier=Modifier.padding(vertical=4.dp)); val dark=MaterialTheme.colorScheme.background.red<.2f; Button({memberDialog=true},Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=if(dark) ComposeColor(0xFF5A2825) else ComposeColor(0xFFFFE4E1),contentColor=if(dark) ComposeColor(0xFFFFB4AB) else ComposeColor(0xFFB3261E))) { Icon(MaterialSymbols.Outlined.Remove,null); Spacer(Modifier.width(8.dp)); Text("查看/清理未发言成员 (${inactive.size}人)",fontWeight=FontWeight.Bold) }
        } }; if(memberDialog) InactiveMemberDialog(talker,members,inactive){memberDialog=false}
    }
    @Composable private fun InactiveMemberDialog(talker:String,members:List<dev.ujhhgtg.wekit.features.api.core.models.WeContact>,inactive:List<dev.ujhhgtg.wekit.features.api.core.models.WeContact>,onDismiss:()->Unit) {
        var selected by remember { mutableStateOf(emptySet<String>()) }; var query by remember { mutableStateOf("") }; val ids=inactive.map{it.wxId}.toSet(); val ordered=inactive+members.filterNot{it.wxId in ids}; val visible=ordered.filter{query.isBlank()||it.displayName.contains(query,true)||it.wxId.contains(query,true)}
        AlertDialog(onDismissRequest=onDismiss,shape=RoundedCornerShape(28.dp),title={Text("群聊成员",fontWeight=FontWeight.Bold)},text={Column(Modifier.heightIn(max=520.dp)){ OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("搜索成员")},shape=RoundedCornerShape(16.dp)); Spacer(Modifier.height(8.dp)); Column(Modifier.verticalScroll(rememberScrollState())) { visible.forEach { m -> val checked=m.wxId in selected; Row(Modifier.fillMaxWidth().clickable{selected=if(checked)selected-m.wxId else selected+m.wxId}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){ Checkbox(checked,null); if(m.avatarUrl.isNotBlank()) AsyncImage(m.avatarUrl,null,contentScale=ContentScale.Crop,modifier=Modifier.size(40.dp).clip(CircleShape)) else Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){Text(m.nickname.take(1))}; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)){Text(m.remarkName.ifBlank{m.nickname});Text(m.wxId,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}; if(m.wxId in ids) Text("未发言",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall) } } } }},confirmButton={TextButton(onClick={WeGroupApi.delMembers(talker,selected.toList());onDismiss()}, enabled=selected.isNotEmpty()){Text("移除选中成员")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
    }
    @Composable private fun ExpandableSection(icon: ImageVector, title: String, controlledExpanded: Boolean? = null, onControlledToggle: (() -> Unit)? = null, content: @Composable () -> Unit) {
        var local by remember { mutableStateOf(false) }; val expanded = controlledExpanded ?: local
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            border = CardDefaults.outlinedCardBorder(),
            colors = CardDefaults.outlinedCardColors(),
        ) { Column {
            Row(Modifier.fillMaxWidth().clickable { onControlledToggle?.invoke() ?: run { local = !local } }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(accentColor().copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor(), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, Modifier.weight(1f), color = accentColor(), fontWeight = FontWeight.SemiBold); Text(if (expanded) "⌃" else "⌄", color = accentColor(), style = MaterialTheme.typography.titleMedium)
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
    private fun MetricTripleRow(a: MetricData, b: MetricData, c: MetricData) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(34.dp), colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background.red < 0.2f) ComposeColor(0xFFFFD1A3) else ComposeColor(0xFFFFE4EC))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                MetricCard(a, Modifier.weight(1f))
                MetricCard(b, Modifier.weight(1f))
                MetricCard(c, Modifier.weight(1f))
            }
        }
    }
    @Composable
    private fun MetricRow(a: String, av: String, b: String, bv: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard(MetricData(a, av, MaterialSymbols.Outlined.Info), Modifier.weight(1f)); MetricCard(MetricData(b, bv, MaterialSymbols.Outlined.Info), Modifier.weight(1f)) }
    @Composable
    private fun MetricCard(data: MetricData, modifier: Modifier) {
        Column(modifier.padding(horizontal = 4.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(data.icon, contentDescription = null, tint = accentColor(), modifier = Modifier.size(22.dp))
            Text(data.value, color = accentColor(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(data.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
        }
    }
}

private data class MetricData(val label: String, val value: String, val icon: ImageVector)
private data class ApiDraft(val providerId: String = "", val baseUrl: String = "", val apiPath: String = "/chat/completions", val apiKey: String = "", val modelName: String = "")

private fun analysisDateRange(range: AnalysisRange): String {
    val end = Calendar.getInstance()
    val start = end.clone() as Calendar
    if (range.days > 0) start.add(Calendar.DAY_OF_YEAR, -(range.days - 1))
    val format = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    return if (range.days == 0) "全部记录" else "${format.format(start.time)} ~ ${format.format(end.time)}"
}

private enum class AnalysisRange(val labelRes: Int, val days: Int) {
    TODAY(R.string.group_chat_analysis_today, 1), YESTERDAY(R.string.group_chat_analysis_yesterday, 2),
    WEEK(R.string.group_chat_analysis_week, 7), LAST_WEEK(R.string.group_chat_analysis_last_week, 14),
    MONTH(R.string.group_chat_analysis_month, 30), LAST_MONTH(R.string.group_chat_analysis_last_month, 60),
    ALL(R.string.group_chat_analysis_all, 0),
}

private data class AnalysisMessage(val sender: String, val content: String, val createTime: Long)
private data class RankingEntry(val senderId: String, val count: Int)
private data class LoadedAnalysis(val stats: GroupAnalysisStats, val messages: List<AnalysisMessage>)
private data class GroupAnalysisStats(
    val totalMessages: Int,
    val historyTotalMessages: Int,
    val todayMessages: Int,
    val todayActiveUsers: Int,
    val textMessages: Int,
    val activeUsers: Int,
    val atMeMessages: Int,
    val ranking: List<RankingEntry>,
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
            ranking = ranking.entries.sortedByDescending { it.value }.map { RankingEntry(it.key, it.value) },
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
