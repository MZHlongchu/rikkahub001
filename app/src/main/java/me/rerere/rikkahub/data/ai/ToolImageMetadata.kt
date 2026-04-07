package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val TOOL_IMAGE_SOURCE_KEY = "study_rik_image_source"
const val TOOL_IMAGE_SOURCE_MCP_TOOL = "mcp_tool_image"
const val TOOL_IMAGE_SOURCE_CONTAINER_TOOL = "container_tool_image"
const val TOOL_IMAGE_TOOL_NAME_KEY = "study_rik_tool_name"
const val TOOL_IMAGE_INDEX_KEY = "study_rik_tool_image_index"

fun buildToolImageMetadata(
    imageSource: String,
    toolName: String,
    imageIndex: Int,
    metadata: JsonObject? = null,
): JsonObject {
    return mergeMessagePartMetadata(
        metadata = metadata,
        entries = mapOf(
            TOOL_IMAGE_SOURCE_KEY to kotlinx.serialization.json.JsonPrimitive(imageSource),
            TOOL_IMAGE_TOOL_NAME_KEY to kotlinx.serialization.json.JsonPrimitive(toolName),
            TOOL_IMAGE_INDEX_KEY to kotlinx.serialization.json.JsonPrimitive(imageIndex),
        )
    )
}

fun mergeMessagePartMetadata(
    metadata: JsonObject?,
    entries: Map<String, JsonElement>,
): JsonObject {
    return buildJsonObject {
        metadata?.forEach { (key, value) -> put(key, value) }
        entries.forEach { (key, value) -> put(key, value) }
    }
}
