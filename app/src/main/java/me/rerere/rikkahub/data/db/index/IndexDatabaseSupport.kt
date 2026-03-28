package me.rerere.rikkahub.data.db.index

import androidx.sqlite.db.SupportSQLiteDatabase

internal const val INDEX_DB_NAME = "rikka_hub_index"
internal const val INDEX_SCHEMA_VERSION = 1
internal const val INDEX_BACKEND_VERSION = "objectbox_hnsw_v1"
internal const val INDEX_KB_FTS_TABLE = "knowledge_base_chunk_fts"
internal const val INDEX_MIGRATION_STATE_TABLE = "index_migration_state"
internal const val KB_VECTOR_TABLE_PREFIX = "knowledge_base_vector_d"
internal const val MEMORY_VECTOR_TABLE_PREFIX = "memory_index_vector_d"

internal fun buildKnowledgeBaseVectorTableName(dimension: Int): String {
    require(dimension > 0) { "Vector dimension must be positive" }
    return "${KB_VECTOR_TABLE_PREFIX}${dimension}"
}

internal fun buildMemoryVectorTableName(dimension: Int): String {
    require(dimension > 0) { "Vector dimension must be positive" }
    return "${MEMORY_VECTOR_TABLE_PREFIX}${dimension}"
}

internal fun createKnowledgeBaseFtsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS $INDEX_KB_FTS_TABLE USING fts5(
            content,
            assistant_id UNINDEXED,
            document_id UNINDEXED,
            chunk_id UNINDEXED,
            tokenize = 'simple'
        )
        """.trimIndent()
    )
}

internal fun initializeExistingVectorTables(db: SupportSQLiteDatabase) {
    val cursor = db.query(
        """
        SELECT name
        FROM sqlite_master
        WHERE type = 'table'
            AND (name LIKE '${KB_VECTOR_TABLE_PREFIX}%' OR name LIKE '${MEMORY_VECTOR_TABLE_PREFIX}%')
        """.trimIndent()
    )
    cursor.use {
        while (it.moveToNext()) {
            val tableName = it.getString(0) ?: continue
            when {
                tableName.startsWith(KB_VECTOR_TABLE_PREFIX) -> {
                    val dimension = tableName.removePrefix(KB_VECTOR_TABLE_PREFIX).toIntOrNull() ?: continue
                    initializeVectorTable(db, tableName, dimension)
                }

                tableName.startsWith(MEMORY_VECTOR_TABLE_PREFIX) -> {
                    val dimension = tableName.removePrefix(MEMORY_VECTOR_TABLE_PREFIX).toIntOrNull() ?: continue
                    initializeVectorTable(db, tableName, dimension)
                }
            }
        }
    }
}

internal fun initializeVectorTable(
    db: SupportSQLiteDatabase,
    tableName: String,
    dimension: Int,
) {
    db.query(
        "SELECT vector_init(?, ?, ?)",
        arrayOf(tableName, "embedding", "dimension=$dimension,type=FLOAT32,distance=COSINE")
    ).use { }
}
