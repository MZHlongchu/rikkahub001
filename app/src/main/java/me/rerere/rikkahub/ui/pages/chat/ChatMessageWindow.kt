package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.CompressionEvent
import kotlin.uuid.Uuid

data class ConversationPreviewSearchResult(
    val nodeId: Uuid,
    val messageId: Uuid,
    val globalIndex: Int,
    val message: UIMessage,
    val snippet: String,
)

internal fun localizeCompressionEvents(
    events: List<CompressionEvent>,
    totalStableCount: Int,
    startIndex: Int = 0,
    nodeCount: Int = totalStableCount,
): List<CompressionEvent> {
    val loadedEndIndex = startIndex + nodeCount
    return events
        .map { event ->
            event.copy(boundaryIndex = event.boundaryIndex.coerceIn(0, totalStableCount))
        }
        .filter { event -> event.boundaryIndex in startIndex..loadedEndIndex }
        .map { event -> event.copy(boundaryIndex = event.boundaryIndex - startIndex) }
}

internal fun renderedListIndexForMessage(
    localMessageIndex: Int,
    localizedEvents: List<CompressionEvent>,
): Int {
    val boundaryCount = localizedEvents.count { it.boundaryIndex <= localMessageIndex }
    return localMessageIndex + boundaryCount
}

internal fun findCompressionListIndex(
    eventId: Long,
    localizedEvents: List<CompressionEvent>,
    messageCount: Int,
): Int? {
    var listIndex = 0
    for (boundary in 0..messageCount) {
        localizedEvents.filter { it.boundaryIndex == boundary }.forEach { event ->
            if (event.id == eventId) return listIndex
            listIndex++
        }
        if (boundary < messageCount) {
            listIndex++
        }
    }
    return null
}
