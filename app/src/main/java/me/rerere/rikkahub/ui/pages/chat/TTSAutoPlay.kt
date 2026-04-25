package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import kotlin.uuid.Uuid

@Composable
fun TTSAutoPlay(vm: ChatVM, setting: Settings, chatId: Uuid, latestAssistantText: String?) {
    val tts = LocalTTSState.current
    val currentAssistantText = rememberUpdatedState(latestAssistantText)
    val updatedSetting = rememberUpdatedState(setting)
    LaunchedEffect(Unit) {
        vm.generationDoneFlow.collect { conversationId ->
            if (conversationId != chatId) return@collect
            if (updatedSetting.value.displaySetting.autoPlayTTSAfterGeneration) {
                currentAssistantText.value?.let { text ->
                    val textToSpeak = if (updatedSetting.value.displaySetting.ttsOnlyReadQuoted) {
                        text.extractQuotedContentAsText() ?: text
                    } else {
                        text
                    }
                    if (textToSpeak.isNotBlank()) {
                        tts.speak(textToSpeak)
                    }
                }
            }
        }
    }
}
