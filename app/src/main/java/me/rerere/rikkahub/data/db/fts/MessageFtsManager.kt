package me.rerere.rikkahub.data.db.fts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import java.time.Instant

data class MessageSearchResult(
    val nodeId: String,
    val messageId: String,
    val conversationId: String,
    val title: String,
    val updateAt: Instant,
    val snippet: String,
)

private const val TAG = "MessageFtsManager"

class MessageFtsManager(private val database: AppDatabase) {

    private val db get() = database.openHelper.writableDatabase

    suspend fun indexConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        val conversationId = conversation.id.toString()
        db.execSQL("DELETE FROM message_fts WHERE conversation_id = ?", arrayOf(conversationId))
        insertNodes(
            conversationId = conversationId,
            title = conversation.title,
            updateAtMillis = conversation.updateAt.toEpochMilli(),
            nodes = conversation.messageNodes,
        )
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        db.execSQL("DELETE FROM message_fts WHERE conversation_id = ?", arrayOf(conversationId))
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.execSQL("DELETE FROM message_fts")
    }

    suspend fun syncConversation(
        conversation: Conversation,
        changedNodes: List<MessageNode>,
        deletedNodeIds: List<String>,
    ) = withContext(Dispatchers.IO) {
        val conversationId = conversation.id.toString()
        db.execSQL(
            "UPDATE message_fts SET title = ?, update_at = ? WHERE conversation_id = ?",
            arrayOf<Any>(conversation.title, conversation.updateAt.toEpochMilli(), conversationId)
        )
        if (deletedNodeIds.isNotEmpty()) {
            deleteNodes(conversationId, deletedNodeIds)
        }
        if (changedNodes.isNotEmpty()) {
            val changedNodeIds = changedNodes.map { it.id.toString() }
            deleteNodes(conversationId, changedNodeIds)
            insertNodes(
                conversationId = conversationId,
                title = conversation.title,
                updateAtMillis = conversation.updateAt.toEpochMilli(),
                nodes = changedNodes,
            )
        }
    }

    suspend fun search(keyword: String): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MessageSearchResult>()
        val cursor = db.query(
            """
            SELECT node_id, message_id, conversation_id, title, update_at,
                   simple_snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
            FROM message_fts
            WHERE text MATCH jieba_query(?)
            ORDER BY rank, update_at DESC
            LIMIT 50
            """.trimIndent(),
            arrayOf(keyword)
        )
        Log.i(TAG, "search: $keyword")
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    MessageSearchResult(
                        nodeId = it.getString(0),
                        messageId = it.getString(1),
                        conversationId = it.getString(2),
                        title = it.getString(3),
                        updateAt = Instant.ofEpochMilli(it.getLong(4)),
                        snippet = it.getString(5),
                    )
                )
            }
        }
        results
    }

    suspend fun searchConversation(
        conversationId: String,
        keyword: String,
        limit: Int,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MessageSearchResult>()
        val cursor = db.query(
            """
            SELECT node_id, message_id, conversation_id, title, update_at,
                   simple_snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
            FROM message_fts
            WHERE conversation_id = ?
              AND text MATCH jieba_query(?)
            ORDER BY rank, update_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(conversationId, keyword, limit.toString())
        )
        Log.i(TAG, "searchConversation: $conversationId $keyword")
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    MessageSearchResult(
                        nodeId = it.getString(0),
                        messageId = it.getString(1),
                        conversationId = it.getString(2),
                        title = it.getString(3),
                        updateAt = Instant.ofEpochMilli(it.getLong(4)),
                        snippet = it.getString(5),
                    )
                )
            }
        }
        results
    }

    private fun deleteNodes(conversationId: String, nodeIds: List<String>) {
        val placeholders = nodeIds.joinToString(",") { "?" }
        db.execSQL(
            "DELETE FROM message_fts WHERE conversation_id = ? AND node_id IN ($placeholders)",
            arrayOf(conversationId, *nodeIds.toTypedArray())
        )
    }

    private fun insertNodes(
        conversationId: String,
        title: String,
        updateAtMillis: Long,
        nodes: List<MessageNode>,
    ) {
        nodes.forEach { node ->
            node.messages.forEach { message ->
                val text = message.extractFtsText()
                if (text.isBlank()) return@forEach
                db.execSQL(
                    "INSERT INTO message_fts(text, node_id, message_id, conversation_id, title, update_at) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        text,
                        node.id.toString(),
                        message.id.toString(),
                        conversationId,
                        title,
                        updateAtMillis.toString(),
                    )
                )
            }
        }
    }
}

private fun UIMessage.extractFtsText(): String =
    parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .take(10_000)
