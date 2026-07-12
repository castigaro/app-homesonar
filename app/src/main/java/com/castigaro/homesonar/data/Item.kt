package com.castigaro.homesonar.data

import org.json.JSONObject
import java.util.UUID

data class Item(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var location: String,
    var room: String = "",
    var photoPath: String?,
    /** false = Foto wurde noch nicht von der KI analysiert (Offline-Aufnahme). */
    var analyzed: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("location", location)
        put("room", room)
        put("photoPath", photoPath ?: JSONObject.NULL)
        put("analyzed", analyzed)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Item = Item(
            id = o.getString("id"),
            name = o.getString("name"),
            location = o.optString("location", ""),
            room = o.optString("room", ""),
            photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath"),
            // Bestandsdaten aus Version 1.0 stammen immer aus einer Analyse.
            analyzed = o.optBoolean("analyzed", true),
            createdAt = o.optLong("createdAt", 0L),
        )
    }
}
