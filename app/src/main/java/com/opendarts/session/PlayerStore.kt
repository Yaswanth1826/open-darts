package com.opendarts.session

import android.content.Context

/** The pool of named players on this device and which of them are ticked for the next game, in throwing order. */
data class Roster(val names: List<String>, val selected: List<Int>) {
    val chosenNames: List<String> get() = selected.map { names[it] }

    companion object {
        const val SIZE = 8
        fun default() = Roster(List(SIZE) { "Player ${it + 1}" }, listOf(0, 1))
    }
}

/** Remembers the roster so the next game starts with the same group. */
class PlayerStore(context: Context) {
    private val prefs = context.getSharedPreferences("players", Context.MODE_PRIVATE)

    fun load(): Roster {
        val stored = prefs.getString("roster", null)?.split('\n')
        val names = Roster.default().names.mapIndexed { i, fallback -> stored?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: fallback }
        val selected = prefs.getString("selected", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.filter { it in 0 until Roster.SIZE }?.distinct()
        return Roster(names, selected ?: Roster.default().selected)
    }

    fun save(roster: Roster) {
        prefs.edit()
            .putString("roster", roster.names.joinToString("\n") { it.trim().replace('\n', ' ') })
            .putString("selected", roster.selected.joinToString(","))
            .apply()
    }
}