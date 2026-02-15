package me.rerere.rikkahub.ui.pages.skill

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.api.MarketplaceSkillItem
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.AgentSkill
import me.rerere.rikkahub.data.repository.SkillRepository
import kotlin.uuid.Uuid

class SkillVM(
    private val settingsStore: SettingsStore,
    private val skillRepository: SkillRepository,
    private val context: Application,
) : ViewModel() {

    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    val skills = settingsStore.settingsFlow
        .map { it.agentSkills }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 市场状态
    private val _marketplaceSkills = MutableStateFlow<List<MarketplaceSkillItem>>(emptyList())
    val marketplaceSkills: StateFlow<List<MarketplaceSkillItem>> = _marketplaceSkills.asStateFlow()

    private val _isLoadingMarketplace = MutableStateFlow(false)
    val isLoadingMarketplace: StateFlow<Boolean> = _isLoadingMarketplace.asStateFlow()

    private val _marketplaceError = MutableStateFlow<String?>(null)
    val marketplaceError: StateFlow<String?> = _marketplaceError.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    /**
     * 从 ZIP 导入技能
     */
    fun importFromZip(uri: Uri, onResult: (Result<AgentSkill>) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = skillRepository.importFromZip(uri)
            result.onSuccess { skill ->
                val current = settings.value
                settingsStore.update(
                    current.copy(
                        agentSkills = current.agentSkills + skill
                    )
                )
            }
            _isImporting.value = false
            onResult(result)
        }
    }

    /**
     * 添加技能
     */
    fun addSkill(skill: AgentSkill) {
        viewModelScope.launch {
            val current = settings.value
            settingsStore.update(
                current.copy(
                    agentSkills = current.agentSkills + skill
                )
            )
        }
    }

    /**
     * 更新技能
     */
    fun updateSkill(skill: AgentSkill) {
        viewModelScope.launch {
            val current = settings.value
            settingsStore.update(
                current.copy(
                    agentSkills = current.agentSkills.map {
                        if (it.id == skill.id) skill.copy(updatedAt = System.currentTimeMillis())
                        else it
                    }
                )
            )
        }
    }

    /**
     * 删除技能
     */
    fun deleteSkill(skillId: Uuid) {
        viewModelScope.launch {
            val current = settings.value
            settingsStore.update(
                current.copy(
                    agentSkills = current.agentSkills.filter { it.id != skillId },
                    // 同时清理助手中的引用
                    assistants = current.assistants.map { assistant ->
                        assistant.copy(
                            skillIds = assistant.skillIds - skillId
                        )
                    }
                )
            )
        }
    }

    /**
     * 切换技能启用状态
     */
    fun toggleSkill(skillId: Uuid) {
        viewModelScope.launch {
            val current = settings.value
            settingsStore.update(
                current.copy(
                    agentSkills = current.agentSkills.map {
                        if (it.id == skillId) it.copy(enabled = !it.enabled)
                        else it
                    }
                )
            )
        }
    }

    /**
     * 加载市场技能列表
     */
    fun loadMarketplaceSkills(query: String = "") {
        viewModelScope.launch {
            _isLoadingMarketplace.value = true
            _marketplaceError.value = null
            skillRepository.fetchMarketplaceSkills(query = query)
                .onSuccess { response ->
                    _marketplaceSkills.value = response.skills
                }
                .onFailure { error ->
                    _marketplaceError.value = error.message
                }
            _isLoadingMarketplace.value = false
        }
    }

    /**
     * 从市场下载并安装技能
     */
    fun installFromMarketplace(item: MarketplaceSkillItem, onResult: (Result<AgentSkill>) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = skillRepository.downloadAndInstallSkill(item)
            result.onSuccess { skill ->
                val current = settings.value
                // 检查是否已安装（通过 sourceUrl 判断）
                val existing = current.agentSkills.find { it.sourceUrl == skill.sourceUrl }
                if (existing != null) {
                    // 更新已有技能
                    settingsStore.update(
                        current.copy(
                            agentSkills = current.agentSkills.map {
                                if (it.id == existing.id) skill.copy(
                                    id = existing.id,
                                    updatedAt = System.currentTimeMillis()
                                )
                                else it
                            }
                        )
                    )
                } else {
                    settingsStore.update(
                        current.copy(
                            agentSkills = current.agentSkills + skill
                        )
                    )
                }
            }
            _isImporting.value = false
            onResult(result)
        }
    }
}
