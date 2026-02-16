package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Agent Skill - 可导入的技能包，包含 SKILL.md 指令内容
 * 技能内容会被动态注入到对话中
 */
@Serializable
data class AgentSkill(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val content: String = "",       // SKILL.md 的正文内容（详细说明）
    val author: String = "",
    val version: String = "1.0.0",
    val icon: String? = null,       // 图标 URI
    val source: SkillSource = SkillSource.LOCAL,
    val sourceUrl: String? = null,   // 来源 URL（市场下载时记录）
    val enabled: Boolean = true,
    
    // 触发相关配置
    val triggerMode: TriggerMode = TriggerMode.ALWAYS,  // 触发模式
    val triggerKeywords: List<String> = emptyList(),     // 触发关键词列表
    val useRegex: Boolean = false,                      // 是否使用正则匹配
    val caseSensitive: Boolean = false,                // 是否大小写敏感
    val scanDepth: Int = 3,                             // 扫描最近N条消息
    
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
 * 技能触发模式
 */
@Serializable
enum class TriggerMode {
    @SerialName("always")
    ALWAYS,         // 总是触发（注入元信息，但不注入详细内容）
    
    @SerialName("keyword")
    KEYWORD,        // 关键词触发（匹配时注入详细内容）
    
    @SerialName("never")
    NEVER,          // 从不触发（仅保留在列表中）
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
 * triggerMode: keyword
 * triggerKeywords:
 *   - review
 *   - code
 *   - 审查
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
        
        // 解析触发相关配置
        val triggerModeStr = extractFrontmatterValue(frontmatter, "triggerMode") ?: "always"
        val triggerMode = try {
            TriggerMode.valueOf(triggerModeStr.uppercase())
        } catch (e: Exception) {
            TriggerMode.ALWAYS
        }
        val triggerKeywords = extractFrontmatterList(frontmatter, "triggerKeywords")
        val useRegex = extractFrontmatterValue(frontmatter, "useRegex")?.toBoolean() ?: false
        val caseSensitive = extractFrontmatterValue(frontmatter, "caseSensitive")?.toBoolean() ?: false
        val scanDepth = extractFrontmatterValue(frontmatter, "scanDepth")?.toIntOrNull() ?: 3

        return AgentSkill(
            name = name,
            description = description,
            content = body,
            author = author,
            version = version,
            triggerMode = triggerMode,
            triggerKeywords = triggerKeywords,
            useRegex = useRegex,
            caseSensitive = caseSensitive,
            scanDepth = scanDepth,
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

private fun extractFrontmatterList(frontmatter: String, key: String): List<String> {
    // 匹配 YAML 列表格式：
    // key:
    //   - item1
    //   - item2
    val listRegex = Regex("^$key:\\s*\\n((?:\\s*-\\s*.+\\n?)+)", RegexOption.MULTILINE)
    val match = listRegex.find(frontmatter)
    
    if (match != null) {
        val listContent = match.groupValues[1]
        return listContent.lines()
            .map { it.trim() }
            .filter { it.startsWith("-") }
            .map { it.substring(1).trim() }
            .filter { it.isNotBlank() }
    }
    
    // 尝试匹配单行格式：key: [item1, item2]
    val inlineRegex = Regex("^$key:\\s*\\[(.*?)\\]", RegexOption.MULTILINE)
    val inlineMatch = inlineRegex.find(frontmatter)
    if (inlineMatch != null) {
        return inlineMatch.groupValues[1]
            .split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotBlank() }
    }
    
    return emptyList()
}

private fun extractFirstHeading(content: String): String? {
    val regex = Regex("^#+\\s+(.+)$", RegexOption.MULTILINE)
    return regex.find(content)?.groupValues?.get(1)?.trim()
}

/**
 * 检查技能是否应该被触发
 */
fun AgentSkill.shouldTrigger(context: String): Boolean {
    when (triggerMode) {
        TriggerMode.ALWAYS -> return true
        TriggerMode.NEVER -> return false
        TriggerMode.KEYWORD -> {
            if (triggerKeywords.isEmpty()) return false
            
            return triggerKeywords.any { keyword ->
                if (useRegex) {
                    try {
                        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                        Regex(keyword, options).containsMatchIn(context)
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    if (caseSensitive) {
                        context.contains(keyword)
                    } else {
                        context.contains(keyword, ignoreCase = true)
                    }
                }
            }
        }
    }
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

