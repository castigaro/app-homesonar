package com.castigaro.homesonar.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * Einfacher JSON-Datei-Speicher für die Gegenstände. Für die erwartete
 * Datenmenge (Haushalts-Inventar) ist eine Datei robuster und schlanker
 * als eine Datenbank.
 */
object ItemStore {

    private var items: MutableList<Item>? = null

    /** Nur für Tests: verwirft den In-Memory-Cache, erzwingt Neuladen. */
    internal fun resetForTest() {
        items = null
    }

    private fun file(context: Context): File = File(context.filesDir, "items.json")

    private fun load(context: Context): MutableList<Item> {
        items?.let { return it }
        val loaded = mutableListOf<Item>()
        val f = file(context)
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    loaded.add(Item.fromJson(arr.getJSONObject(i)))
                }
            }
        }
        items = loaded
        return loaded
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        load(context).forEach { arr.put(it.toJson()) }
        file(context).writeText(arr.toString())
    }

    fun getAll(context: Context): List<Item> =
        load(context).sortedByDescending { it.createdAt }

    fun add(context: Context, item: Item) {
        load(context).add(item)
        save(context)
    }

    fun update(context: Context, item: Item) {
        val list = load(context)
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item
        save(context)
    }

    fun delete(context: Context, item: Item) {
        val list = load(context)
        list.removeAll { it.id == item.id }
        save(context)
        deletePhotoIfOrphaned(context, item.photoPath)
    }

    /**
     * Löscht eine Fotodatei, wenn kein Gegenstand sie (mehr) referenziert —
     * mehrere Gegenstände aus einer Analyse teilen sich dieselbe Datei.
     */
    fun deletePhotoIfOrphaned(context: Context, path: String?) {
        if (path == null) return
        if (load(context).none { it.photoPath == path }) {
            runCatching { File(path).delete() }
        }
    }
}
