package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantSkills

/**
 * Agent Skills 提示词注入转换器
 *
 * 将助手关联的技能内容注入到系统提示词后面
 */
object SkillsTransformer : InputMessageTransformer {

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val skills = ctx.settings.getAssistantSkills(ctx.assistant)
        if (skills.isEmpty()) {
            return messages
        }

        // 检查是否有系统消息
        val systemIndex = messages.indexOfFirst { it.role == me.rerere.ai.core.MessageRole.SYSTEM }
        
        // 构建技能提示词
        val skillsContent = buildSkillsPrompt(skills.map { it.content })
        if (skillsContent.isBlank()) {
            return messages
        }

        val result = messages.toMutableList()
        
        if (systemIndex >= 0) {
            // 在系统消息后面追加技能内容
            val systemMessage = result[systemIndex]
            val originalText = systemMessage.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }
            
            val newText = buildString {
                append(originalText)
                appendLine()
                appendLine()
                append(skillsContent)
            }
            
            result[systemIndex] = systemMessage.copy(
                parts = listOf(UIMessagePart.Text(newText))
            )
        } else {
            // 没有系统消息时，创建一个新的系统消息
            result.add(0, UIMessage.system(skillsContent))
        }
        
        return result
    }

    private fun buildSkillsPrompt(skillContents: List<String>): String {
        if (skillContents.isEmpty()) return ""
        
        return buildString {
            appendLine()
            appendLine("--- Agent Skills ---")
            skillContents.forEachIndexed { index, content ->
                if (content.isNotBlank()) {
                    appendLine()
                    append(content)
                    if (!content.endsWith("\n")) {
                        appendLine()
                    }
                }
            }
            appendLine("--- End of Agent Skills ---")
        }
    }
}
