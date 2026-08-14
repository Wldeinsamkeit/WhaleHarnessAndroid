package org.whaleharness.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class RemoteHarnessClient {
    fun pair(request: RemotePairingRequest, deviceName: String): RemoteHarnessConfig {
        val response = requestJson(
            url = request.pairingUrl(),
            method = "POST",
            body = JSONObject().put("code", request.code).put("deviceName", deviceName),
        )
        require(response.optString("product") == "whale-harness-mobile") {
            "电脑返回的不是 Whale Harness 移动配对服务"
        }
        val token = response.optString("token")
        require(token.isNotBlank()) { "电脑没有返回设备令牌" }
        return RemoteHarnessConfig(baseUrl = request.baseUrl, token = token)
    }

    fun test(config: RemoteHarnessConfig) {
        val response = requestJson(config.healthUrl(), "GET", token = config.token)
        require(response.optString("product") == "whale-harness-mobile" && response.optString("harness") == "ready") {
            "电脑 Harness 移动直连服务响应无法识别"
        }
    }

    fun listSessions(config: RemoteHarnessConfig): List<RemoteSession> {
        val items = rpc(config, "session.list", JSONObject()).optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                if (item.optBoolean("blank")) continue
                val title = item.optJSONObject("projections")
                    ?.optJSONObject("values")
                    ?.optString("title")
                    ?.takeIf { it.isNotBlank() }
                val cwd = item.optString("cwd").takeIf { it.isNotBlank() }
                add(
                    RemoteSession(
                        id = item.getString("sessionId"),
                        title = title ?: cwd?.substringAfterLast('/') ?: "未命名会话",
                        cwd = cwd,
                        updatedAt = item.optLong("updatedAt"),
                        running = item.optBoolean("running"),
                    ),
                )
            }
        }
    }

    fun createSession(config: RemoteHarnessConfig): String =
        rpc(config, "session.create", JSONObject()).getString("sessionId")

    fun history(config: RemoteHarnessConfig, sessionId: String): List<ChatMessage> {
        val value = rpc(
            config,
            "session.history",
            JSONObject().put("sessionId", sessionId).put("maxMessages", 100),
        )
        val events = value.optJSONArray("events") ?: JSONArray()
        return buildList {
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index)?.optJSONObject("event") ?: continue
                val type = event.optString("type")
                val data = event.optJSONObject("data") ?: continue
                val content = when (type) {
                    "user/message" -> data.optJSONArray("content")
                    "assistant/message" -> data.optJSONObject("message")?.optJSONArray("content")
                    else -> null
                } ?: continue
                val text = content.textBlocks()
                if (text.isBlank()) continue
                add(
                    ChatMessage(
                        id = "remote-${event.optLong("seq", index.toLong())}-$type",
                        role = if (type == "user/message") Role.USER else Role.ASSISTANT,
                        content = text,
                    ),
                )
            }
        }
    }

    fun prompt(config: RemoteHarnessConfig, sessionId: String, text: String) {
        rpc(
            config,
            "session.prompt",
            JSONObject()
                .put("sessionId", sessionId)
                .put("mode", "queue")
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text))),
        )
    }

    fun revoke(config: RemoteHarnessConfig) {
        requestJson("${config.baseUrl.trimEnd('/')}/v1/device", "DELETE", token = config.token)
    }

    private fun rpc(config: RemoteHarnessConfig, method: String, payload: JSONObject): JSONObject {
        val rpcId = UUID.randomUUID().toString()
        val response = requestJson(
            url = "${config.baseUrl.trimEnd('/')}/api/$method",
            method = "POST",
            token = config.token,
            body = JSONObject()
                .put("type", "client-request")
                .put("rpcId", rpcId)
                .put("method", method)
                .put("payload", payload),
        )
        val result = response.getJSONObject("result")
        if (!result.optBoolean("ok")) {
            val error = result.optJSONObject("error")
            throw IllegalStateException(error?.optString("message")?.takeIf { it.isNotBlank() } ?: "$method 执行失败")
        }
        return result.optJSONObject("value") ?: JSONObject()
    }

    private fun requestJson(
        url: String,
        method: String,
        token: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val status = connection.responseCode
            val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            require(status in 200..299) {
                parsed.optString("message").takeIf { it.isNotBlank() } ?: "电脑 Harness 拒绝连接（HTTP $status）"
            }
            return parsed
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONArray.textBlocks(): String = buildList {
    for (index in 0 until length()) {
        val block = optJSONObject(index) ?: continue
        if (block.optString("type") == "text") {
            block.optString("text").takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}.joinToString("")
