package org.whaleharness.android.data

import java.net.URI
import java.net.URLDecoder
import java.util.UUID

data class ApiConfig(
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val apiKey: String = "",
)

data class RemoteHarnessConfig(
    val baseUrl: String,
    val token: String,
) {
    fun healthUrl(): String = "${baseUrl.trimEnd('/')}/v1/health"
}

data class RemotePairingRequest(
    val baseUrl: String,
    val code: String,
) {
    fun pairingUrl(): String = "${baseUrl.trimEnd('/')}/v1/pair"

    companion object {
        fun fromPayload(payload: String): RemotePairingRequest {
            val uri = URI(payload.trim())
            require(uri.scheme == "whaleharness" && uri.host == "pair") { "这不是 DeepSeek Harness 移动配对二维码" }
            val parameters = uri.rawQuery.orEmpty().split('&').mapNotNull { item ->
                val parts = item.split('=', limit = 2)
                val key = parts.firstOrNull() ?: return@mapNotNull null
                key to URLDecoder.decode(parts.getOrNull(1).orEmpty(), Charsets.UTF_8.name())
            }.toMap()
            return fromManualEntry(
                baseUrl = parameters["endpoint"].orEmpty(),
                code = parameters["code"].orEmpty(),
            )
        }

        fun fromManualEntry(baseUrl: String, code: String): RemotePairingRequest {
            val uri = URI(baseUrl.trim().trimEnd('/'))
            require(uri.scheme == "http" || uri.scheme == "https") { "电脑地址必须是 HTTP 或 HTTPS" }
            require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "电脑地址无效" }
            require(code.trim().matches(Regex("\\d{8}"))) { "请输入电脑 Harness 显示的 8 位配对码" }
            require(uri.scheme == "https" || isTrustedLocalHost(uri.host)) {
                "HTTP 直连只允许局域网、.local 或 Tailscale 地址"
            }
            val base = URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString()
            return RemotePairingRequest(baseUrl = base, code = code.trim())
        }

        private fun isTrustedLocalHost(host: String): Boolean {
            val normalized = host.lowercase()
            if (normalized == "localhost" || normalized.endsWith(".local")) return true
            if (normalized.startsWith("fe80:") || normalized.startsWith("fc") || normalized.startsWith("fd")) return true
            val parts = normalized.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return false
            return parts[0] == 10 ||
                (parts[0] == 172 && parts[1] in 16..31) ||
                (parts[0] == 192 && parts[1] == 168) ||
                (parts[0] == 100 && parts[1] in 64..127) ||
                parts[0] == 127
        }
    }
}

data class RemoteSession(
    val id: String,
    val title: String,
    val cwd: String?,
    val updatedAt: Long,
    val running: Boolean,
)

data class Skill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val prompt: String,
    val enabled: Boolean = true,
)

data class LocalAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
)

enum class Role { USER, ASSISTANT }

object EndpointResolver {
    fun chatCompletions(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.isNotBlank()) { "请填写 API 地址" }
        val uri = URI(trimmed)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "API 地址必须使用有效的 HTTPS" }
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }
}

object PromptComposer {
    fun systemPrompt(skills: List<Skill>): String {
        val enabled = skills.filter { it.enabled && it.prompt.isNotBlank() }
        if (enabled.isEmpty()) return "你是小鲸鱼，一个诚实、清晰、尊重用户数据边界的个人 AI 助手。"
        return buildString {
            appendLine("你是小鲸鱼，一个诚实、清晰、尊重用户数据边界的个人 AI 助手。")
            appendLine("下面是用户明确启用的 Skills：")
            enabled.forEach {
                appendLine("\n## ${it.name}")
                appendLine(it.prompt.trim())
            }
        }.trim()
    }

    fun userPrompt(text: String, attachments: List<LocalAttachment>): String {
        if (attachments.isEmpty()) return text.trim()
        return buildString {
            appendLine(text.trim())
            appendLine("\n--- 用户选择的本地文件 ---")
            attachments.forEach {
                appendLine("\n### ${it.name}")
                appendLine(it.content)
            }
        }.trim()
    }
}
