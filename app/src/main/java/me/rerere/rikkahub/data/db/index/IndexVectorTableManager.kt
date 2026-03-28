package me.rerere.rikkahub.data.db.index

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.index.dao.IndexMigrationStateDAO
import me.rerere.rikkahub.data.db.index.objectbox.IndexObjectBoxVectorStore
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "IndexVectorTableMgr"
private const val VECTOR_MIGRATION_BATCH_SIZE = 128

class IndexVectorTableManager(
    private val indexDatabase: IndexDatabase,
    private val migrationStateDAO: IndexMigrationStateDAO,
    private val vectorStore: IndexObjectBoxVectorStore,
    private val vectorBackendVerifier: VectorBackendVerifier,
) {
    private val migrationMutex = Mutex()

    suspend fun ensureReady() {
        vectorStore.ensureReady()
        migrateLegacyBackendIfNeeded()
    }

    suspend fun insertKnowledgeBaseVectors(
        dimension: Int,
        records: List<VectorInsertRecord>,
    ) {
        ensureReady()
        vectorStore.insertKnowledgeBaseVectors(dimension, records)
    }

    suspend fun insertMemoryVectors(
        dimension: Int,
        records: List<VectorInsertRecord>,
    ) {
        ensureReady()
        vectorStore.insertMemoryVectors(dimension, records)
    }

    suspend fun searchKnowledgeBaseDistances(
        candidateIds: List<Long>,
        queryEmbeddingJson: String,
        dimension: Int,
        limit: Int,
    ): Map<Long, Double> {
        ensureReady()
        return runCatching {
            vectorBackendVerifier.assertHealthy(
                operation = "knowledge_base",
                tableName = "objectbox_knowledge_base_vector",
                dimension = dimension,
                candidateCount = candidateIds.size,
            )
            vectorStore.searchKnowledgeBaseDistances(
                candidateIds = candidateIds,
                queryEmbeddingJson = queryEmbeddingJson,
                dimension = dimension,
                limit = limit,
            )
        }.getOrElse { error ->
            throw VectorSearchExecutionException(
                operation = "knowledge_base",
                tableName = "objectbox_knowledge_base_vector",
                dimension = dimension,
                candidateCount = candidateIds.size,
                message = "vector search failed: ${error.message.orEmpty()}",
                cause = error,
            )
        }
    }

    suspend fun searchMemoryDistances(
        candidateIds: List<Long>,
        queryEmbeddingJson: String,
        dimension: Int,
        limit: Int,
    ): Map<Long, Double> {
        ensureReady()
        return runCatching {
            vectorBackendVerifier.assertHealthy(
                operation = "memory",
                tableName = "objectbox_memory_vector",
                dimension = dimension,
                candidateCount = candidateIds.size,
            )
            vectorStore.searchMemoryDistances(
                candidateIds = candidateIds,
                queryEmbeddingJson = queryEmbeddingJson,
                dimension = dimension,
                limit = limit,
            )
        }.getOrElse { error ->
            throw VectorSearchExecutionException(
                operation = "memory",
                tableName = "objectbox_memory_vector",
                dimension = dimension,
                candidateCount = candidateIds.size,
                message = "vector search failed: ${error.message.orEmpty()}",
                cause = error,
            )
        }
    }

    suspend fun clearAllVectorTables() {
        ensureReady()
        vectorStore.clearAllVectors()
        clearLegacyVectorTables(indexDatabase.openHelper.writableDatabase)
    }

    suspend fun deleteMemoryVectors(chunkIdsByDimension: Map<Int, List<Long>>) {
        ensureReady()
        vectorStore.deleteMemoryVectors(chunkIdsByDimension)
    }

    suspend fun deleteKnowledgeBaseVectors(chunkIdsByDimension: Map<Int, List<Long>>) {
        ensureReady()
        vectorStore.deleteKnowledgeBaseVectors(chunkIdsByDimension)
    }

    private suspend fun migrateLegacyBackendIfNeeded() = withContext(Dispatchers.IO) {
        migrationMutex.withLock {
            val state = migrationStateDAO.getState() ?: return@withLock
            if (!state.cutoverComplete || state.backendVersion == INDEX_BACKEND_VERSION) return@withLock

            val db = indexDatabase.openHelper.writableDatabase
            runCatching {
                vectorStore.clearAllVectors()
                migrateLegacyTableSet(
                    db = db,
                    prefix = KB_VECTOR_TABLE_PREFIX,
                    importer = vectorStore::putKnowledgeBaseVectors,
                )
                migrateLegacyTableSet(
                    db = db,
                    prefix = MEMORY_VECTOR_TABLE_PREFIX,
                    importer = vectorStore::putMemoryVectors,
                )
                clearLegacyVectorTables(db)
                migrationStateDAO.upsert(
                    state.copy(
                        backendVersion = INDEX_BACKEND_VERSION,
                        lastMigratedAt = System.currentTimeMillis(),
                        lastError = "",
                    )
                )
                Log.i(TAG, "Migrated legacy sqlite-vector payloads into ObjectBox")
            }.onFailure { error ->
                migrationStateDAO.upsert(
                    state.copy(
                        lastError = error.message.orEmpty().take(500),
                    )
                )
                throw error
            }
        }
    }

    private suspend fun migrateLegacyTableSet(
        db: SupportSQLiteDatabase,
        prefix: String,
        importer: suspend (Int, List<Pair<Long, FloatArray>>) -> Unit,
    ) {
        val tableNames = db.query(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table' AND name LIKE ?
            ORDER BY name
            """.trimIndent(),
            arrayOf("${prefix}%")
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }
        tableNames.forEach { tableName ->
            val dimension = tableName.removePrefix(prefix).toIntOrNull()
                ?: error("Unable to parse vector dimension from table $tableName")
            db.query("SELECT chunk_id, embedding FROM `$tableName` ORDER BY chunk_id").use { cursor ->
                val batch = mutableListOf<Pair<Long, FloatArray>>()
                while (cursor.moveToNext()) {
                    batch += cursor.getLong(0) to vectorStore.decodeLegacyEmbedding(
                        blob = cursor.getBlob(1),
                        dimension = dimension,
                    )
                    if (batch.size >= VECTOR_MIGRATION_BATCH_SIZE) {
                        importer(dimension, batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) {
                    importer(dimension, batch.toList())
                }
            }
        }
    }

    private fun clearLegacyVectorTables(db: SupportSQLiteDatabase) {
        val tableNames = db.query(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
                AND (name LIKE '${KB_VECTOR_TABLE_PREFIX}%' OR name LIKE '${MEMORY_VECTOR_TABLE_PREFIX}%')
            """.trimIndent()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }
        tableNames.forEach { tableName ->
            db.execSQL("DROP TABLE IF EXISTS `$tableName`")
        }
    }
}
