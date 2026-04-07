package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.TOOL_IMAGE_INDEX_KEY
import me.rerere.rikkahub.data.ai.TOOL_IMAGE_TOOL_NAME_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolImageReinjectionTransformerTest {
    @Test
    fun `should inject synthetic user message for executed tool images when model supports image`() {
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "call_1",
                        toolName = "container_view_image",
                        input = """{"path":"chart.png"}""",
                        output = listOf(
                            UIMessagePart.Text("""{"success":true,"render_url":"file:///tmp/chart.png"}"""),
                            UIMessagePart.Image(url = "file:///tmp/chart.png"),
                        )
                    )
                )
            )
        )

        val result = applyToolImageReinjection(
            messages = messages,
            imageInputEnabled = true,
        )

        assertEquals(3, result.size)
        assertEquals(MessageRole.USER, result[2].role)

        val injectedParts = result[2].parts
        assertEquals(3, injectedParts.size)
        assertTrue((injectedParts[0] as UIMessagePart.Text).text.contains("tool results"))
        assertEquals("Tool image from `container_view_image` (#1).", (injectedParts[1] as UIMessagePart.Text).text)

        val injectedImage = injectedParts[2] as UIMessagePart.Image
        assertEquals("file:///tmp/chart.png", injectedImage.url)
        assertEquals("container_view_image", injectedImage.metadata?.get(TOOL_IMAGE_TOOL_NAME_KEY)?.toString()?.trim('"'))
        assertEquals("1", injectedImage.metadata?.get(TOOL_IMAGE_INDEX_KEY)?.toString())
    }

    @Test
    fun `should not inject synthetic user message when model does not support image`() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "call_1",
                        toolName = "container_view_image",
                        input = "{}",
                        output = listOf(UIMessagePart.Image(url = "file:///tmp/chart.png"))
                    )
                )
            )
        )

        val result = applyToolImageReinjection(
            messages = messages,
            imageInputEnabled = false,
        )

        assertEquals(messages, result)
    }

    @Test
    fun `should skip assistant messages without executed tool images`() {
        val messages = listOf(
            UIMessage.assistant("No tool output here"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "call_1",
                        toolName = "search_web",
                        input = "{}",
                        output = listOf(UIMessagePart.Text("text only"))
                    )
                )
            )
        )

        val result = applyToolImageReinjection(
            messages = messages,
            imageInputEnabled = true,
        )

        assertEquals(messages, result)
    }
}
