package me.rerere.rikkahub.data.db.index.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.index.entity.IndexSourcePreviewChunkEntity

@Dao
interface IndexSourcePreviewChunkDAO {
    @Query("SELECT * FROM source_preview_chunk WHERE assistant_id = :assistantId ORDER BY updated_at DESC, chunk_order ASC")
    suspend fun getChunksOfAssistant(assistantId: String): List<IndexSourcePreviewChunkEntity>

    @Query(
        """
        SELECT * FROM source_preview_chunk
        WHERE assistant_id = :assistantId
            AND conversation_id IN (:conversationIds)
        ORDER BY updated_at DESC, chunk_order ASC
        """
    )
    suspend fun getChunksOfConversations(
        assistantId: String,
        conversationIds: List<String>,
    ): List<IndexSourcePreviewChunkEntity>

    @Query("DELETE FROM source_preview_chunk WHERE conversation_id = :conversationId")
    suspend fun deleteChunksOfConversation(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<IndexSourcePreviewChunkEntity>)
}
