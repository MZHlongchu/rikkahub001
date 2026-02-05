package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.model.WorkflowPhase

/**
 * ToolGuard - Workflow 模式下的工具执行守卫
 *
 * 根据当前 WorkflowPhase 决定是否允许执行特定工具
 * - PLAN: 只允许只读操作
 * - EXECUTE: 允许所有操作
 * - REVIEW: 只允许只读操作
 */
object ToolGuard {

    // 沙箱工具名称常量
    private const val SANDBOX_FILE = "sandbox_file"
    private const val SANDBOX_PYTHON = "sandbox_python"
    private const val SANDBOX_SHELL = "sandbox_shell"
    private const val SANDBOX_DATA = "sandbox_data"
    private const val SANDBOX_DEV = "sandbox_dev"

    // 只读操作白名单（PLAN/REVIEW 阶段允许）
    private val READONLY_OPERATIONS = setOf(
        "read",           // 读取文件
        "list",           // 列出目录
        "stat",           // 文件信息
        "exists",         // 检查存在性
        "git_status",     // Git 状态
        "git_log",        // Git 日志
        "git_branch",     // 查看分支（只读）
        "git_diff"        // Git 差异
    )

    // 写操作黑名单（PLAN/REVIEW 阶段禁止）
    private val WRITE_OPERATIONS = setOf(
        "write",          // 写入文件
        "delete",         // 删除文件/目录
        "mkdir",          // 创建目录
        "copy",           // 复制文件
        "move",           // 移动文件
        "unzip",          // 解压
        "zip_create",     // 创建压缩包
        "git_init",       // Git 初始化
        "git_add",        // Git 添加
        "git_commit",     // Git 提交
        "git_rm",         // Git 删除
        "git_mv",         // Git 移动
        "git_checkout",   // Git 切换分支（改变状态）
        "git_reset",      // Git 重置
        "git_restore"     // Git 恢复
    )

    // 危险操作（EXECUTE 阶段也需要特别注意）
    private val DANGEROUS_OPERATIONS = setOf(
        "python_exec",      // Python 执行
        "exec",             // Shell 执行
        "exec_script",      // 脚本执行
        "exec_js",          // JavaScript 执行
        "exec_lua",         // Lua 执行
        "sqlite_query",     // SQLite 查询（可能修改）
        "process_image",    // 图像处理（可能覆盖）
        "download_file",    // 下载文件
        "convert_excel",    // Excel 转换
        "extract_pdf_text", // PDF 提取
        "matplotlib_plot"   // 绘图
    )

    /**
     * 检查工具操作是否被允许
     *
     * @param phase 当前 Workflow 阶段
     * @param toolName 工具名称
     * @param operation 具体操作（对于沙箱工具）
     * @return 是否允许执行
     */
    fun isAllowed(
        phase: WorkflowPhase,
        toolName: String,
        operation: String? = null
    ): Boolean {
        return when (phase) {
            WorkflowPhase.EXECUTE -> {
                // EXECUTE 模式下允许所有操作
                true
            }
            WorkflowPhase.PLAN, WorkflowPhase.REVIEW -> {
                // PLAN/REVIEW 模式下只允许只读操作
                when {
                    // 非沙箱工具，根据工具名称判断
                    toolName != SANDBOX_FILE &&
                    toolName != SANDBOX_PYTHON &&
                    toolName != SANDBOX_SHELL &&
                    toolName != SANDBOX_DATA &&
                    toolName != SANDBOX_DEV -> {
                        // 对于非沙箱工具，默认只允许 eval_javascript 和 search_web
                        toolName == "eval_javascript" || toolName == "search_web"
                    }
                    // 沙箱工具，根据 operation 判断
                    operation != null -> {
                        operation in READONLY_OPERATIONS
                    }
                    // 没有 operation 信息，保守起见拒绝
                    else -> false
                }
            }
        }
    }

    /**
     * 获取操作被拒绝的原因
     *
     * @param phase 当前 Workflow 阶段
     * @param toolName 工具名称
     * @param operation 具体操作
     * @return 拒绝原因描述
     */
    fun getBlockedReason(
        phase: WorkflowPhase,
        toolName: String,
        operation: String? = null
    ): String {
        return when (phase) {
            WorkflowPhase.PLAN -> {
                """
                ❌ 操作被拦截：当前处于 PLAN（规划）阶段
                
                你尝试执行的操作 '${operation ?: toolName}' 在此阶段被禁止。
                
                PLAN 阶段只能执行只读操作：
                • 读取文件内容 (file_read)
                • 列出目录结构 (file_list)
                • 查看Git状态/日志 (git_status, git_log)
                • 分析现有代码和数据
                
                💡 解决方案：
                请告诉用户："需要切换到 EXECUTE 阶段才能执行写入/执行操作。"
                建议先在当前阶段完成需求分析和方案规划。
                """.trimIndent()
            }
            WorkflowPhase.REVIEW -> {
                """
                ❌ 操作被拦截：当前处于 REVIEW（审查）阶段
                
                你尝试执行的操作 '${operation ?: toolName}' 在此阶段被禁止。
                
                REVIEW 阶段只能执行只读操作：
                • 查看代码文件 (file_read)
                • 对比代码差异 (git_diff)
                • 检查代码质量和安全性
                
                💡 解决方案：
                请告诉用户："发现需要修改的问题，请切换到 EXECUTE 阶段进行修复。"
                在此阶段只进行代码审查，不做任何修改。
                """.trimIndent()
            }
            WorkflowPhase.EXECUTE -> {
                "⚠️ 内部错误：操作在 EXECUTE 阶段不应被拦截。"
            }
        }
    }

    /**
     * 判断操作是否需要审批（用于设置 Tool.needsApproval）
     *
     * @param phase 当前 Workflow 阶段
     * @param toolName 工具名称
     * @param operation 具体操作
     * @return 是否需要审批
     */
    fun needsApproval(
        phase: WorkflowPhase,
        toolName: String,
        operation: String? = null
    ): Boolean {
        return when (phase) {
            WorkflowPhase.EXECUTE -> {
                // EXECUTE 模式下自动批准，不需要审批
                false
            }
            WorkflowPhase.PLAN, WorkflowPhase.REVIEW -> {
                // PLAN/REVIEW 模式下，只读操作不需要审批（但会被 isAllowed 拦截）
                // 这里返回 false，让 isAllowed 来处理拦截逻辑
                false
            }
        }
    }

    /**
     * 从工具调用参数中提取 operation 名称
     *
     * @param toolName 工具名称
     * @param arguments 工具调用参数（JSON 字符串）
     * @return operation 名称，如果无法提取则返回 null
     */
    fun extractOperation(toolName: String, arguments: String): String? {
        return try {
            val json = Json.parseToJsonElement(arguments)
            when (val operation = json.jsonObject["operation"]) {
                is kotlinx.serialization.json.JsonPrimitive -> operation.content
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
