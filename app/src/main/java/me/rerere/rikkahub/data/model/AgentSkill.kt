package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Agent Skill - 可导入的技能包，包含 SKILL.md 指令内容
 * 技能内容会被注入到助手的系统提示词中
 */
@Serializable
data class AgentSkill(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val content: String = "",       // SKILL.md 的正文内容
    val author: String = "",
    val version: String = "1.0.0",
    val icon: String? = null,       // 图标 URI
    val source: SkillSource = SkillSource.LOCAL,
    val sourceUrl: String? = null,  // 来源 URL（市场下载时记录）
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class SkillSource {
    @SerialName("local")
    LOCAL,

    @SerialName("marketplace")
    MARKETPLACE,
}

/**
 * 解析 SKILL.md 内容，支持 YAML frontmatter
 *
 * 格式示例:
 * ```
 * ---
 * name: Code Review Expert
 * description: Helps review code
 * author: someone
 * version: 1.0.0
 * ---
 *
 * # Code Review Expert
 * You are an expert code reviewer...
 * ```
 */
fun parseSkillMd(raw: String): AgentSkill {
    val trimmed = raw.trim()
    val frontmatterRegex = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n?", RegexOption.DOT_MATCHES_ALL)
    val match = frontmatterRegex.find(trimmed)

    if (match != null) {
        val frontmatter = match.groupValues[1]
        val body = trimmed.substring(match.range.last + 1).trim()

        val name = extractFrontmatterValue(frontmatter, "name")
            ?: extractFirstHeading(body)
            ?: "Unnamed Skill"
        val description = extractFrontmatterValue(frontmatter, "description") ?: ""
        val author = extractFrontmatterValue(frontmatter, "author") ?: ""
        val version = extractFrontmatterValue(frontmatter, "version") ?: "1.0.0"

        return AgentSkill(
            name = name,
            description = description,
            content = body,
            author = author,
            version = version,
        )
    }

    // 无 frontmatter，用第一个标题作为名称
    val name = extractFirstHeading(trimmed) ?: "Unnamed Skill"
    return AgentSkill(
        name = name,
        content = trimmed,
    )
}

private fun extractFrontmatterValue(frontmatter: String, key: String): String? {
    val regex = Regex("^$key:\\s*(.+)$", RegexOption.MULTILINE)
    return regex.find(frontmatter)?.groupValues?.get(1)?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
}

private fun extractFirstHeading(content: String): String? {
    val regex = Regex("^#+\\s+(.+)$", RegexOption.MULTILINE)
    return regex.find(content)?.groupValues?.get(1)?.trim()
}

/**
 * 获取助手关联的所有启用技能的合并内容
 */
fun List<AgentSkill>.getSkillsPromptContent(skillIds: Set<Uuid>): String {
    return this
        .filter { it.id in skillIds && it.enabled }
        .joinToString("\n\n") { skill ->
            buildString {
                appendLine("<!-- Skill: ${skill.name} -->")
                append(skill.content)
            }
        }
}
