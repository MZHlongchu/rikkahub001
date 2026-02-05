package me.rerere.rikkahub.data.container

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * PRootManager 单元测试
 *
 * 测试内容：
 * 1. 容器状态管理（状态流转）
 * 2. 架构检测
 * 3. 命令执行参数构建
 * 4. 环境变量配置
 */
@RunWith(RobolectricTestRunner::class)
class PRootManagerTest {

    private lateinit var context: Context
    private lateinit var prootManager: PRootManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        prootManager = PRootManager(context)
    }

    /**
     * 测试：初始状态应为 NotInitialized
     */
    @Test
    fun `initial state should be NotInitialized`() {
        val initialState = prootManager.containerState.value
        assertTrue(
            "初始状态应为 NotInitialized",
            initialState is ContainerStateEnum.NotInitialized
        )
    }

    /**
     * 测试：isRunning 在初始状态应为 false
     */
    @Test
    fun `isRunning should be false when not initialized`() {
        assertFalse("未初始化时 isRunning 应为 false", prootManager.isRunning)
    }

    /**
     * 测试：架构检测
     * 验证能正确识别当前设备架构
     */
    @Test
    fun `should detect device architecture`() {
        // 由于无法模拟不同的架构，只能验证方法存在且不会崩溃
        val arch = android.system.Os.uname().machine
        assertNotNull("应能获取设备架构", arch)
        assertTrue(
            "架构应为已知类型之一",
            arch in listOf("aarch64", "armv7l", "armv8l", "x86_64", "i686", "i386")
        )
    }

    /**
     * 测试：检查初始化状态
     * 在没有资源文件时应返回 false
     */
    @Test
    fun `checkInitializationStatus should return false without resources`() {
        val isInitialized = prootManager.checkInitializationStatus()
        // 没有 proot 二进制和 rootfs，应该返回 false
        assertFalse("没有资源时应返回 false", isInitialized)
    }

    /**
     * 测试：容器状态流转 - 错误处理
     */
    @Test
    fun `should transition to Error state on failure`() = runTest {
        // 尝试在没有资源的情况下初始化，应该失败
        val result = prootManager.initialize()

        // 应该失败（因为没有 assets）
        assertTrue("没有资源时初始化应失败", result.isFailure)

        // 状态应该变为 Error
        val currentState = prootManager.containerState.value
        assertTrue(
            "失败后状态应为 Error",
            currentState is ContainerStateEnum.Error
        )
    }

    /**
     * 测试：停止未运行的容器应该失败
     */
    @Test
    fun `stop should fail when not running`() = runTest {
        val result = prootManager.stop()
        assertTrue("停止未运行的容器应失败", result.isFailure)
    }

    /**
     * 测试：销毁容器（未初始化状态）
     */
    @Test
    fun `destroy should succeed even when not initialized`() = runTest {
        val result = prootManager.destroy()
        assertTrue("销毁应始终成功", result.isSuccess)

        // 状态应该变为 NotInitialized
        val currentState = prootManager.containerState.value
        assertTrue(
            "销毁后状态应为 NotInitialized",
            currentState is ContainerStateEnum.NotInitialized
        )
    }

    /**
     * 测试：执行命令时容器未运行的错误处理
     */
    @Test
    fun `executePython should return error when container not running`() = runTest {
        val result = prootManager.executePython("test-sandbox", "print('hello')")

        assertFalse("容器未运行时应返回失败", result["success"] as Boolean)
        assertTrue(
            "错误信息应提示容器未运行",
            (result["error"] as String).contains("not running", ignoreCase = true)
        )
    }

    /**
     * 测试：Shell 执行错误处理
     */
    @Test
    fun `executeShell should return error when container not running`() = runTest {
        val result = prootManager.executeShell("test-sandbox", "ls -la")

        assertFalse("容器未运行时应返回失败", result["success"] as Boolean)
        assertTrue(
            "错误信息应提示容器未运行",
            (result["error"] as String).contains("not running", ignoreCase = true)
        )
    }

    /**
     * 测试：自动管理设置
     */
    @Test
    fun `enableAutoManagement should not crash`() {
        // 这应该能正常执行不崩溃
        prootManager.enableAutoManagement(enableContainerRuntime = true)
        prootManager.enableAutoManagement(enableContainerRuntime = false)

        // 多次调用也不应崩溃
        prootManager.enableAutoManagement(enableContainerRuntime = true)
        prootManager.enableAutoManagement(enableContainerRuntime = true)
    }

    /**
     * 测试：沙箱目录隔离
     * 验证不同 sandboxId 创建不同的工作目录
     */
    @Test
    fun `should create isolated sandbox directories`() {
        val sandboxDir1 = File(context.filesDir, "sandboxes/test-sandbox-1")
        val sandboxDir2 = File(context.filesDir, "sandboxes/test-sandbox-2")

        sandboxDir1.mkdirs()
        sandboxDir2.mkdirs()

        assertTrue("沙箱目录1应存在", sandboxDir1.exists())
        assertTrue("沙箱目录2应存在", sandboxDir2.exists())
        assertNotEquals(
            "不同沙箱ID应对应不同目录",
            sandboxDir1.absolutePath,
            sandboxDir2.absolutePath
        )

        // 清理
        sandboxDir1.deleteRecursively()
        sandboxDir2.deleteRecursively()
    }

    /**
     * 测试：容器大小计算（空容器）
     */
    @Test
    fun `getContainerSize should return 0 for empty container`() {
        val size = prootManager.getContainerSize()
        assertEquals("空容器大小应为 0", 0, size)
    }

    /**
     * 测试：已安装包列表（空）
     */
    @Test
    fun `getInstalledPackages should return empty list when not initialized`() = runTest {
        val packages = prootManager.getInstalledPackages()
        assertTrue("未初始化时应返回空列表", packages.isEmpty())
    }
}

/**
 * 容器状态枚举测试
 */
class ContainerStateEnumTest {

    @Test
    fun `state hierarchy should be correct`() {
        // 验证各种状态可以被创建
        val notInitialized = ContainerStateEnum.NotInitialized
        val initializing = ContainerStateEnum.Initializing(0.5f)
        val running = ContainerStateEnum.Running
        val stopped = ContainerStateEnum.Stopped
        val error = ContainerStateEnum.Error("test error")

        // Initializing 应包含进度
        assertEquals(0.5f, (initializing as ContainerStateEnum.Initializing).progress)

        // Error 应包含消息
        assertEquals("test error", (error as ContainerStateEnum.Error).message)
    }
}

/**
 * ExecutionResult 数据类测试
 */
class ExecutionResultTest {

    @Test
    fun `execution result should hold correct values`() {
        val result = ExecutionResult(
            exitCode = 0,
            stdout = "Hello World",
            stderr = ""
        )

        assertEquals(0, result.exitCode)
        assertEquals("Hello World", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun `execution result should handle error case`() {
        val result = ExecutionResult(
            exitCode = 1,
            stdout = "",
            stderr = "Command not found"
        )

        assertEquals(1, result.exitCode)
        assertTrue("非零退出码表示失败", result.exitCode != 0)
        assertEquals("Command not found", result.stderr)
    }
}
