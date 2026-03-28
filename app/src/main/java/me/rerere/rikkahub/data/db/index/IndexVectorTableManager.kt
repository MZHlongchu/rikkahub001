package me.rerere.rikkahub.data.db.index

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase

data class VectorInsertRecord(
    val chunkId: Long,
    val embeddingJson: String,
)

class IndexVectorTableManager(
    private val database: IndexDatabase,
    private val vectorBackendVerifier: VectorBackendVerifier,
) {
    companion object {
        private const val TAG = "IndexVectorTableManager"
        private const val SQLITE_BIND_LIMIT_HEADROOM = 900
    }

    private val db: SupportSQLiteDatabase
        get() = database.openHelper.writableDatabase

    suspend fun ensureReady() {
        createKnowledgeBaseFtsTable(db)
        initializeExistingVectorTables(db)
    }

    suspend fun insertKnowledgeBaseVectors(
        dimension: Int,
        records: List<VectorInsertRecord>,
    ) {
        if (records.isEmpty() || dimension <= 0) return
        val databaseHandle = db
        val tableName = buildKnowledgeBaseVectorTableName(dimension)
        ensureVectorTable(databaseHandle, tableName, dimension, "knowledge_base_chunk")
        insertVectorRows(databaseHandle, tableName, records)
    }

    suspend fun insertMemoryVectors(
        dimension: Int,
        records: List<VectorInsertRecord>,
    ) {
        if (records.isEmpty() || dimension <= 0) return
        val databaseHandle = db
        val tableName = buildMemoryVectorTableName(dimension)
        ensureVectorTable(databaseHandle, tableName, dimension, "memory_index_chunk")
        insertVectorRows(databaseHandle, tableName, records)
    }

    suspend fun searchKnowledgeBaseDistances(
        candidateIds: List<Long>,
        queryEmbeddingJson: String,
        dimension: Int,
        limit: Int,
    ): Map<Long, Double> {
        if (candidateIds.isEmpty() || queryEmbeddingJson.isBlank() || dimension <= 0 || limit <= 0) {
            return emptyMap()
        }
        val databaseHandle = db
        val tableName = buildKnowledgeBaseVectorTableName(dimension)
        if (!hasTable(databaseHandle, tableName)) {
            throw VectorSearchExecutionException(
                operation = "knowledge_base",
                tableName = tableName,
                dimension = dimension,
                candidateCount = candidateIds.size,
                message = "Knowledge base vector table is missing"
            )
        }
        ensureVectorTable(databaseHandle, tableName, dimension, "knowledge_base_chunk")
        return searchDistancesByRowIds(
            databaseHandle = databaseHandle,
            tableName = tableName,
            candidateIds = candidateIds,
            queryEmbeddingJson = queryEmbeddingJson,
            limit = limit,
            operation = "knowledge_base"
        )
    }

    suspend fun searchMemoryDistances(
        candidateIds: List<Long>,
        queryEmbeddingJson: String,
        dimension: Int,
        limit: Int,
    ): Map<Long, Double> {
        if (candidateIds.isEmpty() || queryEmbeddingJson.isBlank() || dimension <= 0 || limit <= 0) {
            return emptyMap()
        }
        val databaseHandle = db
        val tableName = buildMemoryVectorTableName(dimension)
        if (!hasTable(databaseHandle, tableName)) {
            throw VectorSearchExecutionException(
                operation = "memory",
                tableName = tableName,
                dimension = dimension,
                candidateCount = candidateIds.size,
                message = "Memory vector table is missing"
            )
        }
        ensureVectorTable(databaseHandle, tableName, dimension, "memory_index_chunk")
        return searchDistancesByRowIds(
            databaseHandle = databaseHandle,
            tableName = tableName,
            candidateIds = candidateIds,
            queryEmbeddingJson = queryEmbeddingJson,
            limit = limit,
            operation = "memory"
        )
    }

    suspend fun clearAllVectorTables() {
        val databaseHandle = db
        val cursor = databaseHandle.query(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
                AND (name LIKE '${KB_VECTOR_TABLE_PREFIX}%' OR name LIKE '${MEMORY_VECTOR_TABLE_PREFIX}%')
            """.trimIndent()
        )
        val tableNames = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(it.getString(0))
                }
            }
        }
        tableNames.forEach { tableName ->
            databaseHandle.execSQL("DROP TABLE IF EXISTS `$tableName`")
        }
    }

    suspend fun deleteMemoryVectors(chunkIdsByDimension: Map<Int, List<Long>>) {
        val databaseHandle = db
        chunkIdsByDimension.forEach { (dimension, chunkIds) ->
            if (dimension <= 0 || chunkIds.isEmpty()) return@forEach
            val tableName = buildMemoryVectorTableName(dimension)
            if (!hasTable(databaseHandle, tableName)) return@forEach
            deleteVectorRows(databaseHandle, tableName, chunkIds.distinct())
        }
    }

    private fun hasTable(
        databaseHandle: SupportSQLiteDatabase,
        tableName: String,
    ): Boolean {
        databaseHandle.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName)
        ).use {
            return it.moveToFirst() && it.getInt(0) > 0
        }
    }

    private fun ensureVectorTable(
        databaseHandle: SupportSQLiteDatabase,
        tableName: String,
        dimension: Int,
        parentTable: String,
    ) {
        databaseHandle.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `$tableName` (
                `chunk_id` INTEGER PRIMARY KEY NOT NULL,
                `embedding` BLOB NOT NULL,
                FOREIGN KEY(`chunk_id`) REFERENCES `$parentTable`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        initializeVectorTable(databaseHandle, tableName, dimension)
    }

    private fun insertVectorRows(
        databaseHandle: SupportSQLiteDatabase,
        tableName: String,
        records: List<VectorInsertRecord>,
    ) {
        val statement = databaseHandle.compileStatement(
            "INSERT OR REPLACE INTO `$tableName` (`chunk_id`, `embedding`) VALUES (?, vector_as_f32(?))"
        )
        records.forEach { record ->
            statement.clearBindings()
            statement.bindLong(1, record.chunkId)
            statement.bindString(2, record.embeddingJson)
            statement.executeInsert()
        }
    }

    private fun queryDistanceMap(
        databaseHandle: SupportSQLiteDatabase,
        sql: String,
        args: List<Any>,
    ): Map<Long, Double> {
        val result = linkedMapOf<Long, Double>()
        databaseHandle.query(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = cursor.getDouble(1)
            }
        }
        return result
    }

    private suspend fun searchDistancesByRowIds(
        databaseHandle: SupportSQLiteDatabase,
        tableName: String,
        candidateIds: List<Long>,
        queryEmbeddingJson: String,
        limit: Int,
        operation: String,
    ): Map<Long, Double> {
        val uniqueIds = candidateIds.distinct()
        if (uniqueIds.isEmpty()) return emptyMap()
        vectorBackendVerifier.assertHealthy(
            operation = operation,
            tableName = tableName,
            dimension = extractDimension(tableName),
            candidateCount = uniqueIds.size,
        )

        return try {
            val totalVectorRows = countRows(databaseHandle, tableName)
            if (totalVectorRows == 0) {
                return emptyMap()
            }
            val candidateIdSet = uniqueIds.toHashSet()
            val result = queryDistanceMap(
                databaseHandle = databaseHandle,
                sql = buildTopKDistanceQuery(tableName),
                args = listOf(queryEmbeddingJson, totalVectorRows)
            )
                .asSequence()
                .filter { (rowId, _) -> candidateIdSet.contains(rowId) }
                .sortedBy { it.value }
                .take(limit)
                .associate { it.key to it.value }
            Log.i(
                TAG,
                "sqlite-vector $operation search succeeded for $tableName with ${result.size} hits from ${uniqueIds.size} candidates"
            )
            result
        } catch (error: Throwable) {
            Log.e(TAG, "sqlite-vector $operation distance query failed for $tableName", error)
            throw VectorSearchExecutionException(
                operation = operation,
                tableName = tableName,
                dimension = extractDimension(tableName),
                candidateCount = uniqueIds.size,
                message = error.message.orEmpty().ifBlank { "sqlite-vector search failed" },
                cause = error
            )
        }
    }

    private fun deleteVectorRows(
        databaseHandle: SupportSQLiteDatabase,
        tableName: String,
        chunkIds: List<Long>,
    ) {
        if (chunkIds.isEmpty()) return
        chunkIds.chunked(SQLITE_BIND_LIMIT_HEADROOM).forEach { batch ->
            databaseHandle.execSQL(
                "DELETE FROM `$tableName` WHERE chunk_id IN (${batch.joinToString(",") { "?" }})",
                batch.map { it as Any }.toTypedArray()
            )
        }
    }

    private fun buildTopKDistanceQuery(tableName: String): String {
        return """
            SELECT rowid, distance
            FROM vector_full_scan('$tableName', 'embedding', ?, ?)
        """.trimIndent()
    }

    private fun countRows(
        databaseHandle: SupportSQLiteDatabase,
        tableName: String,
    ): Int {
        databaseHandle.query("SELECT COUNT(*) FROM `$tableName`").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun extractDimension(tableName: String): Int {
        return when {
            tableName.startsWith(KB_VECTOR_TABLE_PREFIX) ->
                tableName.removePrefix(KB_VECTOR_TABLE_PREFIX).toIntOrNull()
            tableName.startsWith(MEMORY_VECTOR_TABLE_PREFIX) ->
                tableName.removePrefix(MEMORY_VECTOR_TABLE_PREFIX).toIntOrNull()
            else -> null
        } ?: 0
    }
}
