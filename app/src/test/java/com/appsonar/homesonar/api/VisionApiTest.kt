package com.appsonar.homesonar.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VisionApiTest {

    @Test
    fun `extractJson liest reines JSON`() {
        assertEquals(2, VisionApi.extractJson("""{"match": 2}""").getInt("match"))
    }

    @Test
    fun `extractJson toleriert Markdown-Zaeune`() {
        val text = "```json\n{\"match\": 3}\n```"
        assertEquals(3, VisionApi.extractJson(text).getInt("match"))
    }

    @Test
    fun `extractJson toleriert Fliesstext drumherum`() {
        val text = "Hier ist das Ergebnis: {\"objects\":[{\"name\":\"Schere\"}]} — fertig."
        val json = VisionApi.extractJson(text)
        assertEquals("Schere", json.getJSONArray("objects").getJSONObject(0).getString("name"))
    }

    @Test
    fun `extractJson wirft bei Antwort ohne JSON`() {
        assertThrows(VisionApi.ApiException::class.java) {
            VisionApi.extractJson("Tut mir leid, das kann ich nicht erkennen.")
        }
    }
}
