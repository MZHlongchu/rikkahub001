package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.ConversationCompressionPayloadEntity

@Dao
interface ConversationCompressionPayloadDAO {
    @Query("SELECT * FROM conversation_compression_payload WHERE conversation_id = :conversationId")
    suspend fun getPayloadOfConversation(conversationId: String): ConversationCompressionPayloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payload: ConversationCompressionPayloadEntity)

    @Query("DELETE FROM conversation_compression_payload WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}
