package me.rerere.rikkahub.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Skills Marketplace API 响应模型
 * 对应 https://skillsmp.com/zh 的 API
 */
@Serializable
data class MarketplaceSkillItem(
    val id: String,
    val name: String,
    val description: String = "",
    val author: String = "",
    val version: String = "1.0.0",
    val icon: String? = null,
    @SerialName("download_url")
    val downloadUrl: String = "",
    @SerialName("skill_content")
    val skillContent: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("download_count")
    val downloadCount: Int = 0,
    val rating: Float = 0f,
)

@Serializable
data class MarketplaceResponse(
    val skills: List<MarketplaceSkillItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size")
    val pageSize: Int = 20,
)
