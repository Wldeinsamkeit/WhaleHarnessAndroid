package org.whaleharness.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.whaleharness.android.data.ApiClient
import org.whaleharness.android.data.ApiConfig
import org.whaleharness.android.data.AppRepository
import org.whaleharness.android.data.ChatMessage
import org.whaleharness.android.data.LocalAttachment
import org.whaleharness.android.data.PromptComposer
import org.whaleharness.android.data.Role
import org.whaleharness.android.data.RemoteHarnessClient
import org.whaleharness.android.data.RemoteHarnessConfig
import org.whaleharness.android.data.Skill

enum class AppTab { CHAT, SKILLS, SETTINGS, REMOTE }

data class AppUiState(
    val tab: AppTab = AppTab.CHAT,
    val config: ApiConfig = ApiConfig(),
    val skills: List<Skill> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val attachments: List<LocalAttachment> = emptyList(),
    val draft: String = "",
    val isBusy: Boolean = false,
    val remoteBaseUrl: String = "",
    val remoteToken: String = "",
    val remoteConfigured: Boolean = false,
    val remoteConnected: Boolean = false,
    val remoteBusy: Boolean = false,
    val notice: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val apiClient = ApiClient()
    private val remoteClient = RemoteHarnessClient()
    private val initialRemoteConfig = repository.loadRemoteConfig()
    private val _state = MutableStateFlow(
        AppUiState(
            config = repository.loadConfig(),
            skills = repository.loadSkills(),
            remoteBaseUrl = initialRemoteConfig?.baseUrl.orEmpty(),
            remoteConfigured = initialRemoteConfig != null,
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun selectTab(tab: AppTab) = _state.update { it.copy(tab = tab) }
    fun updateDraft(value: String) = _state.update { it.copy(draft = value) }
    fun updateBaseUrl(value: String) = _state.update { it.copy(config = it.config.copy(baseUrl = value)) }
    fun updateModel(value: String) = _state.update { it.copy(config = it.config.copy(model = value)) }
    fun updateApiKey(value: String) = _state.update { it.copy(config = it.config.copy(apiKey = value)) }
    fun updateRemoteBaseUrl(value: String) = _state.update { it.copy(remoteBaseUrl = value) }
    fun updateRemoteToken(value: String) = _state.update { it.copy(remoteToken = value) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun pairRemote(payload: String) {
        runCatching {
            RemoteHarnessConfig.fromPairingCode(payload).also(repository::saveRemoteConfig)
        }
            .onSuccess { config ->
                _state.update {
                    it.copy(
                        remoteBaseUrl = config.baseUrl,
                        remoteToken = "",
                        remoteConfigured = true,
                        notice = "已读取电脑配对码，正在测试连接",
                    )
                }
                testRemote()
            }
            .onFailure { error -> _state.update { it.copy(notice = error.message ?: "无法读取配对码") } }
    }

    fun saveAndTestRemote() {
        val snapshot = _state.value
        val saved = repository.loadRemoteConfig()
        val token = snapshot.remoteToken.ifBlank { saved?.token.orEmpty() }
        runCatching {
            RemoteHarnessConfig.fromManualEntry(snapshot.remoteBaseUrl, token).also(repository::saveRemoteConfig)
        }
            .onSuccess {
                _state.update { state -> state.copy(remoteToken = "", remoteConfigured = true) }
                testRemote()
            }
            .onFailure { error -> _state.update { it.copy(notice = error.message ?: "电脑连接配置无效") } }
    }

    fun testRemote() {
        val config = repository.loadRemoteConfig()
        if (config == null) {
            _state.update { it.copy(notice = "请先扫描电脑上的配对二维码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(remoteBusy = true, remoteConnected = false) }
            runCatching { withContext(Dispatchers.IO) { remoteClient.test(config) } }
                .onSuccess {
                    _state.update { it.copy(remoteBusy = false, remoteConnected = true, notice = "连接成功，电脑 Harness 已就绪") }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            remoteBusy = false,
                            remoteConnected = false,
                            notice = "无法连接电脑桥接器：${error.message ?: "请检查同一 Wi-Fi 和电脑端状态"}",
                        )
                    }
                }
        }
    }

    fun remoteEntryUrl(): String? = repository.loadRemoteConfig()?.entryUrl()

    fun disconnectRemote() {
        repository.clearRemoteConfig()
        _state.update {
            it.copy(
                remoteBaseUrl = "",
                remoteToken = "",
                remoteConfigured = false,
                remoteConnected = false,
                notice = "已断开电脑 Harness",
            )
        }
    }

    fun saveConfiguration(showConfirmation: Boolean = true): Boolean = runCatching {
        repository.saveConfig(_state.value.config)
        if (showConfirmation) _state.update { it.copy(notice = "API 配置已安全保存") }
        true
    }.getOrElse { error ->
        _state.update { it.copy(notice = error.message ?: "配置无法保存") }
        false
    }

    fun testConnection() {
        if (!saveConfiguration(showConfirmation = false)) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val snapshot = _state.value
            runCatching {
                apiClient.send(
                    snapshot.config,
                    PromptComposer.systemPrompt(snapshot.skills),
                    "请只回复：连接成功",
                )
            }.onSuccess { response ->
                _state.update { it.copy(isBusy = false, notice = "连接成功：${response.take(60)}") }
            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, notice = error.message ?: "连接失败") }
            }
        }
    }

    fun send() {
        val snapshot = _state.value
        val text = snapshot.draft.trim()
        if (text.isBlank() || snapshot.isBusy) return
        if (snapshot.config.apiKey.isBlank()) {
            _state.update { it.copy(tab = AppTab.SETTINGS, notice = "请先配置并测试 API") }
            return
        }

        val userMessage = ChatMessage(role = Role.USER, content = text)
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                draft = "",
                attachments = emptyList(),
                isBusy = true,
            )
        }

        viewModelScope.launch {
            runCatching {
                apiClient.send(
                    snapshot.config,
                    PromptComposer.systemPrompt(snapshot.skills),
                    PromptComposer.userPrompt(text, snapshot.attachments),
                )
            }.onSuccess { response ->
                _state.update {
                    it.copy(
                        messages = it.messages + ChatMessage(role = Role.ASSISTANT, content = response),
                        isBusy = false,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, notice = error.message ?: "请求失败") }
            }
        }
    }

    fun addAttachments(items: List<LocalAttachment>) {
        if (items.isEmpty()) {
            _state.update { it.copy(notice = "没有读到可用的文本文件") }
            return
        }
        _state.update { current ->
            current.copy(
                attachments = (current.attachments + items).distinctBy { it.id }.take(5),
                notice = "已添加 ${items.size} 个文件",
            )
        }
    }

    fun removeAttachment(id: String) = _state.update { current ->
        current.copy(attachments = current.attachments.filterNot { it.id == id })
    }

    fun importSkill(fileName: String, content: String) {
        if (content.isBlank()) {
            _state.update { it.copy(notice = "Skill 文件是空的") }
            return
        }
        val skill = repository.importedSkill(fileName, content)
        val updated = _state.value.skills + skill
        repository.saveSkills(updated)
        _state.update { it.copy(skills = updated, notice = "已导入 Skill：${skill.name}") }
    }

    fun saveSkill(skill: Skill) {
        val current = _state.value.skills
        val updated = if (current.any { it.id == skill.id }) {
            current.map { if (it.id == skill.id) skill else it }
        } else {
            current + skill
        }
        repository.saveSkills(updated)
        _state.update { it.copy(skills = updated, notice = "Skill 已保存") }
    }

    fun toggleSkill(skill: Skill, enabled: Boolean) = saveSkill(skill.copy(enabled = enabled))

    fun deleteSkill(skill: Skill) {
        val updated = _state.value.skills.filterNot { it.id == skill.id }
        repository.saveSkills(updated)
        _state.update { it.copy(skills = updated, notice = "已删除 ${skill.name}") }
    }
}
