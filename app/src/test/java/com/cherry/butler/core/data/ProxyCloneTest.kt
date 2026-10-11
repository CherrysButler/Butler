package com.cherry.butler.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyCloneTest {
    private val source = ProxyConfig(
        id = "p1", name = "OpenRouter", apiUrl = "https://openrouter.ai/api/v1/chat/completions", model = "deepseek/v4",
        hasKey = true, promptId = "pr1", promptName = "Main", clientId = "c1",
    )
    private val prompts = listOf(Prompt("pr1", "Main", "system", "Be vivid."), Prompt("pr2", "Other", "system", "Be brief."))

    private fun form(name: String = source.name, url: String = source.apiUrl, model: String = source.model, promptId: String? = source.promptId, key: String? = null) =
        ProxyDraft(id = null, name = name, apiUrl = url, model = model, apiKey = key, promptId = promptId)

    @Test
    fun `without a form the copy is the server's`() {
        val d = SettingsRepository.cloneDraft(source, null, key = "k", prompts = prompts, legacyPrompt = null)
        assertEquals(ProxyDraft(null, "OpenRouter copy", source.apiUrl, "deepseek/v4", "k", "pr1"), d)
    }

    @Test
    fun `the form's unsaved edits are what get copied`() {
        val d = SettingsRepository.cloneDraft(
            source, form(name = "Fast", url = "https://example.com/v1", model = "kimi/k2", promptId = "pr2", key = "typed"),
            key = "typed", prompts = prompts, legacyPrompt = null,
        )
        assertEquals(ProxyDraft(null, "Fast copy", "https://example.com/v1", "kimi/k2", "typed", "pr2"), d)
    }

    @Test
    fun `none picked in the form is kept as none`() {
        assertNull(SettingsRepository.clonedPromptId(source, form(promptId = null), prompts, legacyPrompt = "Be vivid."))
    }

    @Test
    fun `a prompt the server left out is found by the text the legacy config carries`() {
        val unlinked = source.copy(promptId = null, promptName = null)
        assertEquals("pr1", SettingsRepository.clonedPromptId(unlinked, form(promptId = null), prompts, legacyPrompt = "  Be vivid.\n"))
        assertEquals("pr1", SettingsRepository.clonedPromptId(unlinked, null, prompts, legacyPrompt = "Be vivid."))
        assertNull(SettingsRepository.clonedPromptId(unlinked, null, prompts, legacyPrompt = "Something else"))
        assertNull(SettingsRepository.clonedPromptId(unlinked, null, prompts, legacyPrompt = null))
    }

    @Test
    fun `a blank name or URL in the form falls back to the server's`() {
        val d = SettingsRepository.cloneDraft(source, form(name = "  ", url = ""), key = null, prompts = prompts, legacyPrompt = null)
        assertEquals("OpenRouter copy", d.name)
        assertEquals(source.apiUrl, d.apiUrl)
    }

    @Test
    fun `a long name is cut to Janitor's limit`() {
        val d = SettingsRepository.cloneDraft(source, form(name = "x".repeat(300)), key = null, prompts = prompts, legacyPrompt = null)
        assertEquals(255, d.name.length)
    }
}
