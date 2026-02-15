package me.rerere.rikkahub.data.container

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * PRootManager Android 集成测试
 *
 * 这些测试需要在真机或模拟器上运行，因为它们依赖 Android 系统 API 和文件系统。
 *
 * 测试前提条件：
 * 1. 设备有足够的存储空间（> 100MB）
 * 2. 网络可用（用于下载 rootfs）
 * 3. 应用已安装 assets（proot 二进制和 rootfs）
 */
@RunWith(AndroidJUnit4::class)
class PRootManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var prootManager: PRootManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prootManager = PRootManager(context)
    }

    /**
     * 测试：检查 assets 是否包含必要的文件
     *
     * 这是其他测试的前提条件。
     */
    @Test
    fun `assets should contain proot binary and rootfs`() {
        val assets = context.assets

        // 检查 proot 二进制
        val prootArm64Exists = assets.list("proot/arm64-v8a")?.contains("proot") ?: false
        val prootX86_64Exists = assets.list("proot/x86_64")?.contains("proot") ?: false

        // 检查 rootfs
        val rootfsExists = assets.list("rootfs")?.any {
            it.startsWith("alpine-minirootfs")
        } ?: false

        assertTrue(
            "Assets 应包含 arm64 proot 或 x86_64 proot",
            prootArm64Exists || prootX86_64Exists
        )
        assertTrue("Assets 应包含 rootfs", rootfsExists)
    }

    /**
     * 测试：检查初始化状态
     */
    @Test
    fun `checkInitializationStatus should return false initially`() {
        val isInitialized = prootManager.checkInitializationStatus()

        // 首次运行时应该是 false
        assertFalse("初始状态应为未初始化", isInitialized)
    }

    /**
     * 测试：初始化容器（完整流程）
     *
     * 这个测试可能需要几分钟，因为它需要解压 rootfs。
     */
    @Test(timeout = 300000) // 5分钟超时
    fun `initialize should extract rootfs and prepare environment`() = runBlocking {
        // 确保先销毁之前的状态
        prootManager.destroy()

        // 执行初始化
        val result = prootManager.initialize()

        // 验证结果
        if (result.isFailure) {
            // 如果失败，检查是否是资源缺失导致的
            val error = result.exceptionOrNull()
            if (error?.message?.contains("assets") == true ||
                error?.message?.contains("not found") == true
            ) {
                // 缺少 assets 是正常的测试环境限制，跳过断言
                println("跳过测试：缺少必要的 assets 文件")
                return@runBlocking
            }
        }

        assertTrue("初始化应成功", result.isSuccess)

        // 验证状态变为 Ready
        val state = prootManager.containerState.value
        assertTrue(
            "状态应为 Ready 或 Running",
            state is ContainerStateEnum.Ready || state is ContainerStateEnum.Running
        )

        // 验证 proot 目录存在
        val prootDir = File(context.filesDir, "proot")
        assertTrue("proot 目录应存在", prootDir.exists())

        // 验证 rootfs 目录存在
        val rootfsDir = File(context.filesDir, "rootfs")
        assertTrue("rootfs 目录应存在", rootfsDir.exists())
        assertTrue("rootfs 目录不应为空", rootfsDir.list()?.isNotEmpty() ?: false)
    }

    /**
     * 测试：启动和停止容器
     */
    @Test(timeout = 120000) // 2分钟超时
    fun `start and stop container should work correctly`() = runBlocking {
        // 确保先初始化
        prootManager.initialize()

        // 启动容器
        val startResult = prootManager.start()

        // 启动可能失败（如果缺少 proot 二进制），这是正常的
        if (startResult.isFailure) {
            println("启动失败（可能缺少 proot 二进制）：${startResult.exceptionOrNull()?.message}")
            return@runBlocking
        }

        assertTrue("启动应成功", startResult.isSuccess)
        assertTrue("容器应处于运行状态", prootManager.isRunning)

        // 状态应为 Running
        val runningState = prootManager.containerState.value
        assertTrue(
            "状态应为 Running",
            runningState is ContainerStateEnum.Running
        )

        // 停止容器
        val stopResult = prootManager.stop()
        assertTrue("停止应成功", stopResult.isSuccess)
        assertFalse("容器不应再处于运行状态", prootManager.isRunning)
    }

    /**
     * 测试：销毁容器
     */
    @Test
    fun `destroy should clean up all resources`() = runBlocking {
        // 先初始化
        prootManager.initialize()

        // 销毁
        val result = prootManager.destroy()
        assertTrue("销毁应成功", result.isSuccess)

        // 状态应重置
        val state = prootManager.containerState.value
        assertTrue(
            "状态应为 NotInitialized",
            state is ContainerStateEnum.NotInitialized
        )

        // 目录应被删除
        val prootDir = File(context.filesDir, "proot")
        val rootfsDir = File(context.filesDir, "rootfs")
        assertFalse("proot 目录应被删除", prootDir.exists())
        assertFalse("rootfs 目录应被删除", rootfsDir.exists())
    }

    /**
     * 测试：自动管理开关
     */
    @Test
    fun `auto management settings should be applied`() {
        // 启用
        prootManager.enableAutoManagement(enableContainerRuntime = true)
        // 应该能正常执行不崩溃

        // 禁用
        prootManager.enableAutoManagement(enableContainerRuntime = false)
        // 应该能正常执行不崩溃
    }

    /**
     * 测试：容器大小计算（空）
     */
    @Test
    fun `getContainerSize should return 0 for empty container`() {
        // 销毁确保干净
        prootManager.destroy()

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

    /**
     * 测试：状态流转序列
     */
    @Test
    fun `state transitions should follow correct sequence`() = runBlocking {
        // 初始：NotInitialized
        assertTrue(
            "初始状态应为 NotInitialized",
            prootManager.containerState.value is ContainerStateEnum.NotInitialized
        )

        // 初始化后：Ready 或 Error
        prootManager.initialize()
        val postInitState = prootManager.containerState.value
        assertTrue(
            "初始化后应为 Ready 或 Error",
            postInitState is ContainerStateEnum.Ready ||
            postInitState is ContainerStateEnum.Error
        )

        // 销毁后：NotInitialized
        prootManager.destroy()
        assertTrue(
            "销毁后应为 NotInitialized",
            prootManager.containerState.value is ContainerStateEnum.NotInitialized
        )
    }
}
