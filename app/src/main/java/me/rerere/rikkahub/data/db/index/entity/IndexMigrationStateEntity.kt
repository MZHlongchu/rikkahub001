package me.rerere.rikkahub.data.db.index.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.rerere.rikkahub.data.db.index.INDEX_BACKEND_VERSION
import me.rerere.rikkahub.data.db.index.INDEX_MIGRATION_STATE_TABLE
import me.rerere.rikkahub.data.db.index.INDEX_SCHEMA_VERSION

@Entity(tableName = INDEX_MIGRATION_STATE_TABLE)
data class IndexMigrationStateEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo("schema_version")
    val schemaVersion: Int = INDEX_SCHEMA_VERSION,
    @ColumnInfo("backend_version")
    val backendVersion: String = INDEX_BACKEND_VERSION,
    @ColumnInfo("cutover_complete")
    val cutoverComplete: Boolean = false,
    @ColumnInfo("legacy_pruned")
    val legacyPruned: Boolean = false,
    @ColumnInfo("last_migrated_at")
    val lastMigratedAt: Long = 0L,
    @ColumnInfo("last_error")
    val lastError: String = "",
)
