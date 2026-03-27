package me.rerere.rikkahub.data.repository

import androidx.room.withTransaction
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.SourcePreviewChunkDAO
import me.rerere.rikkahub.data.db.entity.SourcePreviewChunkEntity
import me.rerere.rikkahub.data.db.index.IndexDatabase
import me.rerere.rikkahub.data.db.index.IndexMigrationManager
import me.rerere.rikkahub.data.db.index.dao.IndexSourcePreviewChunkDAO
import me.rerere.rikkahub.data.db.index.entity.IndexSourcePreviewChunkEntity
import me.rerere.rikkahub.data.model.SourcePreviewChunk
import java.time.Instant
import kotlin.uuid.Uuid

class SourcePreviewRepository(
    private val legacySourcePreviewChunkDAO: SourcePreviewChunkDAO,
    private val appDatabase: AppDatabase,
    private val indexSourcePreviewChunkDAO: IndexSourcePreviewChunkDAO,
    private val indexDatabase: IndexDatabase,
    private val indexMigrationManager: IndexMigrationManager,
) {
    suspend fun replaceConversationChunks(
        conversationId: Uuid,
        chunks: List<SourcePreviewChunk>,
    ) {
        if (!indexMigrationManager.shouldUseIndexBackend()) {
            appDatabase.withTransaction {
                legacySourcePreviewChunkDAO.deleteChunksOfConversation(conversationId.toString())
                if (chunks.isEmpty()) return@withTransaction
                legacySourcePreviewChunkDAO.insertAll(
                    chunks.map { chunk ->
                        SourcePreviewChunkEntity(
                            assistantId = chunk.assistantId.toString(),
                            conversationId = chunk.conversationId.toString(),
                            messageId = chunk.messageId.toString(),
                            role = chunk.role,
                            chunkOrder = chunk.chunkOrder,
                            prefixText = chunk.prefixText,
                            searchText = chunk.searchText,
                            blockType = chunk.blockType,
                            updatedAt = chunk.updatedAt.toEpochMilli(),
                        )
                    }
                )
            }
            return
        }

        indexDatabase.withTransaction {
            indexSourcePreviewChunkDAO.deleteChunksOfConversation(conversationId.toString())
            if (chunks.isEmpty()) return@withTransaction
            indexSourcePreviewChunkDAO.insertAll(
                chunks.map { chunk ->
                    IndexSourcePreviewChunkEntity(
                        assistantId = chunk.assistantId.toString(),
                        conversationId = chunk.conversationId.toString(),
                        messageId = chunk.messageId.toString(),
                        role = chunk.role,
                        chunkOrder = chunk.chunkOrder,
                        prefixText = chunk.prefixText,
                        searchText = chunk.searchText,
                        blockType = chunk.blockType,
                        updatedAt = chunk.updatedAt.toEpochMilli(),
                    )
                }
            )
        }
    }

    suspend fun getChunksOfAssistant(assistantId: Uuid): List<SourcePreviewChunk> {
        return if (indexMigrationManager.shouldUseIndexBackend()) {
            indexSourcePreviewChunkDAO.getChunksOfAssistant(assistantId.toString()).mapNotNull { it.toModel() }
        } else {
            legacySourcePreviewChunkDAO.getChunksOfAssistant(assistantId.toString()).mapNotNull { it.toModel() }
        }
    }

    suspend fun getChunksOfConversations(
        assistantId: Uuid,
        conversationIds: List<Uuid>,
    ): List<SourcePreviewChunk> {
        if (conversationIds.isEmpty()) return emptyList()
        return if (indexMigrationManager.shouldUseIndexBackend()) {
            indexSourcePreviewChunkDAO.getChunksOfConversations(
                assistantId = assistantId.toString(),
                conversationIds = conversationIds.map { it.toString() }
            ).mapNotNull { it.toModel() }
        } else {
            legacySourcePreviewChunkDAO.getChunksOfConversations(
                assistantId = assistantId.toString(),
                conversationIds = conversationIds.map { it.toString() }
            ).mapNotNull { it.toModel() }
        }
    }

    suspend fun deleteConversationChunks(conversationId: Uuid) {
        if (indexMigrationManager.shouldUseIndexBackend()) {
            indexSourcePreviewChunkDAO.deleteChunksOfConversation(conversationId.toString())
        } else {
            legacySourcePreviewChunkDAO.deleteChunksOfConversation(conversationId.toString())
        }
    }

    private fun SourcePreviewChunkEntity.toModel(): SourcePreviewChunk? {
        val assistantUuid = runCatching { Uuid.parse(assistantId) }.getOrNull() ?: return null
        val conversationUuid = runCatching { Uuid.parse(conversationId) }.getOrNull() ?: return null
        val messageUuid = runCatching { Uuid.parse(messageId) }.getOrNull() ?: return null
        return SourcePreviewChunk(
            id = id,
            assistantId = assistantUuid,
            conversationId = conversationUuid,
            messageId = messageUuid,
            role = role,
            chunkOrder = chunkOrder,
            prefixText = prefixText,
            searchText = searchText,
            blockType = blockType,
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private fun IndexSourcePreviewChunkEntity.toModel(): SourcePreviewChunk? {
        val assistantUuid = runCatching { Uuid.parse(assistantId) }.getOrNull() ?: return null
        val conversationUuid = runCatching { Uuid.parse(conversationId) }.getOrNull() ?: return null
        val messageUuid = runCatching { Uuid.parse(messageId) }.getOrNull() ?: return null
        return SourcePreviewChunk(
            id = id,
            assistantId = assistantUuid,
            conversationId = conversationUuid,
            messageId = messageUuid,
            role = role,
            chunkOrder = chunkOrder,
            prefixText = prefixText,
            searchText = searchText,
            blockType = blockType,
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }
}
