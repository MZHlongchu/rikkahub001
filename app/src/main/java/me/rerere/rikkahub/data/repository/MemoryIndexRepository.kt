package me.rerere.rikkahub.data.repository

import androidx.room.withTransaction
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.MemoryIndexChunkDAO
import me.rerere.rikkahub.data.db.entity.MemoryIndexChunkEntity
import me.rerere.rikkahub.data.db.index.IndexDatabase
import me.rerere.rikkahub.data.db.index.IndexMigrationManager
import me.rerere.rikkahub.data.db.index.IndexVectorTableManager
import me.rerere.rikkahub.data.db.index.VectorInsertRecord
import me.rerere.rikkahub.data.db.index.dao.IndexMemoryIndexChunkDAO
import me.rerere.rikkahub.data.db.index.dao.IndexVectorRow
import me.rerere.rikkahub.data.db.index.entity.IndexMemoryIndexChunkEntity
import me.rerere.rikkahub.data.model.MemoryChunkMetadata
import me.rerere.rikkahub.data.model.MemoryIndexChunk
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

class MemoryIndexRepository(
    private val legacyMemoryIndexChunkDAO: MemoryIndexChunkDAO,
    private val conversationDAO: ConversationDAO,
    private val appDatabase: AppDatabase,
    private val indexMemoryIndexChunkDAO: IndexMemoryIndexChunkDAO,
    private val indexDatabase: IndexDatabase,
    private val indexMigrationManager: IndexMigrationManager,
    private val vectorTableManager: IndexVectorTableManager,
) {
    suspend fun replaceConversationChunks(
        assistantId: Uuid,
        conversationId: Uuid,
        chunks: List<MemoryIndexChunk>,
    ) {
        if (!indexMigrationManager.shouldUseIndexBackend()) {
            appDatabase.withTransaction {
                legacyMemoryIndexChunkDAO.deleteChunksOfConversation(conversationId.toString())
                if (chunks.isEmpty()) return@withTransaction
                legacyMemoryIndexChunkDAO.insertAll(
                    chunks.map { chunk ->
                        MemoryIndexChunkEntity(
                            assistantId = assistantId.toString(),
                            conversationId = conversationId.toString(),
                            sectionKey = chunk.sectionKey,
                            chunkOrder = chunk.chunkOrder,
                            content = chunk.content,
                            tokenEstimate = chunk.tokenEstimate,
                            embeddingJson = JsonInstant.encodeToString(chunk.embedding),
                            metadataJson = JsonInstant.encodeToString(chunk.metadata),
                            updatedAt = chunk.updatedAt.toEpochMilli(),
                        )
                    }
                )
            }
            return
        }

        val existingVectorRows = indexMemoryIndexChunkDAO.getVectorRowsOfConversation(conversationId.toString())
        val rowIds = indexDatabase.withTransaction {
            indexMemoryIndexChunkDAO.deleteChunksOfConversation(conversationId.toString())
            if (chunks.isEmpty()) return@withTransaction emptyList<Long>()
            indexMemoryIndexChunkDAO.insertAll(
                chunks.map { chunk ->
                    IndexMemoryIndexChunkEntity(
                        assistantId = assistantId.toString(),
                        conversationId = conversationId.toString(),
                        sectionKey = chunk.sectionKey,
                        chunkOrder = chunk.chunkOrder,
                        content = chunk.content,
                        tokenEstimate = chunk.tokenEstimate,
                        embeddingDimension = chunk.embedding.size,
                        metadataJson = JsonInstant.encodeToString(chunk.metadata),
                        updatedAt = chunk.updatedAt.toEpochMilli(),
                    )
                }
            )
        }
        if (chunks.isNotEmpty()) {
            val recordsByDimension = chunks.zip(rowIds).groupBy({ it.first.embedding.size }, { pair ->
                VectorInsertRecord(
                    chunkId = pair.second,
                    embeddingJson = JsonInstant.encodeToString(pair.first.embedding),
                )
            })
            runCatching {
                recordsByDimension.forEach { (dimension, records) ->
                    vectorTableManager.insertMemoryVectors(dimension, records)
                }
            }.onFailure { error ->
                indexDatabase.withTransaction {
                    if (rowIds.isNotEmpty()) {
                        indexMemoryIndexChunkDAO.deleteByIds(rowIds)
                    }
                }
                throw error
            }
        }
        val oldVectorsByDimension = existingVectorRows.toDimensionMap()
        if (oldVectorsByDimension.isNotEmpty()) {
            vectorTableManager.deleteMemoryVectors(oldVectorsByDimension)
        }
    }

    suspend fun getChunksOfAssistant(assistantId: Uuid): List<IndexedChunkWithConversation> {
        val chunks = if (indexMigrationManager.shouldUseIndexBackend()) {
            indexMemoryIndexChunkDAO.getChunksOfAssistant(assistantId.toString())
                .mapNotNull { it.toMemoryIndexChunk() }
        } else {
            legacyMemoryIndexChunkDAO.getChunksOfAssistant(assistantId.toString())
                .mapNotNull { it.toMemoryIndexChunk() }
        }
        if (chunks.isEmpty()) return emptyList()

        val titleByConversation = chunks
            .map { it.conversationId.toString() }
            .distinct()
            .associateWith { conversationId ->
                conversationDAO.getConversationById(conversationId)?.title.orEmpty()
            }

        return chunks.map { chunk ->
            IndexedChunkWithConversation(
                chunk = chunk,
                conversationTitle = titleByConversation[chunk.conversationId.toString()].orEmpty(),
            )
        }
    }

    suspend fun searchVectorDistances(
        candidateChunkIds: List<Long>,
        queryEmbedding: List<Float>,
        limit: Int,
    ): Map<Long, Double> {
        check(indexMigrationManager.shouldUseIndexBackend()) {
            "Memory vector search requires the migrated index backend"
        }
        return vectorTableManager.searchMemoryDistances(
            candidateIds = candidateChunkIds,
            queryEmbeddingJson = JsonInstant.encodeToString(queryEmbedding),
            dimension = queryEmbedding.size,
            limit = limit,
        )
    }

    suspend fun deleteConversationChunks(conversationId: Uuid) {
        if (indexMigrationManager.shouldUseIndexBackend()) {
            val vectorRowsByDimension = indexMemoryIndexChunkDAO
                .getVectorRowsOfConversation(conversationId.toString())
                .toDimensionMap()
            indexMemoryIndexChunkDAO.deleteChunksOfConversation(conversationId.toString())
            if (vectorRowsByDimension.isNotEmpty()) {
                vectorTableManager.deleteMemoryVectors(vectorRowsByDimension)
            }
        } else {
            legacyMemoryIndexChunkDAO.deleteChunksOfConversation(conversationId.toString())
        }
    }

    private fun MemoryIndexChunkEntity.toMemoryIndexChunk(): MemoryIndexChunk? {
        val embedding = runCatching {
            JsonInstant.decodeFromString<List<Float>>(embeddingJson)
        }.getOrNull() ?: return null
        val metadata = runCatching {
            JsonInstant.decodeFromString<MemoryChunkMetadata>(metadataJson)
        }.getOrElse { MemoryChunkMetadata(sectionKey = sectionKey) }
        val assistantUuid = runCatching { Uuid.parse(assistantId) }.getOrNull() ?: return null
        val conversationUuid = runCatching { Uuid.parse(conversationId) }.getOrNull() ?: return null
        return MemoryIndexChunk(
            id = id,
            assistantId = assistantUuid,
            conversationId = conversationUuid,
            sectionKey = sectionKey,
            chunkOrder = chunkOrder,
            content = content,
            tokenEstimate = tokenEstimate,
            embedding = embedding,
            metadata = metadata.copy(sectionKey = metadata.sectionKey.ifBlank { sectionKey }),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private fun IndexMemoryIndexChunkEntity.toMemoryIndexChunk(): MemoryIndexChunk? {
        val metadata = runCatching {
            JsonInstant.decodeFromString<MemoryChunkMetadata>(metadataJson)
        }.getOrElse { MemoryChunkMetadata(sectionKey = sectionKey) }
        val assistantUuid = runCatching { Uuid.parse(assistantId) }.getOrNull() ?: return null
        val conversationUuid = runCatching { Uuid.parse(conversationId) }.getOrNull() ?: return null
        return MemoryIndexChunk(
            id = id,
            assistantId = assistantUuid,
            conversationId = conversationUuid,
            sectionKey = sectionKey,
            chunkOrder = chunkOrder,
            content = content,
            tokenEstimate = tokenEstimate,
            embedding = emptyList(),
            metadata = metadata.copy(sectionKey = metadata.sectionKey.ifBlank { sectionKey }),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }
}

private fun List<IndexVectorRow>.toDimensionMap(): Map<Int, List<Long>> {
    return groupBy({ it.embeddingDimension }, { it.id })
}

data class IndexedChunkWithConversation(
    val chunk: MemoryIndexChunk,
    val conversationTitle: String,
)
