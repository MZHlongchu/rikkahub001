package me.rerere.rikkahub.data.db.index

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.index.objectbox.IndexObjectBoxVectorStore

class VectorSearchExecutionException(
    val operation: String,
    val tableName: String,
    val dimension: Int,
    val candidateCount: Int,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(
    "$message [operation=$operation, table=$tableName, dimension=$dimension, candidates=$candidateCount]",
    cause
)

class VectorBackendVerifier(
    private val vectorStore: IndexObjectBoxVectorStore,
) {
    companion object {
        private const val TAG = "VectorBackendVerifier"
    }

    private val verificationMutex = Mutex()

    @Volatile
    private var lastKnownHealthy = false

    @Volatile
    private var lastFailureMessage = "Vector backend has not been verified yet"

    suspend fun verifyBackendHealth(force: Boolean = false) = withContext(Dispatchers.IO) {
        verificationMutex.withLock {
            if (!force && lastKnownHealthy) return@withLock
            runCatching {
                vectorStore.ensureReady()
                vectorStore.runProbe()
                lastKnownHealthy = true
                lastFailureMessage = ""
                Log.i(TAG, "ObjectBox vector backend health check passed for ${vectorStore.databasePath}")
            }.getOrElse { error ->
                lastKnownHealthy = false
                lastFailureMessage = error.message.orEmpty().ifBlank { "Unknown vector backend health check failure" }
                Log.e(TAG, "ObjectBox vector backend health check failed for ${vectorStore.databasePath}", error)
                throw error
            }
        }
    }

    suspend fun assertHealthy(
        operation: String,
        tableName: String,
        dimension: Int,
        candidateCount: Int,
    ) {
        try {
            verifyBackendHealth(force = false)
        } catch (error: Throwable) {
            throw VectorSearchExecutionException(
                operation = operation,
                tableName = tableName,
                dimension = dimension,
                candidateCount = candidateCount,
                message = "vector backend health check failed: ${error.message.orEmpty()}",
                cause = error
            )
        }
        if (!lastKnownHealthy) {
            throw VectorSearchExecutionException(
                operation = operation,
                tableName = tableName,
                dimension = dimension,
                candidateCount = candidateCount,
                message = "vector backend is unhealthy: $lastFailureMessage"
            )
        }
    }
}
