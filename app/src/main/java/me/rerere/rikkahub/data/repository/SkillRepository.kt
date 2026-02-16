package me.rerere.rikkahub.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.api.MarketplaceResponse
import me.rerere.rikkahub.data.api.MarketplaceSkillItem
import me.rerere.rikkahub.data.model.AgentSkill
import me.rerere.rikkahub.data.model.SkillSource
import me.rerere.rikkahub.data.model.parseSkillMd
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream
import kotlin.uuid.Uuid

private const val TAG = "SkillRepository"
private const val MARKETPLACE_BASE_URL = "https://skillsmp.com"

class SkillRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    /**
     * 从 ZIP 文件 URI 导入技能
     * ZIP 中必须包含 SKILL.md 文件
     */
    suspend fun importFromZip(uri: Uri): Result<AgentSkill> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("无法打开文件")

            var skillMdContent: String? = null
            var iconBytes: ByteArray? = null
            var iconFileName: String? = null

            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        // 匹配根目录或子目录下的 SKILL.md
                        name.endsWith("SKILL.md", ignoreCase = true) && skillMdContent == null -> {
                            skillMdContent = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        // 提取图标文件
                        (name.endsWith(".png", ignoreCase = true) ||
                                name.endsWith(".jpg", ignoreCase = true) ||
                                name.endsWith(".svg", ignoreCase = true)) &&
                                name.contains("icon", ignoreCase = true) && iconBytes == null -> {
                            iconBytes = zis.readBytes()
                            iconFileName = name.substringAfterLast("/")
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val content = skillMdContent ?: error("ZIP 中未找到 SKILL.md 文件")
            val skill = parseSkillMd(content).copy(
                source = SkillSource.LOCAL,
            )

            // 如果有图标，保存到应用内部存储
            val iconUri = if (iconBytes != null && iconFileName != null) {
                saveIconToInternal(skill.id, iconBytes!!, iconFileName!!)
            } else null

            skill.copy(icon = iconUri)
        }
    }

    /**
     * 从市场获取技能列表
     */
    suspend fun fetchMarketplaceSkills(
        page: Int = 1,
        pageSize: Int = 20,
        query: String = "",
    ): Result<MarketplaceResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = StringBuilder("$MARKETPLACE_BASE_URL/api/skills?page=$page&page_size=$pageSize")
            if (query.isNotBlank()) {
                urlBuilder.append("&q=${Uri.encode(query)}")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Referer", "$MARKETPLACE_BASE_URL/zh")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("请求失败: ${response.code}")
            }

            val body = response.body?.string() ?: error("响应为空")
            json.decodeFromString<MarketplaceResponse>(body)
        }
    }

    /**
     * 从市场下载并安装技能
     */
    suspend fun downloadAndInstallSkill(item: MarketplaceSkillItem): Result<AgentSkill> = withContext(Dispatchers.IO) {
        runCatching {
            // 如果直接提供了 skill_content，直接解析
            if (!item.skillContent.isNullOrBlank()) {
                val skill = parseSkillMd(item.skillContent)
                return@runCatching skill.copy(
                    source = SkillSource.MARKETPLACE,
                    sourceUrl = "$MARKETPLACE_BASE_URL/zh/skill/${item.id}",
                )
            }

            // 否则下载 ZIP
            val downloadUrl = item.downloadUrl.ifBlank {
                "$MARKETPLACE_BASE_URL/api/skills/${item.id}/download"
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .addHeader("Accept", "application/zip, application/octet-stream, */*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Referer", "$MARKETPLACE_BASE_URL/zh/skills/${item.id}")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("下载失败: ${response.code}")
            }

            val contentType = response.header("Content-Type") ?: ""
            val bodyBytes = response.body?.bytes() ?: error("下载内容为空")

            if (contentType.contains("zip") || downloadUrl.endsWith(".zip")) {
                // ZIP 格式
                var skillMdContent: String? = null
                ZipInputStream(BufferedInputStream(bodyBytes.inputStream())).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith("SKILL.md", ignoreCase = true)) {
                            skillMdContent = zis.readBytes().toString(Charsets.UTF_8)
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                val content = skillMdContent ?: error("ZIP 中未找到 SKILL.md")
                parseSkillMd(content).copy(
                    source = SkillSource.MARKETPLACE,
                    sourceUrl = "$MARKETPLACE_BASE_URL/zh/skill/${item.id}",
                )
            } else {
                // 直接是 Markdown 内容
                val content = bodyBytes.toString(Charsets.UTF_8)
                parseSkillMd(content).copy(
                    source = SkillSource.MARKETPLACE,
                    sourceUrl = "$MARKETPLACE_BASE_URL/zh/skill/${item.id}",
                )
            }
        }
    }

    private fun saveIconToInternal(skillId: Uuid, bytes: ByteArray, fileName: String): String? {
        return try {
            val dir = context.getDir("skill_icons", Context.MODE_PRIVATE)
            val file = java.io.File(dir, "${skillId}_$fileName")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save skill icon", e)
            null
        }
    }
}

