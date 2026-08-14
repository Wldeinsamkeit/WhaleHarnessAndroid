package org.whaleharness.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun endpointAddsChatCompletionsPath() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            EndpointResolver.chatCompletions("https://api.deepseek.com/"),
        )
    }

    @Test
    fun endpointDoesNotDuplicateExistingPath() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            EndpointResolver.chatCompletions("https://example.com/v1/chat/completions"),
        )
    }

    @Test
    fun endpointRejectsCleartextHttp() {
        assertThrows(IllegalArgumentException::class.java) {
            EndpointResolver.chatCompletions("http://example.com/v1")
        }
    }

    @Test
    fun systemPromptOnlyIncludesEnabledSkills() {
        val result = PromptComposer.systemPrompt(
            listOf(
                Skill(name = "启用", prompt = "先给结论", enabled = true),
                Skill(name = "关闭", prompt = "不应出现", enabled = false),
            ),
        )
        assertTrue(result.contains("先给结论"))
        assertFalse(result.contains("不应出现"))
    }

    @Test
    fun userPromptIncludesExplicitAttachments() {
        val result = PromptComposer.userPrompt(
            "审查代码",
            listOf(LocalAttachment(name = "Main.kt", content = "fun main() = Unit")),
        )
        assertTrue(result.contains("审查代码"))
        assertTrue(result.contains("Main.kt"))
        assertTrue(result.contains("fun main() = Unit"))
    }

    @Test
    fun pairingQrExtractsHarnessEndpointAndOneTimeCode() {
        val request = RemotePairingRequest.fromPayload(
            "whaleharness://pair?endpoint=http%3A%2F%2F192.168.1.20%3A43117&code=12345678&v=1",
        )
        assertEquals("http://192.168.1.20:43117", request.baseUrl)
        assertEquals("12345678", request.code)
        assertEquals("http://192.168.1.20:43117/v1/pair", request.pairingUrl())
    }

    @Test
    fun manualPairingAcceptsLocalHostnameAndTailscaleAddress() {
        assertEquals(
            "http://my-mac.local:43117",
            RemotePairingRequest.fromManualEntry("http://my-mac.local:43117", "12345678").baseUrl,
        )
        assertEquals(
            "http://100.64.1.8:43117",
            RemotePairingRequest.fromManualEntry("http://100.64.1.8:43117", "12345678").baseUrl,
        )
    }

    @Test
    fun pairingCodeRejectsPublicCleartextAddress() {
        assertThrows(IllegalArgumentException::class.java) {
            RemotePairingRequest.fromManualEntry("http://example.com:43117", "12345678")
        }
    }

    @Test
    fun pairingCodeRequiresEightDigits() {
        assertThrows(IllegalArgumentException::class.java) {
            RemotePairingRequest.fromManualEntry("http://192.168.1.20:43117", "1234")
        }
    }

    @Test
    fun pairingQrRejectsOldBridgeUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            RemotePairingRequest.fromPayload("http://192.168.1.20:3081/?token=legacy")
        }
    }
}
