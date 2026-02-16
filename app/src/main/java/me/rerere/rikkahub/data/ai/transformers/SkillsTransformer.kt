package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.AgentSkill
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Agent Skills 提示词注入转换器
 *
 * 1. 默认在系统提示词后注入所有启用技能的元信息（name + description）
 * 2. 如果技能设置了 triggerKeywords，则在匹配时动态注入该技能的详细内容
 *    到对话底部（最新消息之前）
 */
object SkillsTransformer : InputMessageTransformer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val settings = ctx.settings
        val assistant = ctx.assistant

        // 获取当前助手关联的所有已启用技能
        val enabledSkills = settings.agentSkills.filter {
            it.id in assistant.skillIds && it.enabled
        }

        if (enabledSkills.isEmpty()) {
            return messages
        }

        val result = messages.toMutableList()

        // 1. 检查是否有系统消息，找到系统消息索引
        val systemIndex = result.indexOfFirst {
            it.role == MessageRole.SYSTEM
        }

        // 2. 构建技能元信息汇总（默认注入到系统提示词）
        val skillsMetaInfo = buildSkillsMetaInfo(enabledSkills)

        // 3. 检查哪些技能应该被动态触发（基于关键词匹配）
        val contextForMatching = messages
            .takeLast(3) // 最近3条消息用于匹配
            .joinToString("\n") { it.toText() }

        val triggeredSkills = enabledSkills.filter { skill ->
            skill.shouldTrigger(contextForMatching)
        }

        // 构建动态触发内容
        val triggeredContent = if (triggeredSkills.isNotEmpty()) {
            buildTriggeredSkillsContent(triggeredSkills)
        } else ""

        // 4. 将元信息注入到系统提示词后
        if (systemIndex >= 0) {
            val systemMessage = result[systemIndex]
            val originalText = systemMessage.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }

            val newText = buildString {
                append(originalText)
                if (skillsMetaInfo.isNotBlank()) {
                    appendLine()
                    appendLine()
                    append(skillsMetaInfo)
                }
            }

            result[systemIndex] = systemMessage.copy(
                parts = listOf(UIMessagePart.Text(newText))
            )
        } else {
            // 没有系统消息时，创建一个新的系统消息
            if (skillsMetaInfo.isNotBlank()) {
                result.add(0, UIMessage.system(skillsMetaInfo))
            }
        }

        // 5. 将触发的技能内容注入到最新消息之前
        if (triggeredContent.isNotBlank() && result.isNotEmpty()) {
            // 在最新消息之前插入触发内容
            val insertIndex = findSafeInsertIndex(result, result.size)
            result.add(
                insertIndex,
                UIMessage.system(
                    "[Skill Context Activated]\n$triggeredContent\n[End of Skill Context]"
                )
            )
        }

        return result
    }

    /**
     * 构建技能元信息（列出所有可用技能）
     */
    private fun buildSkillsMetaInfo(skills: List<AgentSkill>): String {
        if (skills.isEmpty()) return ""

        return buildString {
            appendLine("--- Agent Skills ---")
            appendLine("The following skills are available for this assistant:")
            appendLine()

            skills.forEach { skill ->
                append("• **${skill.name}**")
                if (skill.description.isNotBlank()) {
                    append(": ${skill.description.take(100)}")
                    if (skill.description.length > 100) {
                        append("...")
                    }
                }
                appendLine()
            }

            appendLine()
            appendLine("The detailed skill content will be injected when relevant.")
            append("---")
        }.trim()
    }

    /**
     * 构建被触发技能的详细内容
     */
    private fun buildTriggeredSkillsContent(skills: List<AgentSkill>): String {
        if (skills.isEmpty()) return ""

        return buildString {
            skills.forEachIndexed { index, skill ->
                if (index > 0) appendLine("\n---\n")
                appendLine("### Skill: ${skill.name}")
                if (skill.description.isNotBlank()) {
                    appendLine("*${skill.description}*")
                    appendLine()
                }
                if (skill.content.isNotBlank()) {
                    append(skill.content.trim())
                }
            }
        }.trim()
    }

    /**
     * 查找安全的插入位置
     */
    private fun findSafeInsertIndex(messages: List<UIMessage>, targetIndex: Int): Int {
        return targetIndex.coerceIn(0, messages.size)
    }
}
