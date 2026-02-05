package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.sandbox.SandboxEngine
import java.io.File

private const val TAG = "SandboxContextFileTransformer"

/**
 * 沙箱上下文文件注入 Transformer
 *
 * 从对话沙箱根目录读取 rikkahub.md 文件，并注入到系统提示词之后。
 * 这样可以提供持久化的项目上下文（项目背景、用户偏好等），避免多次压缩导致摘要堆积。
 */
object SandboxContextFileTransformer : InputMessageTransformer {

    private const val CONTEXT_FILE_NAME = "rikkahub.md"
    private const val MAX_FILE_SIZE = 50 * 1024  // 50KB

    /**
     * 文件缓存：conversationId -> (content, lastModified)
     */
    private val fileCache = LinkedHashMap<String, CachedContent>(
        50,  // 最多缓存50个文件
        0.75f,  // 访问顺序，LRU淘汰
        true   // 访问顺序
    )

    data class CachedContent(
        val content: String,
        val lastModified: Long
    )

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>
    ): List<UIMessage> {
        val conversationId = ctx.conversationId
        if (conversationId.isEmpty()) {
            // conversationId 为空，跳过（可能是预览模式）
            return messages
        }

        val fileContent = readSandboxContextFile(
            context = ctx.context,
            conversationId = conversationId
        ) ?: return messages  // 文件不存在，返回原消息列表

        return injectAfterSystemPrompt(messages, fileContent)
    }

    /**
     * 读取沙箱上下文文件
     *
     * @return 文件内容，如果文件不存在或读取失败则返回 null
     */
    private suspend fun readSandboxContextFile(
        context: Context,
        conversationId: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val sandboxDir = SandboxEngine.getSandboxDir(context, conversationId)
                val contextFile = File(sandboxDir, CONTEXT_FILE_NAME)

                // 检查文件是否存在
                if (!contextFile.exists()) {
                    return@withContext null
                }

                // 检查文件是否可读
                if (!contextFile.canRead()) {
                    Log.w(TAG, "Cannot read context file: ${contextFile.absolutePath}")
                    return@withContext null
                }

                // 检查缓存
                val currentModified = contextFile.lastModified()
                val cached = fileCache[conversationId]
                if (cached != null && cached.lastModified == currentModified) {
                    Log.d(TAG, "Using cached context file for conversation: $conversationId")
                    return@withContext cached.content
                }

                // 读取文件内容
                val rawContent = contextFile.readText(Charsets.UTF_8)

                // 检查文件大小并截断
                val finalContent = if (rawContent.length > MAX_FILE_SIZE) {
                    Log.w(TAG, "Context file exceeds ${MAX_FILE_SIZE / 1024}KB, truncating")
                    rawContent.take(MAX_FILE_SIZE) +
                        "\n\n[Context file truncated: exceeded ${MAX_FILE_SIZE / 1024}KB limit]"
                } else {
                    rawContent
                }

                // 更新缓存
                fileCache[conversationId] = CachedContent(finalContent, currentModified)

                Log.d(TAG, "Loaded context file for conversation: $conversationId (${finalContent.length} bytes)")
                finalContent
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read sandbox context file for conversation: $conversationId", e)
                null
            }
        }
    }

    /**
     * 将内容注入到系统提示词之后
     */
    private fun injectAfterSystemPrompt(
        messages: List<UIMessage>,
        content: String
    ): List<UIMessage> {
        if (content.isBlank()) return messages

        val result = messages.toMutableList()
        val systemIndex = result.indexOfFirst { it.role == me.rerere.ai.core.MessageRole.SYSTEM }

        if (systemIndex >= 0) {
            // 找到系统消息，在其后追加内容
            val systemMessage = result[systemIndex]
            val originalText = systemMessage.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }

            val newText = buildString {
                append(originalText)
                appendLine()
                appendLine()
                append("--- Sandbox Context (rikkahub.md) ---")
                appendLine()
                append(content)
            }

            result[systemIndex] = systemMessage.copy(
                parts = listOf(UIMessagePart.Text(newText))
            )
        } else {
            // 没有系统消息，在开头插入一条
            val contextMessage = UIMessage.system(
                buildString {
                    append("--- Sandbox Context (rikkahub.md) ---")
                    appendLine()
                    append(content)
                }
            )
            result.add(0, contextMessage)
        }

        return result
    }

    /**
     * 清除指定对话的缓存（用于文件被修改时）
     */
    fun clearCache(conversationId: String) {
        fileCache.remove(conversationId)
    }

    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        fileCache.clear()
    }
}
