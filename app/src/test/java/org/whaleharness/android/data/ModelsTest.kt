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
}
