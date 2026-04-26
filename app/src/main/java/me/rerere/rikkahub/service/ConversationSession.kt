package me.rerere.rikkahub.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

private const val TAG = "ConversationSession"
private const val IDLE_TIMEOUT_MS = 5_000L

data class StreamingTailState(
    val node: MessageNode,
    val index: Int,
    val source: String,
    val version: Long,
)

class ConversationSession(
    val id: Uuid,
    initial: Conversation,
    private val scope: CoroutineScope,
    private val onIdle: (Uuid) -> Unit,
) {
    private var currentConversation: Conversation = initial

    // 会话状态
    val state = MutableStateFlow(initial)
    private val _messageNodesState = MutableStateFlow(initial.messageNodes)
    val messageNodesState: StateFlow<List<MessageNode>> = _messageNodesState.asStateFlow()
    private val _streamingTailState = MutableStateFlow<StreamingTailState?>(null)
    val streamingTailState: StateFlow<StreamingTailState?> = _streamingTailState.asStateFlow()
    private val _streamingUiTickState = MutableStateFlow(0L)
    val streamingUiTickState: StateFlow<Long> = _streamingUiTickState.asStateFlow()

    // 原子引用计数
    private val refCount = AtomicInteger(0)

    // 生成任务（内聚在 session 中）
    private val _generationJob = MutableStateFlow<Job?>(null)
    val generationJob: StateFlow<Job?> = _generationJob.asStateFlow()
    val isGenerating: Boolean get() = _generationJob.value?.isActive == true
    val isInUse: Boolean get() = refCount.get() > 0 || isGenerating

    // 空闲检查任务
    private var idleCheckJob: Job? = null

    fun acquire(): Int = refCount.incrementAndGet().also {
        cancelIdleCheck()
        Log.d(TAG, "acquire $id (refs=$it)")
    }

    fun release(): Int = refCount.decrementAndGet().also {
        Log.d(TAG, "release $id (refs=$it)")
        if (it <= 0) scheduleIdleCheck()
    }

    // 作用域 API - 短请求（REST）
    inline fun <T> withRef(block: () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    // 作用域 API - 长连接（SSE、挂起函数）
    suspend inline fun <T> withRefSuspend(block: () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    fun setJob(job: Job?) {
        _generationJob.value?.cancel()
        _generationJob.value = job
        job?.invokeOnCompletion {
            if (_generationJob.value === job) {
                _generationJob.value = null
            }
            if (refCount.get() <= 0) {
                scheduleIdleCheck()
            }
        }
    }

    fun getJob(): Job? = _generationJob.value

    fun getCurrentConversation(): Conversation = currentConversation

    fun replaceConversation(conversation: Conversation) {
        currentConversation = conversation
        _messageNodesState.value = conversation.messageNodes
        _streamingTailState.value = null
        state.value = conversation
    }

    fun updateConversation(update: (Conversation) -> Conversation) {
        replaceConversation(update(currentConversation))
    }

    fun applyStreamingConversation(conversation: Conversation, source: String): Long {
        currentConversation = conversation
        val nodes = conversation.messageNodes
        if (nodes.isEmpty()) {
            replaceConversation(conversation)
            return _streamingUiTickState.value
        }

        val committedNodes = nodes.dropLast(1)
        _messageNodesState.value = committedNodes

        val nextVersion = _streamingUiTickState.value + 1L
        _streamingTailState.value = StreamingTailState(
            node = nodes.last(),
            index = committedNodes.size,
            source = source,
            version = nextVersion,
        )
        _streamingUiTickState.value = nextVersion
        state.value = conversation
        return nextVersion
    }

    private fun scheduleIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (refCount.get() <= 0 && !isGenerating) {
                onIdle(id)
            }
        }
    }

    private fun cancelIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = null
    }

    fun cleanup() {
        _generationJob.value?.cancel()
        _generationJob.value = null
        idleCheckJob?.cancel()
        idleCheckJob = null
        _streamingTailState.value = null
    }
}
