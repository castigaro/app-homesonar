package com.castigaro.homesonar

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.homesonar.api.VisionApi
import com.castigaro.homesonar.data.Item
import com.castigaro.homesonar.data.ItemStore
import com.castigaro.homesonar.databinding.ActivityAnalyzeBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

/**
 * Zeigt das aufgenommene Foto, lässt es vom KI-Modell analysieren und
 * speichert die ausgewählten Gegenstände mit ihrem Ort.
 */
class AnalyzeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_PATH = "photoPath"
    }

    private lateinit var binding: ActivityAnalyzeBinding
    private lateinit var adapter: DetectedAdapter
    private lateinit var photo: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyzeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val path = intent.getStringExtra(EXTRA_PHOTO_PATH)
        if (path == null) { finish(); return }
        photo = File(path)

        binding.photoPreview.setImageBitmap(BitmapFactory.decodeFile(photo.absolutePath))

        adapter = DetectedAdapter()
        binding.detectedList.layoutManager = LinearLayoutManager(this)
        binding.detectedList.adapter = adapter

        binding.buttonSave.setOnClickListener { saveSelected() }
        binding.buttonRetry.setOnClickListener { analyze() }

        analyze()
    }

    private fun analyze() {
        binding.progress.visibility = View.VISIBLE
        binding.statusText.setText(R.string.analyzing)
        binding.buttonRetry.visibility = View.GONE
        binding.buttonSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val objects = VisionApi.analyze(this@AnalyzeActivity, photo)
                binding.progress.visibility = View.GONE
                if (objects.isEmpty()) {
                    binding.statusText.setText(R.string.nothing_detected)
                    binding.buttonRetry.visibility = View.VISIBLE
                } else {
                    binding.statusText.text =
                        getString(R.string.detected_count, objects.size)
                    adapter.submit(objects.map { it.name })
                    binding.buttonSave.isEnabled = true
                }
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                binding.statusText.text =
                    getString(R.string.analyze_error, e.message ?: "")
                binding.buttonRetry.visibility = View.VISIBLE
            }
        }
    }

    private fun saveSelected() {
        val selected = adapter.getSelected()
        if (selected.isEmpty()) {
            Snackbar.make(binding.root, R.string.nothing_selected, Snackbar.LENGTH_SHORT).show()
            return
        }
        selected.forEach { entry ->
            ItemStore.add(
                this,
                Item(
                    name = entry.name,
                    location = entry.location,
                    photoPath = photo.absolutePath,
                )
            )
        }
        Snackbar.make(binding.root, getString(R.string.saved_count, selected.size), Snackbar.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
