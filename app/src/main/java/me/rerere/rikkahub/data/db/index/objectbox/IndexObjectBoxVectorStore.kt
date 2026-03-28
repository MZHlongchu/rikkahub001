package me.rerere.rikkahub.data.db.index.objectbox

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.index.VectorInsertRecord
import me.rerere.rikkahub.utils.JsonInstant
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class IndexObjectBoxVectorStore(
    context: Context,
) {
    companion object {
        private const val CANDIDATE_BATCH_SIZE = 512
    }

    private val operationMutex = Mutex()
    private val rawStore = RawIndexObjectBoxVectorStore(context)

    val databasePath: String
        get() = rawStore.databasePath

    suspend fun ensureReady() = withStore {
        rawStore.ensureReady()
    }

    suspend fun insertKnowledgeBaseVectors(
        dimension: Int,
        records: List<VectorInsertRecord>,
    ) = withStore {
        if (records.isEmpty()) return@withStore
        rawStore.putKnowledgeBaseVectors(records.map { record ->
            ObjectBoxVectorRecord(
                record.chunkId,
                dimension,
                parseEmbeddingJson(record.embeddingJson, dimension),
            )
        })
    }

    suspend fun insertMemoryVectors(
        dimension: Int,
        records: List<VectorInsertRecord>,
    ) = withStore {
        if (records.isEmpty()) return@withStore
        rawStore.putMemoryVectors(records.map { record ->
            ObjectBoxVectorRecord(
                record.chunkId,
                dimension,
                parseEmbeddingJson(record.embeddingJson, dimension),
            )
        })
    }

    suspend fun putKnowledgeBaseVectors(
        dimension: Int,
        vectors: List<Pair<Long, FloatArray>>,
    ) = withStore {
        if (vectors.isEmpty()) return@withStore
        rawStore.putKnowledgeBaseVectors(vectors.map { (chunkId, embedding) ->
            ObjectBoxVectorRecord(chunkId, dimension, embedding)
        })
    }

    suspend fun putMemoryVectors(
        dimension: Int,
        vectors: List<Pair<Long, FloatArray>>,
    ) = withStore {
        if (vectors.isEmpty()) return@withStore
        rawStore.putMemoryVectors(vectors.map { (chunkId, embedding) ->
            ObjectBoxVectorRecord(chunkId, dimension, embedding)
        })
    }

    suspend fun searchKnowledgeBaseDistances(
        candidateIds: List<Long>,
        queryEmbeddingJson: String,
        dimension: Int,
        limit: Int,
    ): Map<Long, Double> {
        return searchDistances(
            candidateIds = candidateIds,
            queryEmbedding = parseEmbeddingJson(queryEmbeddingJson, dimension),
            limit = limit,
        ) { batch, topK, queryVector ->
            rawStore.searchKnowledgeBaseDistances(batch, queryVector, dimension, topK)
        }
    }

    suspend fun searchMemoryDistances(
        candidateIds: List<Long>,
        queryEmbeddingJson: String,
        dimension: Int,
        limit: Int,
    ): Map<Long, Double> {
        return searchDistances(
            candidateIds = candidateIds,
            queryEmbedding = parseEmbeddingJson(queryEmbeddingJson, dimension),
            limit = limit,
        ) { batch, topK, queryVector ->
            rawStore.searchMemoryDistances(batch, queryVector, dimension, topK)
        }
    }

    suspend fun clearAllVectors() = withStore {
        rawStore.clearAllVectors()
    }

    suspend fun deleteKnowledgeBaseVectors(chunkIdsByDimension: Map<Int, List<Long>>) = withStore {
        chunkIdsByDimension.forEach { (dimension, chunkIds) ->
            chunkIds.distinct().chunked(CANDIDATE_BATCH_SIZE).forEach { batch ->
                rawStore.deleteKnowledgeBaseVectors(dimension, batch)
            }
        }
    }

    suspend fun deleteMemoryVectors(chunkIdsByDimension: Map<Int, List<Long>>) = withStore {
        chunkIdsByDimension.forEach { (dimension, chunkIds) ->
            chunkIds.distinct().chunked(CANDIDATE_BATCH_SIZE).forEach { batch ->
                rawStore.deleteMemoryVectors(dimension, batch)
            }
        }
    }

    suspend fun runProbe() = withStore {
        rawStore.runProbe()
    }

    suspend fun close() = withStore {
        rawStore.close()
    }

    fun decodeLegacyEmbedding(
        blob: ByteArray,
        dimension: Int,
    ): FloatArray {
        val floatCount = blob.size / Float.SIZE_BYTES
        check(floatCount >= dimension) {
            "Legacy vector blob too short for dimension=$dimension size=${blob.size}"
        }
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(dimension) { buffer.float }
    }

    private suspend fun searchDistances(
        candidateIds: List<Long>,
        queryEmbedding: FloatArray,
        limit: Int,
        search: (List<Long>, Int, FloatArray) -> Map<Long, Double>,
    ): Map<Long, Double> = withStore {
        if (candidateIds.isEmpty() || queryEmbedding.isEmpty() || limit <= 0) {
            return@withStore emptyMap()
        }
        val merged = linkedMapOf<Long, Double>()
        candidateIds.distinct().chunked(CANDIDATE_BATCH_SIZE).forEach { batch ->
            val batchScores = search(batch, min(limit, batch.size), queryEmbedding)
            batchScores.forEach { (chunkId, score) ->
                val current = merged[chunkId]
                if (current == null || score < current) {
                    merged[chunkId] = score
                }
            }
        }
        merged.entries
            .sortedBy { it.value }
            .take(limit)
            .associate { it.key to it.value }
    }

    private suspend fun <T> withStore(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            block()
        }
    }

    private fun parseEmbeddingJson(
        embeddingJson: String,
        dimension: Int,
    ): FloatArray {
        val embedding = JsonInstant.decodeFromString<List<Float>>(embeddingJson)
        check(embedding.size == dimension) {
            "Vector dimension mismatch: expected=$dimension actual=${embedding.size}"
        }
        return embedding.toFloatArray()
    }
}
