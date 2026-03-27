package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.db.dao.PendingLedgerBatchDAO
import me.rerere.rikkahub.data.db.entity.PendingLedgerBatchEntity
import me.rerere.rikkahub.data.db.index.IndexMigrationManager
import me.rerere.rikkahub.data.db.index.dao.IndexPendingLedgerBatchDAO
import me.rerere.rikkahub.data.db.index.entity.IndexPendingLedgerBatchEntity
import me.rerere.rikkahub.data.model.PendingLedgerBatch
import java.time.Instant
import kotlin.uuid.Uuid

class PendingLedgerBatchRepository(
    private val legacyDAO: PendingLedgerBatchDAO,
    private val indexDAO: IndexPendingLedgerBatchDAO,
    private val indexMigrationManager: IndexMigrationManager,
) {
    suspend fun getAllOfConversation(conversationId: Uuid): List<PendingLedgerBatch> {
        return if (indexMigrationManager.shouldUseIndexBackend()) {
            indexDAO.getBatchesOfConversation(conversationId.toString()).map(::entityToModel)
        } else {
            legacyDAO.getBatchesOfConversation(conversationId.toString()).map(::entityToModel)
        }
    }

    suspend fun getProcessableOfConversation(conversationId: Uuid): List<PendingLedgerBatch> {
        return if (indexMigrationManager.shouldUseIndexBackend()) {
            indexDAO.getProcessableBatchesOfConversation(conversationId.toString()).map(::entityToModel)
        } else {
            legacyDAO.getProcessableBatchesOfConversation(conversationId.toString()).map(::entityToModel)
        }
    }

    suspend fun upsertPendingBatch(
        conversationId: Uuid,
        eventId: Long,
        startIndex: Int,
        endIndex: Int,
        incrementalMessages: String,
    ): PendingLedgerBatch {
        val now = Instant.now()
        if (indexMigrationManager.shouldUseIndexBackend()) {
            val existing = indexDAO.getBatchByConversationAndEvent(conversationId.toString(), eventId)
            val indexEntity = IndexPendingLedgerBatchEntity(
                id = existing?.id ?: 0L,
                conversationId = conversationId.toString(),
                eventId = eventId,
                startIndex = startIndex,
                endIndex = endIndex,
                incrementalMessages = incrementalMessages,
                status = "pending",
                attemptCount = existing?.attemptCount ?: 0,
                lastError = "",
                createdAt = existing?.createdAt ?: now.toEpochMilli(),
                updatedAt = now.toEpochMilli(),
            )
            return if (existing == null) {
                entityToModel(indexEntity.copy(id = indexDAO.insert(indexEntity)))
            } else {
                indexDAO.update(indexEntity)
                entityToModel(indexEntity)
            }
        }

        val existing = legacyDAO.getBatchByConversationAndEvent(conversationId.toString(), eventId)
        val entity = PendingLedgerBatchEntity(
            id = existing?.id ?: 0L,
            conversationId = conversationId.toString(),
            eventId = eventId,
            startIndex = startIndex,
            endIndex = endIndex,
            incrementalMessages = incrementalMessages,
            status = "pending",
            attemptCount = existing?.attemptCount ?: 0,
            lastError = "",
            createdAt = existing?.createdAt ?: now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )
        return if (existing == null) {
            entityToModel(entity.copy(id = legacyDAO.insert(entity)))
        } else {
            legacyDAO.update(entity)
            entityToModel(entity)
        }
    }

    suspend fun updateStatus(
        batch: PendingLedgerBatch,
        status: String,
        attemptCount: Int = batch.attemptCount,
        lastError: String = batch.lastError,
    ): PendingLedgerBatch {
        val updated = batch.copy(
            status = status,
            attemptCount = attemptCount,
            lastError = lastError,
            updatedAt = Instant.now(),
        )
        if (indexMigrationManager.shouldUseIndexBackend()) {
            indexDAO.update(modelToIndexEntity(updated))
        } else {
            legacyDAO.update(modelToEntity(updated))
        }
        return updated
    }

    suspend fun deleteByConversationAndEvent(conversationId: Uuid, eventId: Long) {
        if (indexMigrationManager.shouldUseIndexBackend()) {
            indexDAO.deleteByConversationAndEvent(conversationId.toString(), eventId)
        } else {
            legacyDAO.deleteByConversationAndEvent(conversationId.toString(), eventId)
        }
    }

    private fun entityToModel(entity: PendingLedgerBatchEntity): PendingLedgerBatch {
        return PendingLedgerBatch(
            id = entity.id,
            conversationId = entity.conversationId,
            eventId = entity.eventId,
            startIndex = entity.startIndex,
            endIndex = entity.endIndex,
            incrementalMessages = entity.incrementalMessages,
            status = entity.status,
            attemptCount = entity.attemptCount,
            lastError = entity.lastError,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt),
        )
    }

    private fun entityToModel(entity: IndexPendingLedgerBatchEntity): PendingLedgerBatch {
        return PendingLedgerBatch(
            id = entity.id,
            conversationId = entity.conversationId,
            eventId = entity.eventId,
            startIndex = entity.startIndex,
            endIndex = entity.endIndex,
            incrementalMessages = entity.incrementalMessages,
            status = entity.status,
            attemptCount = entity.attemptCount,
            lastError = entity.lastError,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt),
        )
    }

    private fun modelToEntity(model: PendingLedgerBatch): PendingLedgerBatchEntity {
        return PendingLedgerBatchEntity(
            id = model.id,
            conversationId = model.conversationId,
            eventId = model.eventId,
            startIndex = model.startIndex,
            endIndex = model.endIndex,
            incrementalMessages = model.incrementalMessages,
            status = model.status,
            attemptCount = model.attemptCount,
            lastError = model.lastError,
            createdAt = model.createdAt.toEpochMilli(),
            updatedAt = model.updatedAt.toEpochMilli(),
        )
    }

    private fun modelToIndexEntity(model: PendingLedgerBatch): IndexPendingLedgerBatchEntity {
        return IndexPendingLedgerBatchEntity(
            id = model.id,
            conversationId = model.conversationId,
            eventId = model.eventId,
            startIndex = model.startIndex,
            endIndex = model.endIndex,
            incrementalMessages = model.incrementalMessages,
            status = model.status,
            attemptCount = model.attemptCount,
            lastError = model.lastError,
            createdAt = model.createdAt.toEpochMilli(),
            updatedAt = model.updatedAt.toEpochMilli(),
        )
    }
}
