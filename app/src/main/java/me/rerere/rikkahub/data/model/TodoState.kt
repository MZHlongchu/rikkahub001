package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

/**
 * Todo 状态
 * 独立于 WorkflowState，可单独使用
 */
@Serializable
data class TodoState(
    val todos: List<TodoItem> = emptyList(),
    val isEnabled: Boolean = false
)
