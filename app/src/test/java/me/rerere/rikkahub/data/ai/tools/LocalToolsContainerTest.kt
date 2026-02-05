package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.Tool
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import kotlin.uuid.Uuid

/**
 * LocalTools 容器相关工具单元测试
 *
 * 测试内容：
 * 1. container_python 工具创建
 * 2. container_shell 工具创建
 * 3. container_install 工具创建
 * 4. 工具参数验证
 */
@RunWith(MockitoJUnitRunner::class)
class LocalToolsContainerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var mockProotManager: me.rerere.rikkahub.data.container.PRootManager

    @Mock
    private lateinit var mockPackageManager: me.rerere.rikkahub.data.container.PackageManager

    private lateinit var localTools: LocalTools
    private val testSandboxId = Uuid.random()

    @Before
    fun setup() {
        // 默认容器未运行
        `when`(mockProotManager.isRunning).thenReturn(false)
        `when`(mockProotManager.containerState).thenReturn(
            kotlinx.coroutines.flow.MutableStateFlow(
                me.rerere.rikkahub.data.container.ContainerStateEnum.NotInitialized
            )
        )

        localTools = LocalTools(
            context = context,
            prootManager = mockProotManager,
            packageManager = mockPackageManager,
            subAgentExecutor = null
        )
    }

    /**
     * 测试：容器未运行时，不应暴露容器工具
     */
    @Test
    fun `should NOT expose container tools when container not running`() {
        val tools = localTools.getTools(
            options = listOf(LocalToolOption.ChaquoPy),
            sandboxId = testSandboxId
        )

        // 不应包含容器工具
        val hasContainerPython = tools.any { it.name == "container_python" }
        val hasContainerShell = tools.any { it.name == "container_shell" }
        val hasContainerInstall = tools.any { it.name == "container_install" }

        assertFalse("容器未运行时不应暴露 container_python", hasContainerPython)
        assertFalse("容器未运行时不应暴露 container_shell", hasContainerShell)
        assertFalse("容器未运行时不应暴露 container_install", hasContainerInstall)
    }

    /**
     * 测试：容器运行时，应暴露容器工具
     */
    @Test
    fun `should expose container tools when container is running`() {
        // 模拟容器运行中
        `when`(mockProotManager.isRunning).thenReturn(true)

        val tools = localTools.getTools(
            options = listOf(LocalToolOption.ChaquoPy),
            sandboxId = testSandboxId
        )

        // 应包含容器工具
        val hasContainerPython = tools.any { it.name == "container_python" }
        val hasContainerShell = tools.any { it.name == "container_shell" }
        val hasContainerInstall = tools.any { it.name == "container_install" }

        assertTrue("容器运行时应暴露 container_python", hasContainerPython)
        assertTrue("容器运行时应暴露 container_shell", hasContainerShell)
        assertTrue("容器运行时应暴露 container_install", hasContainerInstall)
    }

    /**
     * 测试：container_python 工具参数
     */
    @Test
    fun `container_python tool should have correct parameters`() {
        `when`(mockProotManager.isRunning).thenReturn(true)

        val tool = localTools.createContainerPythonTool(testSandboxId)

        assertEquals("container_python", tool.name)
        assertTrue("描述应提及 PRoot", tool.description.contains("PRoot"))
        assertTrue("描述应提及 pip", tool.description.contains("pip"))
    }

    /**
     * 测试：container_shell 工具参数
     */
    @Test
    fun `container_shell tool should have correct parameters`() {
        `when`(mockProotManager.isRunning).thenReturn(true)

        val tool = localTools.createContainerShellTool(testSandboxId)

        assertEquals("container_shell", tool.name)
        assertTrue("描述应提及 GNU bash", tool.description.contains("GNU bash"))
        assertTrue("描述应提及 apk", tool.description.contains("apk"))
    }

    /**
     * 测试：container_install 工具参数
     */
    @Test
    fun `container_install tool should have correct parameters`() {
        val tool = localTools.createContainerInstallTool(testSandboxId, mockPackageManager)

        assertEquals("container_install", tool.name)
        assertTrue("描述应提及 Node.js", tool.description.contains("Node.js"))
        assertTrue("描述应提及 Go", tool.description.contains("Go"))
        assertTrue("描述应提及 Java", tool.description.contains("Java"))
    }

    /**
     * 测试：不启用 ChaquoPy 选项时不应获取容器工具
     */
    @Test
    fun `should NOT include container tools when ChaquoPy option is disabled`() {
        `when`(mockProotManager.isRunning).thenReturn(true)

        // 不使用 ChaquoPy 选项
        val tools = localTools.getTools(
            options = listOf(LocalToolOption.JavascriptEngine),
            sandboxId = testSandboxId
        )

        val hasAnyContainerTool = tools.any {
            it.name in listOf("container_python", "container_shell", "container_install")
        }

        assertFalse("不启用 ChaquoPy 时不应包含容器工具", hasAnyContainerTool)
    }

    /**
     * 测试：没有 sandboxId 时不应返回容器工具
     */
    @Test
    fun `should NOT include container tools when sandboxId is null`() {
        `when`(mockProotManager.isRunning).thenReturn(true)

        val tools = localTools.getTools(
            options = listOf(LocalToolOption.ChaquoPy),
            sandboxId = null  // 没有 sandboxId
        )

        val hasAnyContainerTool = tools.any {
            it.name in listOf("container_python", "container_shell", "container_install")
        }

        assertFalse("没有 sandboxId 时不应包含容器工具", hasAnyContainerTool)
    }

    /**
     * 测试：工具描述更新
     */
    @Test
    fun `getToolDescriptions should include container tools when running`() {
        `when`(mockProotManager.isRunning).thenReturn(true)

        val descriptions = localTools.getToolDescriptions(
            options = listOf(LocalToolOption.ChaquoPy)
        )

        assertTrue(
            "描述应包含容器工具",
            descriptions.contains("container_python") ||
            descriptions.contains("Container Tools")
        )
    }
}
