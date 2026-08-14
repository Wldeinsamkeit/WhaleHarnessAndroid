package org.whaleharness.android.data

import java.net.URI
import java.util.UUID

data class ApiConfig(
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val apiKey: String = "",
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
