package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.TOOL_IMAGE_INDEX_KEY
import me.rerere.rikkahub.data.ai.TOOL_IMAGE_TOOL_NAME_KEY
import me.rerere.rikkahub.data.ai.mergeMessagePartMetadata

private const val TOOL_IMAGE_SYNTHETIC_FLAG = "tool_image_reinjection"
private const val TOOL_IMAGE_REINJECTED_KEY = "study_rik_tool_image_reinjected"

object ToolImageReinjectionTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return applyToolImageReinjection(
            messages = messages,
            imageInputEnabled = ctx.model.inputModalities.contains(Modality.IMAGE),
        )
    }
}

internal fun applyToolImageReinjection(
    messages: List<UIMessage>,
    imageInputEnabled: Boolean,
): List<UIMessage> {
    if (!imageInputEnabled) return messages

    return buildList {
        messages.forEach { message ->
            add(message)
            if (message.role != MessageRole.ASSISTANT) return@forEach

            val toolImages = message.parts
                .filterIsInstance<UIMessagePart.Tool>()
                .filter { it.isExecuted }
                .flatMap { tool ->
                    tool.output
                        .filterIsInstance<UIMessagePart.Image>()
                        .mapIndexed { index, image ->
                            Triple(tool.toolName, index + 1, image)
                        }
                }

            if (toolImages.isEmpty()) return@forEach

            add(
                UIMessage(
                    role = MessageRole.USER,
                    parts = buildList {
                        add(
                            UIMessagePart.Text(
                                """
                                The previous tool call(s) returned image output. These images are tool results, not a new user request.
                                Use them together with the tool text output when reasoning about what to do next.
                                """.trimIndent(),
                                metadata = mergeMessagePartMetadata(
                                    metadata = null,
                                    entries = mapOf(
                                        TOOL_IMAGE_SYNTHETIC_FLAG to JsonPrimitive(true),
                                    )
                                )
                            )
                        )

                        toolImages.forEach { (toolName, imageIndex, image) ->
                            val metadata = mergeMessagePartMetadata(
                                metadata = image.metadata,
                                entries = mapOf(
                                    TOOL_IMAGE_SYNTHETIC_FLAG to JsonPrimitive(true),
                                    TOOL_IMAGE_REINJECTED_KEY to JsonPrimitive(true),
                                    TOOL_IMAGE_TOOL_NAME_KEY to JsonPrimitive(toolName),
                                    TOOL_IMAGE_INDEX_KEY to JsonPrimitive(imageIndex),
                                )
                            )
                            add(
                                UIMessagePart.Text(
                                    "Tool image from `$toolName` (#$imageIndex).",
                                    metadata = metadata
                                )
                            )
                            add(image.copy(metadata = metadata))
                        }
                    }
                )
            )
        }
    }
}
