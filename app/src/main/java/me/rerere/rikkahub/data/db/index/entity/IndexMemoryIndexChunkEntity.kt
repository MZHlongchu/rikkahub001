package me.rerere.rikkahub.data.db.index.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_index_chunk",
    indices = [
        Index(value = ["assistant_id"]),
        Index(value = ["conversation_id"]),
        Index(value = ["assistant_id", "conversation_id"]),
        Index(value = ["embedding_dimension"]),
    ]
)
data class IndexMemoryIndexChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("section_key")
    val sectionKey: String,
    @ColumnInfo("chunk_order")
    val chunkOrder: Int,
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("token_estimate")
    val tokenEstimate: Int,
    @ColumnInfo("embedding_dimension")
    val embeddingDimension: Int,
    @ColumnInfo("metadata_json", defaultValue = "{}")
    val metadataJson: String = "{}",
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
