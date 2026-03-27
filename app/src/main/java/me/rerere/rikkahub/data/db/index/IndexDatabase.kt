package me.rerere.rikkahub.data.db.index

import androidx.room.Database
import androidx.room.RoomDatabase
import me.rerere.rikkahub.data.db.index.dao.IndexKnowledgeBaseChunkDAO
import me.rerere.rikkahub.data.db.index.dao.IndexMemoryIndexChunkDAO
import me.rerere.rikkahub.data.db.index.dao.IndexMigrationStateDAO
import me.rerere.rikkahub.data.db.index.dao.IndexPendingLedgerBatchDAO
import me.rerere.rikkahub.data.db.index.dao.IndexSourcePreviewChunkDAO
import me.rerere.rikkahub.data.db.index.entity.IndexKnowledgeBaseChunkEntity
import me.rerere.rikkahub.data.db.index.entity.IndexMemoryIndexChunkEntity
import me.rerere.rikkahub.data.db.index.entity.IndexMigrationStateEntity
import me.rerere.rikkahub.data.db.index.entity.IndexPendingLedgerBatchEntity
import me.rerere.rikkahub.data.db.index.entity.IndexSourcePreviewChunkEntity

@Database(
    entities = [
        IndexKnowledgeBaseChunkEntity::class,
        IndexMemoryIndexChunkEntity::class,
        IndexSourcePreviewChunkEntity::class,
        IndexPendingLedgerBatchEntity::class,
        IndexMigrationStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class IndexDatabase : RoomDatabase() {
    abstract fun knowledgeBaseChunkDao(): IndexKnowledgeBaseChunkDAO

    abstract fun memoryIndexChunkDao(): IndexMemoryIndexChunkDAO

    abstract fun sourcePreviewChunkDao(): IndexSourcePreviewChunkDAO

    abstract fun pendingLedgerBatchDao(): IndexPendingLedgerBatchDAO

    abstract fun migrationStateDao(): IndexMigrationStateDAO
}
