package com.appsonar.homesonar.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ItemTest {

    @Test
    fun `JSON-Roundtrip erhaelt alle Felder`() {
        val item = Item(
            name = "Schere",
            location = "Schublade",
            room = "Küche",
            photoPath = "/pfad/foto.jpg",
            analyzed = false,
            createdAt = 1234L,
        )
        val loaded = Item.fromJson(item.toJson())
        assertEquals(item.id, loaded.id)
        assertEquals("Schere", loaded.name)
        assertEquals("Schublade", loaded.location)
        assertEquals("Küche", loaded.room)
        assertEquals("/pfad/foto.jpg", loaded.photoPath)
        assertEquals(false, loaded.analyzed)
        assertEquals(1234L, loaded.createdAt)
    }

    @Test
    fun `Altdaten aus Version 1_0 bekommen sinnvolle Defaults`() {
        // Version 1.0 kannte weder "room" noch "analyzed".
        val legacy = JSONObject(
            """{"id":"abc","name":"Hammer","location":"Keller","photoPath":null}"""
        )
        val loaded = Item.fromJson(legacy)
        assertEquals("", loaded.room)
        assertTrue("Bestandsdaten stammen immer aus einer Analyse", loaded.analyzed)
        assertNull(loaded.photoPath)
    }
}
