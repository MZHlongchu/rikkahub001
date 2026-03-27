package me.rerere.rikkahub.data.db.index.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import me.rerere.rikkahub.data.db.index.entity.IndexPendingLedgerBatchEntity

@Dao
interface IndexPendingLedgerBatchDAO {
    @Query(
        """
        SELECT * FROM pending_ledger_batch
        WHERE conversation_id = :conversationId
        ORDER BY start_index ASC, created_at ASC, id ASC
        """
    )
    suspend fun getBatchesOfConversation(conversationId: String): List<IndexPendingLedgerBatchEntity>

    @Query(
        """
        SELECT * FROM pending_ledger_batch
        WHERE conversation_id = :conversationId
            AND status IN ('pending', 'failed', 'running')
        ORDER BY start_index ASC, created_at ASC, id ASC
        """
    )
    suspend fun getProcessableBatchesOfConversation(conversationId: String): List<IndexPendingLedgerBatchEntity>

    @Query("SELECT * FROM pending_ledger_batch WHERE conversation_id = :conversationId AND event_id = :eventId LIMIT 1")
    suspend fun getBatchByConversationAndEvent(conversationId: String, eventId: Long): IndexPendingLedgerBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: IndexPendingLedgerBatchEntity): Long

    @Update
    suspend fun update(batch: IndexPendingLedgerBatchEntity)

    @Query("DELETE FROM pending_ledger_batch WHERE conversation_id = :conversationId AND event_id = :eventId")
    suspend fun deleteByConversationAndEvent(conversationId: String, eventId: Long)

    @Query("DELETE FROM pending_ledger_batch WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}
