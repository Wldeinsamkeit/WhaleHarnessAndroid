package org.whaleharness.android.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import org.whaleharness.android.data.RemotePairingRequest
import org.whaleharness.android.data.RemoteSession
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
    val remotePairCode: String = "",
    val remoteConfigured: Boolean = false,
    val remoteConnected: Boolean = false,
    val remoteBusy: Boolean = false,
    val remoteScanRequest: Int = 0,
    val remoteSessions: List<RemoteSession> = emptyList(),
    val remoteSessionId: String? = null,
    val remoteMessages: List<ChatMessage> = emptyList(),
    val remoteDraft: String = "",
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
    fun updateRemotePairCode(value: String) = _state.update { it.copy(remotePairCode = value.filter(Char::isDigit).take(8)) }
    fun updateRemoteDraft(value: String) = _state.update { it.copy(remoteDraft = value) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun openRemoteScanner() = _state.update {
        it.copy(tab = AppTab.REMOTE, remoteScanRequest = it.remoteScanRequest + 1)
    }

    fun consumeRemoteScanRequest() = _state.update { it.copy(remoteScanRequest = 0) }

    fun pairRemote(payload: String) {
        runCatching { RemotePairingRequest.fromPayload(payload) }
            .onSuccess(::pairAndConnect)
            .onFailure { error -> _state.update { it.copy(notice = error.message ?: "无法读取配对码") } }
    }

    fun saveAndTestRemote() {
        val snapshot = _state.value
        runCatching { RemotePairingRequest.fromManualEntry(snapshot.remoteBaseUrl, snapshot.remotePairCode) }
            .onSuccess(::pairAndConnect)
            .onFailure { error -> _state.update { it.copy(notice = error.message ?: "电脑连接配置无效") } }
    }

    private fun pairAndConnect(request: RemotePairingRequest) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    remoteBaseUrl = request.baseUrl,
                    remotePairCode = "",
                    remoteBusy = true,
                    remoteConnected = false,
                    notice = "正在与电脑 Harness 配对",
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    remoteClient.pair(request, "${Build.MANUFACTURER} ${Build.MODEL}".trim()).also(remoteClient::test)
                }
            }.onSuccess { config ->
                repository.saveRemoteConfig(config)
                loadRemoteSessions(config, "配对成功，手机已直连电脑 Harness")
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        remoteBusy = false,
                        remoteConnected = false,
                        notice = "无法配对电脑 Harness：${error.message ?: "请检查二维码和同一 Wi-Fi"}",
                    )
                }
            }
        }
    }

    fun testRemote() {
        val config = repository.loadRemoteConfig()
        if (config == null) {
            _state.update { it.copy(notice = "请先扫描电脑上的配对二维码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(remoteBusy = true, remoteConnected = false) }
            runCatching {
                withContext(Dispatchers.IO) {
                    remoteClient.test(config)
                    remoteClient.listSessions(config)
                }
            }
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            remoteBusy = false,
                            remoteConnected = true,
                            remoteSessions = it,
                            notice = "连接成功，手机正在直接控制电脑 Harness",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            remoteBusy = false,
                            remoteConnected = false,
                            notice = "无法连接电脑 Harness：${error.message ?: "请检查同一 Wi-Fi 和电脑端状态"}",
                        )
                    }
                }
        }
    }

    private fun loadRemoteSessions(config: RemoteHarnessConfig, notice: String? = null) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remoteClient.listSessions(config) } }
                .onSuccess { sessions ->
                    _state.update {
                        it.copy(
                            remoteBusy = false,
                            remoteConfigured = true,
                            remoteConnected = true,
                            remoteSessions = sessions,
                            notice = notice,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(remoteBusy = false, notice = error.message ?: "无法读取电脑会话") }
                }
        }
    }

    fun refreshRemoteSessions() {
        val config = repository.loadRemoteConfig() ?: return
        _state.update { it.copy(remoteBusy = true) }
        loadRemoteSessions(config)
    }

    fun openRemoteSession(sessionId: String) {
        val config = repository.loadRemoteConfig() ?: return
        viewModelScope.launch {
            _state.update { it.copy(remoteSessionId = sessionId, remoteMessages = emptyList(), remoteBusy = true) }
            runCatching { withContext(Dispatchers.IO) { remoteClient.history(config, sessionId) } }
                .onSuccess { messages -> _state.update { it.copy(remoteMessages = messages, remoteBusy = false) } }
                .onFailure { error -> _state.update { it.copy(remoteBusy = false, notice = error.message ?: "无法读取会话") } }
        }
    }

    fun closeRemoteSession() = _state.update { it.copy(remoteSessionId = null, remoteMessages = emptyList(), remoteDraft = "") }

    fun createRemoteSession() {
        val config = repository.loadRemoteConfig() ?: return
        viewModelScope.launch {
            _state.update { it.copy(remoteBusy = true) }
            runCatching { withContext(Dispatchers.IO) { remoteClient.createSession(config) } }
                .onSuccess(::openRemoteSession)
                .onFailure { error -> _state.update { it.copy(remoteBusy = false, notice = error.message ?: "无法新建会话") } }
        }
    }

    fun refreshRemoteHistory() {
        val sessionId = _state.value.remoteSessionId ?: return
        val config = repository.loadRemoteConfig() ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remoteClient.history(config, sessionId) } }
                .onSuccess { messages -> _state.update { it.copy(remoteMessages = messages, remoteBusy = false) } }
                .onFailure { error -> _state.update { it.copy(remoteBusy = false, notice = error.message ?: "刷新失败") } }
        }
    }

    fun sendRemote() {
        val snapshot = _state.value
        val sessionId = snapshot.remoteSessionId ?: return
        val config = repository.loadRemoteConfig() ?: return
        val text = snapshot.remoteDraft.trim()
        if (text.isBlank() || snapshot.remoteBusy) return
        _state.update {
            it.copy(
                remoteDraft = "",
                remoteBusy = true,
                remoteMessages = it.remoteMessages + ChatMessage(role = Role.USER, content = text),
            )
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remoteClient.prompt(config, sessionId, text) } }
                .onFailure { error ->
                    _state.update { it.copy(remoteBusy = false, notice = error.message ?: "任务发送失败") }
                    return@launch
                }
            repeat(30) {
                delay(2_000)
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        remoteClient.history(config, sessionId) to remoteClient.listSessions(config)
                    }
                }.getOrNull() ?: return@repeat
                val running = result.second.firstOrNull { it.id == sessionId }?.running == true
                _state.update {
                    it.copy(remoteMessages = result.first, remoteSessions = result.second, remoteBusy = running)
                }
                if (!running) return@launch
            }
            _state.update { it.copy(remoteBusy = false, notice = "任务仍在电脑运行，可点刷新查看结果") }
        }
    }

    fun disconnectRemote() {
        val config = repository.loadRemoteConfig()
        repository.clearRemoteConfig()
        if (config != null) viewModelScope.launch(Dispatchers.IO) { runCatching { remoteClient.revoke(config) } }
        _state.update {
            it.copy(
                remoteBaseUrl = "",
                remotePairCode = "",
                remoteConfigured = false,
                remoteConnected = false,
                remoteSessions = emptyList(),
                remoteSessionId = null,
                remoteMessages = emptyList(),
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
