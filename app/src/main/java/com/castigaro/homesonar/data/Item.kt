package com.castigaro.homesonar.data

import org.json.JSONObject
import java.util.UUID

data class Item(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var location: String,
    val photoPath: String?,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("location", location)
        put("photoPath", photoPath ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Item = Item(
            id = o.getString("id"),
            name = o.getString("name"),
            location = o.optString("location", ""),
            photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath"),
            createdAt = o.optLong("createdAt", 0L),
        )
    }
}
