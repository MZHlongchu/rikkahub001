package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.CompressionEventPayloadEntity

@Dao
interface CompressionEventPayloadDAO {
    @Query("SELECT * FROM compression_event_payload WHERE event_id = :eventId")
    suspend fun getPayload(eventId: Long): CompressionEventPayloadEntity?

    @Query("SELECT * FROM compression_event_payload WHERE event_id IN (:eventIds)")
    suspend fun getPayloads(eventIds: List<Long>): List<CompressionEventPayloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payload: CompressionEventPayloadEntity)

    @Query("DELETE FROM compression_event_payload WHERE event_id = :eventId")
    suspend fun delete(eventId: Long)
}
