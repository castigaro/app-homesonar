package com.castigaro.homesonar

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.homesonar.api.VisionApi
import com.castigaro.homesonar.data.AppSettings
import com.castigaro.homesonar.data.Item
import com.castigaro.homesonar.data.ItemStore
import com.castigaro.homesonar.data.RoomStore
import com.castigaro.homesonar.databinding.ActivityAnalyzeBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

/**
 * Zeigt das aufgenommene Foto und speichert Gegenstände mit Ort/Raum.
 * Mit API-Key wird das Foto analysiert; ohne Key wird ein manueller
 * Eintrag angelegt und das Foto als "nicht analysiert" markiert —
 * es lässt sich später über "Fotos indizieren" nachholen.
 */
class AnalyzeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_PATH = "photoPath"
        const val EXTRA_ROOM = "room"
    }

    private lateinit var binding: ActivityAnalyzeBinding
    private lateinit var adapter: DetectedAdapter
    private lateinit var photo: File
    private var room: String = ""
    private var analyzedByAi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyzeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val path = intent.getStringExtra(EXTRA_PHOTO_PATH)
        if (path == null) { finish(); return }
        photo = File(path)
        room = intent.getStringExtra(EXTRA_ROOM) ?: ""
        if (room.isNotBlank()) {
            supportActionBar?.subtitle = getString(R.string.room_subtitle, room)
        }

        binding.photoPreview.setImageBitmap(BitmapFactory.decodeFile(photo.absolutePath))

        val locationSuggestions = ItemStore.getAll(this)
            .map { it.location.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()
        adapter = DetectedAdapter(locationSuggestions)
        binding.detectedList.layoutManager = LinearLayoutManager(this)
        binding.detectedList.adapter = adapter

        binding.buttonSave.setOnClickListener { saveSelected() }
        binding.buttonRetry.setOnClickListener { analyze() }

        if (AppSettings.isConfigured(this)) {
            analyze()
        } else {
            setUpOfflineEntry()
        }
    }

    /** Ohne API-Key: ein manueller Eintrag, Foto bleibt unanalysiert. */
    private fun setUpOfflineEntry() {
        analyzedByAi = false
        binding.progress.visibility = View.GONE
        binding.statusText.setText(R.string.offline_capture_hint)
        adapter.submit(listOf(""))
        binding.buttonSave.isEnabled = true
    }

    private fun analyze() {
        binding.progress.visibility = View.VISIBLE
        binding.statusText.setText(R.string.analyzing)
        binding.buttonRetry.visibility = View.GONE
        binding.buttonSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val objects = VisionApi.analyze(this@AnalyzeActivity, photo)
                analyzedByAi = true
                binding.progress.visibility = View.GONE
                if (objects.isEmpty()) {
                    binding.statusText.setText(R.string.nothing_detected)
                    adapter.submit(listOf(""))
                    binding.buttonSave.isEnabled = true
                    binding.buttonRetry.visibility = View.VISIBLE
                } else {
                    binding.statusText.text =
                        getString(R.string.detected_count, objects.size)
                    adapter.submit(objects.map { it.name })
                    binding.buttonSave.isEnabled = true
                }
            } catch (e: Exception) {
                analyzedByAi = false
                binding.progress.visibility = View.GONE
                binding.statusText.text =
                    getString(R.string.analyze_error_offline, e.message ?: "")
                adapter.submit(listOf(""))
                binding.buttonSave.isEnabled = true
                binding.buttonRetry.visibility = View.VISIBLE
            }
        }
    }

    private fun saveSelected() {
        val selected = adapter.getSelected()
        if (selected.isEmpty()) {
            // Ohne Namen trotzdem erlauben: das Foto wird als unbenannter
            // Eintrag abgelegt und kann später indiziert werden.
            ItemStore.add(
                this,
                Item(
                    name = getString(R.string.unnamed_item),
                    location = "",
                    room = room,
                    photoPath = photo.absolutePath,
                    analyzed = analyzedByAi,
                )
            )
            Snackbar.make(binding.root, getString(R.string.saved_count, 1), Snackbar.LENGTH_SHORT).show()
            finish()
            return
        }
        selected.forEach { entry ->
            ItemStore.add(
                this,
                Item(
                    name = entry.name,
                    location = entry.location,
                    room = room,
                    photoPath = photo.absolutePath,
                    analyzed = analyzedByAi,
                )
            )
        }
        if (room.isNotBlank()) RoomStore.add(this, room)
        Snackbar.make(binding.root, getString(R.string.saved_count, selected.size), Snackbar.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
