package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.WorkflowPhase
import me.rerere.rikkahub.data.model.WorkflowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateBuildersTest {
    @Test
    fun `buildChatChromeUiState only flips workflow fields when workflow state changes`() {
        val settings = Settings.dummy()

        val inactive = buildChatChromeUiState(
            title = "Project Chat",
            assistantId = settings.assistantId,
            hasMessages = true,
            workflowState = null,
            settings = settings,
            workflowEnabled = true,
            defaultAssistantLabel = "Default Assistant",
        )
        val active = buildChatChromeUiState(
            title = "Project Chat",
            assistantId = settings.assistantId,
            hasMessages = true,
            workflowState = WorkflowState(phase = WorkflowPhase.EXECUTE),
            settings = settings,
            workflowEnabled = true,
            defaultAssistantLabel = "Default Assistant",
        )

        assertEquals(inactive.title, active.title)
        assertEquals(inactive.subtitle, active.subtitle)
        assertEquals(inactive.hasMessages, active.hasMessages)
        assertFalse(inactive.workflowActive)
        assertNull(inactive.workflowPhase)
        assertTrue(active.workflowActive)
        assertEquals(WorkflowPhase.EXECUTE, active.workflowPhase)
    }

    @Test
    fun `buildChatInputUiState reflects explicit message count and workflow flag`() {
        val inputState = buildChatInputUiState(
            messageCount = 7,
            workflowEnabled = true,
            workflowActive = true,
            currentChatModel = null,
            loading = true,
            enableWebSearch = false,
            compressionUiState = null,
            showLedgerGenerationDialog = false,
        )

        assertEquals(7, inputState.messageCount)
        assertTrue(inputState.workflowEnabled)
        assertTrue(inputState.workflowActive)
        assertTrue(inputState.loading)
    }

    @Test
    fun `lastAssistantInputTokens reads latest selected assistant branch`() {
        val earlierAssistant = MessageNode(
            messages = listOf(
                UIMessage.assistant("draft").copy(usage = TokenUsage(promptTokens = 32)),
            )
        )
        val selectedAssistantBranch = MessageNode(
            messages = listOf(
                UIMessage.assistant("old").copy(usage = TokenUsage(promptTokens = 48)),
                UIMessage.assistant("current").copy(usage = TokenUsage(promptTokens = 128)),
            ),
            selectIndex = 1,
        )

        assertEquals(128, listOf(earlierAssistant, selectedAssistantBranch).lastAssistantInputTokens())
    }
}
