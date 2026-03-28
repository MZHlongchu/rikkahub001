package me.rerere.rikkahub.data.db.index

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IndexVectorStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "index-vector-store-test-${UUID.randomUUID()}"
    private val vectorStore = IndexVectorStore(context, databaseName)

    @After
    fun tearDown() = runBlocking {
        vectorStore.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun probeAndTableScopedSearchesWork() = runBlocking {
        vectorStore.withPinnedConnection("test_schema_setup") { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_base_chunk (id INTEGER PRIMARY KEY NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS memory_index_chunk (id INTEGER PRIMARY KEY NOT NULL)")
        }

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
}
