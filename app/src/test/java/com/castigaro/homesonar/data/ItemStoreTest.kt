package com.castigaro.homesonar.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ItemStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        ItemStore.resetForTest()
        File(context.filesDir, "items.json").delete()
    }

    private fun item(name: String, createdAt: Long, photoPath: String? = null) =
        Item(name = name, location = "Ort", photoPath = photoPath, createdAt = createdAt)

    @Test
    fun `getAll sortiert neueste zuerst`() {
        ItemStore.add(context, item("alt", createdAt = 1))
        ItemStore.add(context, item("neu", createdAt = 2))
        assertEquals(listOf("neu", "alt"), ItemStore.getAll(context).map { it.name })
    }

    @Test
    fun `Daten ueberleben einen Neustart (Persistenz-Roundtrip)`() {
        val original = item("Schere", createdAt = 1)
        ItemStore.add(context, original)

        ItemStore.resetForTest() // simuliert App-Neustart: von Datei laden

        val loaded = ItemStore.getAll(context).single()
        assertEquals(original.id, loaded.id)
        assertEquals("Schere", loaded.name)
    }

    @Test
    fun `update ersetzt den Eintrag mit gleicher Id`() {
        val original = item("Tippfehler", createdAt = 1)
        ItemStore.add(context, original)
        original.name = "Korrigiert"
        ItemStore.update(context, original)

        ItemStore.resetForTest()
        assertEquals("Korrigiert", ItemStore.getAll(context).single().name)
    }

    @Test
    fun `geteiltes Foto wird erst geloescht wenn kein Item es mehr referenziert`() {
        val photo = File(context.filesDir, "geteilt.jpg").apply { writeBytes(byteArrayOf(1)) }
        val a = item("A", createdAt = 1, photoPath = photo.absolutePath)
        val b = item("B", createdAt = 2, photoPath = photo.absolutePath)
        ItemStore.add(context, a)
        ItemStore.add(context, b)

        ItemStore.delete(context, a)
        assertTrue("Foto darf nicht weg sein — B referenziert es noch", photo.exists())

        ItemStore.delete(context, b)
        assertFalse("Foto muss weg sein — niemand referenziert es mehr", photo.exists())
    }

    @Test
    fun `deletePhotoIfOrphaned respektiert bestehende Referenzen`() {
        val photo = File(context.filesDir, "einzeln.jpg").apply { writeBytes(byteArrayOf(1)) }
        ItemStore.add(context, item("A", createdAt = 1, photoPath = photo.absolutePath))

        ItemStore.deletePhotoIfOrphaned(context, photo.absolutePath)
        assertTrue(photo.exists())

        ItemStore.delete(context, ItemStore.getAll(context).single())
        assertFalse(photo.exists())
    }
}
