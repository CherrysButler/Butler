package com.cherry.butler.core.generation

import com.cherry.butler.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyDefinitionTest {

    // The shape Janitor assembles for a proxy (sections as captured 2026-09-23 / 2026-10-05; made-up text).
    private val prompt = """
        <REALISTICDIALOGUE>
        Write vividly.
        <Ash's Persona>[Personality="dry", "kind"]
        [Ash keeps a ledger of every guest, Rain included.]
        <rules>Stay in character.</rules>
        </Ash's Persona>
        <Scenario>Rain arrives at the Larkspur long after midnight.</Scenario>
        <UserPersona>A 20 year old, dark hair.</UserPersona>
        <example_dialogs>Ash: *looks up* "Late again, Rain."</example_dialogs>
        <summary>Nothing yet.</summary>
    """.trimIndent()

    @Test
    fun `the persona, scenario and examples come out, with the user's name put back as {{user}}`() {
        val d = ProxyDefinition.Prompt.definition(prompt, userName = "Rain")
        assertEquals("[Personality=\"dry\", \"kind\"]\n[Ash keeps a ledger of every guest, {{user}} included.]\n<rules>Stay in character.</rules>", d.personality)
        assertEquals("{{user}} arrives at the Larkspur long after midnight.", d.scenario)
        assertEquals("Ash: *looks up* \"Late again, {{user}}.\"", d.exampleDialogs)
    }

    @Test
    fun `a name inside another word is left alone`() {
        val d = ProxyDefinition.Prompt.definition("<Mia's Persona>Rainy days; Rain's coat.</Mia's Persona>", userName = "Rain")
        assertEquals("Rainy days; {{user}}'s coat.", d.personality)
    }

    @Test
    fun `missing sections are empty`() {
        val d = ProxyDefinition.Prompt.definition("<Mia's Persona>A maid.</Mia's Persona><UserPersona>x</UserPersona>", userName = null)
        assertEquals("A maid.", d.personality)
        assertEquals("", d.scenario)
        assertEquals("", d.exampleDialogs)
    }

    @Test(expected = ApiError.Api::class)
    fun `a prompt without the character says so`() {
        ProxyDefinition.Prompt.definition("<UserPersona>x</UserPersona>", userName = null)
    }
}
