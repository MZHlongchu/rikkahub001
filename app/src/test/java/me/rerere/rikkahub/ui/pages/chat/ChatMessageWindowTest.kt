package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.model.CompressionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ChatTimelineHelpersTest {

    @Test
    fun `localizeCompressionEvents keeps only events inside the loaded window`() {
        val createdAt = Instant.parse("2026-04-13T04:00:00Z")
        val events = listOf(
            CompressionEvent(id = 1L, boundaryIndex = 10, createdAt = createdAt),
            CompressionEvent(id = 2L, boundaryIndex = 45, createdAt = createdAt),
            CompressionEvent(id = 3L, boundaryIndex = 70, createdAt = createdAt),
        )

        val localized = localizeCompressionEvents(
            events = events,
            totalStableCount = 100,
            startIndex = 40,
            nodeCount = 20,
        )

        assertEquals(listOf(2L), localized.map { it.id })
        assertEquals(5, localized.single().boundaryIndex)
    }

    @Test
    fun `renderedListIndexForMessage accounts for inserted compression cards`() {
        val localizedEvents = listOf(
            CompressionEvent(id = 1L, boundaryIndex = 0, createdAt = Instant.EPOCH),
            CompressionEvent(id = 2L, boundaryIndex = 2, createdAt = Instant.EPOCH),
            CompressionEvent(id = 3L, boundaryIndex = 2, createdAt = Instant.EPOCH),
        )

        assertEquals(1, renderedListIndexForMessage(localMessageIndex = 0, localizedEvents = localizedEvents))
        assertEquals(2, renderedListIndexForMessage(localMessageIndex = 1, localizedEvents = localizedEvents))
        assertEquals(5, renderedListIndexForMessage(localMessageIndex = 2, localizedEvents = localizedEvents))
    }

    @Test
    fun `findCompressionListIndex returns rendered compression positions`() {
        val localizedEvents = listOf(
            CompressionEvent(id = 10L, boundaryIndex = 0, createdAt = Instant.EPOCH),
            CompressionEvent(id = 20L, boundaryIndex = 2, createdAt = Instant.EPOCH),
        )

        assertEquals(0, findCompressionListIndex(eventId = 10L, localizedEvents = localizedEvents, messageCount = 3))
        assertEquals(3, findCompressionListIndex(eventId = 20L, localizedEvents = localizedEvents, messageCount = 3))
        assertNull(findCompressionListIndex(eventId = 99L, localizedEvents = localizedEvents, messageCount = 3))
    }
}
