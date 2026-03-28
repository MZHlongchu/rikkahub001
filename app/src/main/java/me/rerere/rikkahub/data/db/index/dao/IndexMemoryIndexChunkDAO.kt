package me.rerere.rikkahub.data.db.index.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.index.entity.IndexMemoryIndexChunkEntity

@Dao
interface IndexMemoryIndexChunkDAO {
    @Query("SELECT * FROM memory_index_chunk WHERE assistant_id = :assistantId ORDER BY updated_at DESC, chunk_order ASC")
    suspend fun getChunksOfAssistant(assistantId: String): List<IndexMemoryIndexChunkEntity>

    @Query(
        """
        SELECT id, embedding_dimension AS embeddingDimension
        FROM memory_index_chunk
        WHERE conversation_id = :conversationId
        """
    )
    suspend fun getVectorRowsOfConversation(conversationId: String): List<IndexVectorRow>

    @Query("DELETE FROM memory_index_chunk WHERE conversation_id = :conversationId")
    suspend fun deleteChunksOfConversation(conversationId: String)

    @Query("DELETE FROM memory_index_chunk WHERE id IN (:chunkIds)")
    suspend fun deleteByIds(chunkIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<IndexMemoryIndexChunkEntity>): List<Long>
}
