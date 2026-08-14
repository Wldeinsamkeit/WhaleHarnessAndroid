package org.whaleharness.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiClient {
    suspend fun send(
        config: ApiConfig,
        systemPrompt: String,
        userPrompt: String,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(EndpointResolver.chatCompletions(config.baseUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
        }

        try {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userPrompt))
            val body = JSONObject()
                .put("model", config.model.trim())
                .put("stream", false)
                .put("messages", messages)
                .toString()

            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "HTTP $status"
                error("请求失败：$message")
            }

            val message = JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
            val content = message.optString("content").trim()
            val reasoning = message.optString("reasoning_content").trim()
            when {
                content.isNotBlank() -> content
                reasoning.isNotBlank() -> reasoning
                else -> error("模型返回成功，但没有可显示的文本内容")
            }
        } finally {
            connection.disconnect()
        }
    }
}
