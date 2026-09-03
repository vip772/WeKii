package dev.ujhhgtg.wekit.features.items.chat

import android.media.MediaPlayer
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.DisposableEffect
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
import dev.ujhhgtg.wekit.R
import dev.ujhhgtg.wekit.features.api.core.WeMessageApi
import dev.ujhhgtg.wekit.features.api.core.models.MessageInfo
import dev.ujhhgtg.wekit.features.api.ui.WeChatMessageContextMenuApi
import dev.ujhhgtg.wekit.features.core.FeatureCategoryIds
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs.Companion.prefOption
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.ui.utils.MofangVoiceIcon
import com.composables.icons.materialsymbols.outlined.Info
import dev.ujhhgtg.wekit.utils.AudioUtils
import dev.ujhhgtg.wekit.utils.android.showToast
import dev.ujhhgtg.wekit.utils.coerceToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object MofangVoice : SwitchFeature(), WeChatMessageContextMenuApi.IMenuItemsProvider {
    override val technicalId = "魔方配音"
    override val nameRes = R.string.feature_mofang_voice_name
    override val categoryIds = listOf(FeatureCategoryIds.CHAT)
    override val descriptionRes = R.string.feature_mofang_voice_description
    private const val MENU_ID = 777031
    private var apiKey by prefOption("mofang_voice_api_key", "")
    private var selectedVoiceId by prefOption("mofang_voice_selected_id", "")
    private var selectedVoiceName by prefOption("mofang_voice_selected_name", "")
    private var selectedVoiceIsCloned by prefOption("mofang_voice_selected_is_cloned", false)
    private val menuIcon = MofangVoiceIcon
    override fun onEnable() = WeChatMessageContextMenuApi.addProvider(this)
    override fun onDisable() = WeChatMessageContextMenuApi.removeProvider(this)

    override fun getMenuItems() = listOf(
        WeChatMessageContextMenuApi.MenuItem(
            id = MENU_ID,
            text = localizedChatString(R.string.mofang_voice_menu),
            drawable = menuIcon,
            imageVector = MaterialSymbols.Outlined.Info,
            isSupported = { true },
            multiSelect = WeChatMessageContextMenuApi.MultiSelectSupport.Unsupported,
        ) { view, chattingContext, message ->
            showGeneratorDialog(view, chattingContext, message)
        },
    )

    private fun showGeneratorDialog(
        anchor: View,
        chattingContext: WeChatMessageContextMenuApi.ChattingContext,
        message: MessageInfo,
    ) {
        showComposeDialog(chattingContext.activity) {
            val scope = rememberCoroutineScope()
            var text by remember { mutableStateOf(message.actualContent.takeIf { message.typeCode == 1 }.orEmpty()) }
            var builtInVoices by remember { mutableStateOf(emptyList<MofangVoiceApi.Voice>()) }
            var clonedVoices by remember { mutableStateOf(emptyList<MofangVoiceApi.Voice>()) }
            val rememberedVoice = remember {
                selectedVoiceId.takeIf { it.isNotBlank() }?.let {
                    MofangVoiceApi.Voice(it, selectedVoiceName)
                }
            }
            var selectedVoice by remember { mutableStateOf(rememberedVoice) }
            var selectedBuiltInVoice by remember {
                mutableStateOf(rememberedVoice.takeUnless { selectedVoiceIsCloned })
            }
            var selectedClonedVoice by remember {
                mutableStateOf(rememberedVoice.takeIf { selectedVoiceIsCloned })
            }
            var rolePicker by remember { mutableStateOf<Boolean?>(null) }
            var selectedEmotion by remember { mutableStateOf(Emotion.NEUTRAL) }
            var generatedFile by remember { mutableStateOf<File?>(null) }
            var generatedInput by remember { mutableStateOf<GeneratedInput?>(null) }
            var busy by remember { mutableStateOf(false) }
            var loadingBuiltIn by remember { mutableStateOf(false) }
            var loadingCloned by remember { mutableStateOf(false) }
            val player = remember { MediaPlayer() }

            DisposableEffect(Unit) {
                onDispose {
                    player.release()
                    generatedFile?.delete()
                }
            }

            fun loadVoices(cloned: Boolean) {
                if (apiKey.isBlank()) {
                    showToast(anchor.context.getString(R.string.mofang_voice_api_key_required))
                    return
                }
                scope.launch {
                    if (cloned) loadingCloned = true else loadingBuiltIn = true
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            if (cloned) MofangVoiceApi.getClonedVoices(apiKey)
                            else MofangVoiceApi.getBuiltInVoices(apiKey)
                        }
                    }
                    if (cloned) loadingCloned = false else loadingBuiltIn = false
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    result.onSuccess { voices ->
                        if (cloned) clonedVoices = voices else builtInVoices = voices
                        if (cloned) {
                            selectedClonedVoice = selectedClonedVoice ?: voices.firstOrNull()
                            selectedVoice = selectedClonedVoice
                        } else {
                            selectedBuiltInVoice = selectedBuiltInVoice ?: voices.firstOrNull()
                            selectedVoice = selectedBuiltInVoice
                        }
                    }.onFailure {
                        showToast(it.message ?: anchor.context.getString(R.string.mofang_voice_request_failed))
                    }
                }
            }

            fun generate(sendAfterGeneration: Boolean) {
                val voice = selectedVoice
                val input = voice?.let { GeneratedInput(text, it.id, selectedEmotion) }
                when {
                    apiKey.isBlank() -> showToast(anchor.context.getString(R.string.mofang_voice_api_key_required))
                    text.isBlank() -> showToast(anchor.context.getString(R.string.mofang_voice_text_required))
                    text.length > 1000 -> showToast(anchor.context.getString(R.string.mofang_voice_text_too_long))
                    voice == null -> showToast(anchor.context.getString(R.string.mofang_voice_voice_required))
                    else -> scope.launch {
                        busy = true
                        val oldFile = generatedFile
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                MofangVoiceApi.generate(
                                    apiKey = apiKey,
                                    voiceId = voice.id,
                                    text = text,
                                    emotionVector = selectedEmotion.vector,
                                    cacheDir = anchor.context.cacheDir,
                                )
                            }
                        }
                        busy = false
                        val error = result.exceptionOrNull()
                        if (error is CancellationException) throw error
                        if (error != null) {
                            showToast(
                                error.message
                                    ?: anchor.context.getString(R.string.mofang_voice_generate_failed),
                            )
                            return@launch
                        }
                        val file = result.getOrThrow()
                        oldFile?.delete()
                        generatedFile = file
                        generatedInput = input
                        if (sendAfterGeneration) {
                            busy = true
                            val sent = withContext(Dispatchers.IO) {
                                sendVoice(message.talker, file, anchor)
                            }
                            busy = false
                            showToast(
                                anchor.context.getString(
                                    if (sent) R.string.mofang_voice_send_success
                                    else R.string.mofang_voice_send_failed,
                                ),
                            )
                        }
                    }
                }
            }

            AlertDialogContent(
                title = {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.feature_mofang_voice_name),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                },
                text = {
                    Column(
                        Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(1000) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.mofang_voice_text_label)) },
                            minLines = 1,
                            maxLines = 6,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RoleButton(
                                title = stringResource(R.string.mofang_voice_system_roles),
                                selected = selectedBuiltInVoice,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    rolePicker = false
                                    if (builtInVoices.isEmpty()) loadVoices(false)
                                },
                            )
                            RoleButton(
                                title = stringResource(R.string.mofang_voice_clone_roles),
                                selected = selectedClonedVoice,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    rolePicker = true
                                    if (clonedVoices.isEmpty()) loadVoices(true)
                                },
                            )
                        }
                        rolePicker?.let { cloned ->
                            VoicePickerDialog(
                                title = if (cloned) stringResource(R.string.mofang_voice_clone_roles) else stringResource(R.string.mofang_voice_system_roles),
                                voices = if (cloned) clonedVoices else builtInVoices,
                                selected = if (cloned) selectedClonedVoice else selectedBuiltInVoice,
                                loading = if (cloned) loadingCloned else loadingBuiltIn,
                                modifier = Modifier.fillMaxWidth(),
                                onLoad = { loadVoices(cloned) },
                                onSelect = {
                                    if (cloned) {
                                        selectedClonedVoice = it
                                        selectedBuiltInVoice = null
                                    } else {
                                        selectedBuiltInVoice = it
                                        selectedClonedVoice = null
                                    }
                                    selectedVoice = it
                                    selectedVoiceId = it.id
                                    selectedVoiceName = it.name
                                    selectedVoiceIsCloned = cloned
                                    rolePicker = null
                                },
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            EmotionSelector(
                                selected = selectedEmotion,
                                modifier = Modifier.weight(1f),
                                onSelect = { selectedEmotion = it },
                            )
                            StatusButton(
                                busy = busy,
                                emotion = selectedEmotion,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PinkButton(
                                onClick = { generate(false) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.mofang_voice_generate)) }
                            PinkButton(
                                onClick = {
                                    val file = generatedFile
                                    val currentInput = selectedVoice?.let {
                                        GeneratedInput(text, it.id, selectedEmotion)
                                    }
                                    if (file == null || generatedInput != currentInput) {
                                        generate(true)
                                    } else {
                                        scope.launch {
                                            busy = true
                                            val sent = withContext(Dispatchers.IO) {
                                                sendVoice(message.talker, file, anchor)
                                            }
                                            busy = false
                                            showToast(anchor.context.getString(if (sent) R.string.mofang_voice_send_success else R.string.mofang_voice_send_failed))
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.mofang_voice_send)) }
                        }
                        if (busy) {
                            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        }
                        generatedFile?.let { file ->
                            HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (player.isPlaying) player.stop()
                                    player.reset()
                                    player.setDataSource(file.absolutePath)
                                    player.prepare()
                                    player.start()
                                }.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.mofang_voice_preview))
                                Text(stringResource(R.string.mofang_voice_tap_to_play))
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton({
                        onDismiss()
                        chattingContext.activity.window.decorView.postOnAnimation {
                            showApiKeyDialog(chattingContext.activity)
                        }
                    }) {
                        Text(stringResource(R.string.mofang_voice_open_settings))
                    }
                },
                confirmButton = {
                    TextButton(onDismiss) { Text(stringResource(R.string.dialog_close)) }
                },
            )
        }
    }

    private fun sendVoice(talker: String, source: File, anchor: View): Boolean {
        val duration = AudioUtils.getDurationMs(source.absolutePath).coerceAtLeast(0L)
        val silk = File(anchor.context.cacheDir, "mofang-voice-${System.nanoTime()}.silk")
        val result = runCatching {
            require(AudioUtils.anyToSilk(source.absolutePath, silk.absolutePath)) {
                anchor.context.getString(R.string.mofang_voice_convert_failed)
            }
            require(WeMessageApi.sendVoice(talker, silk.absolutePath, duration.coerceToInt())) {
                anchor.context.getString(R.string.mofang_voice_send_failed)
            }
        }
        silk.delete()
        return result.isSuccess
    }

    private fun showApiKeyDialog(context: android.content.Context) {
        showComposeDialog(context) {
            var draft by remember { mutableStateOf(apiKey) }
            AlertDialogContent(
                title = { Text(stringResource(R.string.mofang_voice_api_key_title)) },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.mofang_voice_api_key_label)) },
                        singleLine = true,
                    )
                },
                dismissButton = {
                    TextButton(onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
                },
                confirmButton = {
                    Button({ apiKey = draft; onDismiss() }) {
                        Text(stringResource(R.string.dialog_confirm))
                    }
                },
            )
        }
    }

    @Composable
    private fun RoleButton(
        title: String,
        selected: MofangVoiceApi.Voice?,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        PinkButton(onClick = onClick, modifier = modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title)
                Text(selected?.name ?: stringResource(R.string.mofang_voice_not_selected), maxLines = 1)
            }
        }
    }

    @Composable
    private fun EmotionSelector(
        selected: Emotion,
        modifier: Modifier = Modifier,
        onSelect: (Emotion) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier) {
            PinkButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.mofang_voice_emotion))
                    Text(stringResource(selected.labelRes), maxLines = 1)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                Emotion.entries.forEach { emotion ->
                    DropdownMenuItem(
                        text = { Text(stringResource(emotion.labelRes)) },
                        onClick = {
                            onSelect(emotion)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun StatusButton(
        busy: Boolean,
        emotion: Emotion,
        modifier: Modifier = Modifier,
    ) {
        PinkButton(
            onClick = {},
            enabled = false,
            modifier = modifier,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.mofang_voice_status))
                Text(
                    if (busy) stringResource(R.string.mofang_voice_loading)
                    else stringResource(R.string.mofang_voice_current_status, stringResource(emotion.labelRes)),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun PinkButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        MaterialButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
            ),
            content = { content() },
        )
    }

    @Composable
    private fun VoicePickerDialog(
        title: String,
        voices: List<MofangVoiceApi.Voice>,
        selected: MofangVoiceApi.Voice?,
        loading: Boolean,
        modifier: Modifier,
        onLoad: () -> Unit,
        onSelect: (MofangVoiceApi.Voice) -> Unit,
    ) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                if (voices.isEmpty()) {
                    TextButton(onLoad, enabled = !loading) {
                        Text(
                            if (loading) stringResource(R.string.mofang_voice_loading)
                            else stringResource(R.string.mofang_voice_get_roles),
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 360.dp)) {
                        items(voices, key = { it.id }) { voice ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(voice) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected?.id == voice.id, { onSelect(voice) })
                            Text(voice.name, maxLines = 1)
                        }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    private data class GeneratedInput(
        val text: String,
        val voiceId: String,
        val emotion: Emotion,
    )

    private enum class Emotion(val labelRes: Int, val vector: List<Double>?) {
        NEUTRAL(R.string.mofang_voice_emotion_neutral, null),
        HAPPY(R.string.mofang_voice_emotion_happy, listOf(0.7, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        ANGRY(R.string.mofang_voice_emotion_angry, listOf(0.0, 0.7, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        SAD(R.string.mofang_voice_emotion_sad, listOf(0.0, 0.0, 0.7, 0.0, 0.0, 0.0, 0.0, 0.0)),
        SURPRISED(R.string.mofang_voice_emotion_surprised, listOf(0.0, 0.0, 0.0, 0.7, 0.0, 0.0, 0.0, 0.0)),
    }
}

private object MofangVoiceApi {
    private const val BASE_URL = "https://peiyinmofang.com/api/open/v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class Voice(val id: String, val name: String)

    @Serializable
    private data class BuiltInResponse(
        val status: Int,
        val message: String = "",
        val data: List<VoiceGroup> = emptyList(),
    )

    @Serializable
    private data class VoiceGroup(
        val title: String,
        val characters: List<Character> = emptyList(),
    )

    @Serializable
    private data class Character(
        val name: String,
        @SerialName("voice_id") val voiceId: String,
    )

    @Serializable
    private data class UserResponse(
        val status: Int,
        val message: String = "",
        val data: List<UserVoice> = emptyList(),
    )

    @Serializable
    private data class UserVoice(
        val name: String,
        @SerialName("voice_id") val voiceId: String,
    )

    @Serializable
    private data class GenerateRequest(
        val voiceId: String,
        val text: String,
        val emoVec: List<Double>? = null,
    )

    @Serializable
    private data class GenerateResponse(
        val status: Int,
        val message: String = "",
        val data: Generated? = null,
    )

    @Serializable
    private data class Generated(
        val audio: String,
        val format: String = "wav",
    )

    fun getBuiltInVoices(apiKey: String): List<Voice> {
        val response = json.decodeFromString<BuiltInResponse>(get("$BASE_URL/voices", apiKey))
        require(response.status == 200) { response.message }
        return response.data.flatMap { group ->
            group.characters.map { Voice(it.voiceId, "${group.title} · ${it.name}") }
        }
    }

    fun getClonedVoices(apiKey: String): List<Voice> {
        val response = json.decodeFromString<UserResponse>(get("$BASE_URL/user-voices", apiKey))
        require(response.status == 200) { response.message }
        return response.data.map { Voice(it.voiceId, it.name) }
    }

    fun generate(
        apiKey: String,
        voiceId: String,
        text: String,
        emotionVector: List<Double>?,
        cacheDir: File,
    ): File {
        val payload = json.encodeToString(GenerateRequest(voiceId, text, emotionVector))
        val request = Request.Builder()
            .url("$BASE_URL/tts/simple-generate")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        val responseText = client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            response.body.string()
        }
        val response = json.decodeFromString<GenerateResponse>(responseText)
        require(response.status == 200) { response.message }
        val generated = requireNotNull(response.data) { response.message }
        val audioUrl = generated.audio
        val extension = generated.format.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "wav" }
        val target = File(cacheDir, "mofang-voice-${System.nanoTime()}.$extension")
        return try {
            val download = Request.Builder().url(audioUrl).build()
            client.newCall(download).execute().use { audioResponse ->
                require(audioResponse.isSuccessful) { "HTTP ${audioResponse.code}" }
                target.outputStream().use { output ->
                    audioResponse.body.byteStream().use { it.copyTo(output) }
                }
            }
            target
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun get(url: String, apiKey: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .build()
        return client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            response.body.string()
        }
    }
}
