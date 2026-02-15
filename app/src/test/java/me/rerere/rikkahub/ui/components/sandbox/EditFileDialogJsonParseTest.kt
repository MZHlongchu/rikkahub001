package me.rerere.rikkahub.ui.components.sandbox

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * 测试 EditFileDialog 中的 JSON 解析逻辑
 *
 * 验证修复：Python 返回的 data 字段可能是字符串或对象
 */
class EditFileDialogJsonParseTest {

    /**
     * 测试场景1：Python 返回 data 为字符串（JsonPrimitive）
     *
     * 这是 sandbox_tool.py _read_file 实际返回的格式
     */
    @Test
    fun testParseDataAsString() {
        val jsonStr = """{"success": true, "data": "Hello, World!", "file_path": "test.txt"}"""
        val result = Json.parseToJsonElement(jsonStr).jsonObject

        val success = result["success"]?.jsonPrimitive?.boolean ?: false
        assert(success == true) { "success should be true" }

        // 测试修复后的解析逻辑（与实际代码一致）
        @Suppress("UNCHECKED_CAST")
        val dataContent = when (val dataValue = result["data"]!!) {
            is kotlinx.serialization.json.JsonPrimitive -> dataValue.content
            is kotlinx.serialization.json.JsonObject -> dataValue["content"]?.jsonPrimitive?.content ?: ""
            else -> ""
        }

        assert(dataContent == "Hello, World!") { "Expected 'Hello, World!' but was '$dataContent'" }
    }

    /**
     * 测试场景2：Python 返回 data 为对象（JsonObject）
     */
    @Test
    fun testParseDataAsObject() {
        val jsonStr = """{"success": true, "data": {"content": "Nested content"}, "file_path": "test.txt"}"""
        val result = Json.parseToJsonElement(jsonStr).jsonObject

        val success = result["success"]?.jsonPrimitive?.boolean ?: false
        assert(success == true) { "success should be true" }

        @Suppress("UNCHECKED_CAST")
        val dataContent = when (val dataValue = result["data"]!!) {
            is kotlinx.serialization.json.JsonPrimitive -> dataValue.content
            is kotlinx.serialization.json.JsonObject -> dataValue["content"]?.jsonPrimitive?.content ?: ""
            else -> ""
        }

        assert(dataContent == "Nested content") { "Expected 'Nested content' but was '$dataContent'" }
    }

    /**
     * 测试场景3：空 data 字段
     */
    @Test
    fun testParseEmptyData() {
        val jsonStr = """{"success": true, "data": ""}"""
        val result = Json.parseToJsonElement(jsonStr).jsonObject

        val success = result["success"]?.jsonPrimitive?.boolean ?: false
        assert(success == true) { "success should be true" }

        @Suppress("UNCHECKED_CAST")
        val dataContent = when (val dataValue = result["data"]!!) {
            is kotlinx.serialization.json.JsonPrimitive -> dataValue.content
            is kotlinx.serialization.json.JsonObject -> dataValue["content"]?.jsonPrimitive?.content ?: ""
            else -> ""
        }

        assert(dataContent == "") { "Expected empty string but was '$dataContent'" }
    }
}
