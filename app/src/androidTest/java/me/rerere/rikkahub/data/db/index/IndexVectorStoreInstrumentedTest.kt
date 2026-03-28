package me.rerere.rikkahub.data.db.index

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.index.objectbox.IndexObjectBoxVectorStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndexVectorStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var stores = mutableListOf<IndexObjectBoxVectorStore>()

    private fun newStore(): IndexObjectBoxVectorStore {
        return IndexObjectBoxVectorStore(context).also { stores += it }
    }

    @After
    fun tearDown() = runBlocking {
        stores.forEach {
            it.clearAllVectors()
            it.close()
        }
    }

    @Test
    fun probeAndTableScopedSearchesWork() = runBlocking {
        val vectorStore = newStore()
        val verifier = VectorBackendVerifier(vectorStore)
        verifier.verifyBackendHealth(force = true)

        vectorStore.insertKnowledgeBaseVectors(
            dimension = 2,
            records = listOf(
                VectorInsertRecord(chunkId = 1L, embeddingJson = "[1.0,0.0]"),
                VectorInsertRecord(chunkId = 2L, embeddingJson = "[0.0,1.0]"),
            )
        )
        vectorStore.insertMemoryVectors(
            dimension = 2,
            records = listOf(
                VectorInsertRecord(chunkId = 11L, embeddingJson = "[1.0,0.0]"),
                VectorInsertRecord(chunkId = 12L, embeddingJson = "[0.0,1.0]"),
            )
        )

        val knowledgeBaseDistances = vectorStore.searchKnowledgeBaseDistances(
            candidateIds = listOf(1L, 2L),
            queryEmbeddingJson = "[1.0,0.0]",
            dimension = 2,
            limit = 2,
        )
        val memoryDistances = vectorStore.searchMemoryDistances(
            candidateIds = listOf(11L, 12L),
            queryEmbeddingJson = "[1.0,0.0]",
            dimension = 2,
            limit = 2,
        )

        assertEquals(listOf(1L, 2L), knowledgeBaseDistances.keys.toList())
        assertEquals(listOf(11L, 12L), memoryDistances.keys.toList())
        assertTrue((knowledgeBaseDistances[1L] ?: Double.MAX_VALUE) <= (knowledgeBaseDistances[2L] ?: Double.MIN_VALUE))
        assertTrue((memoryDistances[11L] ?: Double.MAX_VALUE) <= (memoryDistances[12L] ?: Double.MIN_VALUE))
    }

    @Test
    fun reopeningStoreKeepsProbeAndSearchUsable() = runBlocking {
        val firstStore = newStore()
        firstStore.insertMemoryVectors(
            dimension = 2,
            records = listOf(
                VectorInsertRecord(chunkId = 21L, embeddingJson = "[1.0,0.0]"),
                VectorInsertRecord(chunkId = 22L, embeddingJson = "[0.0,1.0]"),
            )
        )
        firstStore.close()

        val reopenedStore = newStore()
        val verifier = VectorBackendVerifier(reopenedStore)
        verifier.verifyBackendHealth(force = true)
        val distances = reopenedStore.searchMemoryDistances(
            candidateIds = listOf(21L, 22L),
            queryEmbeddingJson = "[1.0,0.0]",
            dimension = 2,
            limit = 2,
        )

        assertEquals(listOf(21L, 22L), distances.keys.toList())
    }

    @Test
    fun repeatedEnsureReadyDoesNotThrowPragmaQueryError() = runBlocking {
        val vectorStore = newStore()
        repeat(3) {
            vectorStore.close()
            val reopenedStore = newStore()
            val failure = runCatching {
                reopenedStore.ensureReady()
            }.exceptionOrNull()
            assertFalse(
                "ensureReady should not fail after reopen: ${failure?.message}",
                failure != null
            )
        }
    }

    @Test
    fun concurrentReadersShareSingleStoreWithoutLocking() = runBlocking {
        val vectorStore = newStore()
        vectorStore.insertKnowledgeBaseVectors(
            dimension = 2,
            records = listOf(
                VectorInsertRecord(chunkId = 31L, embeddingJson = "[1.0,0.0]"),
                VectorInsertRecord(chunkId = 32L, embeddingJson = "[0.0,1.0]"),
            )
        )

        val results = coroutineScope {
            List(4) {
                async {
                    vectorStore.searchKnowledgeBaseDistances(
                        candidateIds = listOf(31L, 32L),
                        queryEmbeddingJson = "[1.0,0.0]",
                        dimension = 2,
                        limit = 2,
                    )
                }
            }.awaitAll()
        }

        results.forEach { distances ->
            assertEquals(listOf(31L, 32L), distances.keys.toList())
        }
    }

    @Test
    fun largeDimensionMemorySearchWorks() = runBlocking {
        val vectorStore = newStore()
        val firstVector = List(1024) { index -> if (index == 0) 1.0f else 0.0f }
        val secondVector = List(1024) { index -> if (index == 1) 1.0f else 0.0f }
        val queryVector = firstVector.joinToString(prefix = "[", postfix = "]")
        vectorStore.insertMemoryVectors(
            dimension = 1024,
            records = listOf(
                VectorInsertRecord(chunkId = 41L, embeddingJson = firstVector.joinToString(prefix = "[", postfix = "]")),
                VectorInsertRecord(chunkId = 42L, embeddingJson = secondVector.joinToString(prefix = "[", postfix = "]")),
            )
        )

        val distances = vectorStore.searchMemoryDistances(
            candidateIds = listOf(41L, 42L),
            queryEmbeddingJson = queryVector,
            dimension = 1024,
            limit = 2,
        )

        assertEquals(listOf(41L, 42L), distances.keys.toList())
    }
}
