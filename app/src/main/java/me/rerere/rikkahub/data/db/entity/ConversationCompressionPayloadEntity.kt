package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_compression_payload",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversation_id")]
)
data class ConversationCompressionPayloadEntity(
    @PrimaryKey
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("dialogue_summary_text", defaultValue = "")
    val dialogueSummaryText: String = "",
    @ColumnInfo("rolling_summary_json", defaultValue = "")
    val rollingSummaryJson: String = "",
)
