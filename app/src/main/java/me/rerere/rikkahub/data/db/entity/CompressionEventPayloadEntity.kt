package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "compression_event_payload",
    foreignKeys = [
        ForeignKey(
            entity = CompressionEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("event_id")]
)
data class CompressionEventPayloadEntity(
    @PrimaryKey
    @ColumnInfo("event_id")
    val eventId: Long,
    @ColumnInfo("dialogue_summary_text", defaultValue = "")
    val dialogueSummaryText: String = "",
    @ColumnInfo("ledger_snapshot", defaultValue = "")
    val ledgerSnapshot: String = "",
    @ColumnInfo("summary_snapshot", defaultValue = "")
    val summarySnapshot: String = "",
    @ColumnInfo("base_dialogue_summary_text", defaultValue = "")
    val baseDialogueSummaryText: String = "",
    @ColumnInfo("base_ledger_json", defaultValue = "")
    val baseLedgerJson: String = "",
    @ColumnInfo("base_summary_json", defaultValue = "")
    val baseSummaryJson: String = "",
)
