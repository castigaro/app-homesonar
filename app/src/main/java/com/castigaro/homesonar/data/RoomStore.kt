package com.castigaro.homesonar.data

import android.content.Context
import org.json.JSONArray

/** Verwaltet die vom Nutzer angelegten Räume (geordnete Liste). */
object RoomStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences("rooms", Context.MODE_PRIVATE)

    fun getAll(context: Context): List<String> {
        val raw = prefs(context).getString("rooms", "[]")!!
        val result = mutableListOf<String>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) result.add(arr.getString(i))
        }
        return result
    }

    fun add(context: Context, room: String): Boolean {
        val name = room.trim()
        if (name.isEmpty()) return false
        val rooms = getAll(context).toMutableList()
        if (rooms.any { it.equals(name, ignoreCase = true) }) return false
        rooms.add(name)
        save(context, rooms)
        return true
    }

    fun delete(context: Context, room: String) {
        val rooms = getAll(context).toMutableList()
        rooms.remove(room)
        save(context, rooms)
    }

    private fun save(context: Context, rooms: List<String>) {
        val arr = JSONArray()
        rooms.forEach { arr.put(it) }
        prefs(context).edit().putString("rooms", arr.toString()).apply()
    }
}
