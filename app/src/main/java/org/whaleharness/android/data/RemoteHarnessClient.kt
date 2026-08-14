package org.whaleharness.android.data

import java.net.HttpURLConnection
import java.net.URL

class RemoteHarnessClient {
    fun test(config: RemoteHarnessConfig) {
        val connection = (URL(config.healthUrl()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Authorization", "Bearer ${config.token}")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(status in 200..299) {
                if (status == 502) "桥接器已连接，但电脑上的 DeepSeek Harness 没有运行" else "桥接器拒绝连接（HTTP $status）"
            }
            require(body.contains("\"bridge\":\"ok\"") && body.contains("\"upstream\":\"ok\"")) {
                "桥接器响应无法识别"
            }
        } finally {
            connection.disconnect()
        }
    }
}
