package com.sortasong.sortasong

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PlayerRepository {

    private const val PREFS_NAME = "player_data"
    private const val KEY_PLAYERS = "players_json"

    val players: MutableList<Player> = mutableListOf()

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PLAYERS, null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<Player>>() {}.type
            val loaded = Gson().fromJson<List<Player>>(json, type)
            players.clear()
            players.addAll(loaded)
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(players)
        prefs.edit().putString(KEY_PLAYERS, json).apply()
    }

    fun clear(context: Context) {
        players.clear()
        save(context)
    }
}
