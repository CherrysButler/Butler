package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent mode (beta): a reply is drafted, checked against goals, fixed and only then
 * delivered. Off by default; proxies only, and meant for reasoning models. [Effort] is how
 * many check-and-fix rounds a reply may take; [goals] are the user's own goals, one per
 * line, on top of Butler's.
 */
@Singleton
class AgentPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    enum class Effort(val label: String, val rounds: Int, val blurb: String) {
        Light("Light", 1, "One check, small fixes"),
        Balanced("Balanced", 2, "Up to two rounds"),
        Thorough("Thorough", 3, "Up to three rounds, rewrites allowed"),
    }

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ON, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _effort = MutableStateFlow(runCatching { Effort.valueOf(prefs.getString(KEY_EFFORT, null) ?: "") }.getOrDefault(Effort.Balanced))
    val effort: StateFlow<Effort> = _effort.asStateFlow()

    private val _goals = MutableStateFlow(prefs.getString(KEY_GOALS, null).orEmpty())
    val goals: StateFlow<String> = _goals.asStateFlow()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ON, on).apply()
    }

    fun setEffort(effort: Effort) {
        _effort.value = effort
        prefs.edit().putString(KEY_EFFORT, effort.name).apply()
    }

    fun setGoals(text: String) {
        val kept = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").take(2000)
        _goals.value = kept
        prefs.edit().putString(KEY_GOALS, kept).apply()
    }

    private companion object {
        const val KEY_ON = "agent_on"
        const val KEY_EFFORT = "agent_effort"
        const val KEY_GOALS = "agent_goals"
    }
}
