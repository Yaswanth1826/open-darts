package com.opendarts.session

import android.content.Context

/** Remembers the last player names so the next game starts with the same group. */
class PlayerStore(context: Context) {
    private val prefs = context.getSharedPreferences("players", Context.MODE_PRIVATE)

    fun load(): List<String>? = prefs.getString("names", null)?.split('\n')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }

    fun save(names: List<String>) {
        prefs.edit().putString("names", names.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")).apply()
    }
}