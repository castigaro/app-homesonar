package com.appsonar.homesonar

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.appsonar.homesonar.api.VisionApi
import com.appsonar.homesonar.data.Item
import com.appsonar.homesonar.data.ItemStore
import com.appsonar.homesonar.databinding.ActivityIndexBinding
import kotlinx.coroutines.launch
import java.io.File

/**
 * Indiziert nachträglich alle Fotos, die ohne API-Key aufgenommen wurden:
 * Für jedes unanalysierte Foto wird die Erkennung ausgeführt. Der erste
 * erkannte Name ersetzt den Platzhalternamen des Eintrags (falls dieser
 * unbenannt ist), weitere erkannte Gegenstände werden als neue Einträge
 * mit demselben Foto, Raum und Ort angelegt.
 */
class IndexActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIndexBinding
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIndexBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val pending = pendingItems()
        binding.statusText.text = getString(R.string.index_pending, pending.size)
        binding.buttonStart.isEnabled = pending.isNotEmpty()
        binding.buttonStart.setOnClickListener { startIndexing() }
    }

    private fun pendingItems(): List<Item> =
        ItemStore.getAll(this).filter { !it.analyzed && it.photoPath != null }

    private fun startIndexing() {
        if (running) return
        running = true
        binding.buttonStart.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.logText.text = ""

        lifecycleScope.launch {
            val pending = pendingItems()
            var done = 0
            var created = 0
            for (item in pending) {
                binding.statusText.text =
                    getString(R.string.index_progress, done + 1, pending.size)
                try {
                    val photo = File(item.photoPath!!)
                    if (!photo.exists()) {
                        item.analyzed = true
                        ItemStore.update(this@IndexActivity, item)
                        done++
                        continue
                    }
                    val objects = VisionApi.analyze(this@IndexActivity, photo)
                    val names = objects.map { it.name }

                    val unnamed = item.name.isBlank() ||
                        item.name == getString(R.string.unnamed_item)
                    if (names.isNotEmpty() && unnamed) {
                        item.name = names.first()
                    }
                    item.analyzed = true
                    ItemStore.update(this@IndexActivity, item)

                    val extraNames = if (unnamed) names.drop(1) else names
                    extraNames.forEach { name ->
                        // Duplikate zum selben Foto vermeiden.
                        val exists = ItemStore.getAll(this@IndexActivity).any {
                            it.photoPath == item.photoPath &&
                                it.name.equals(name, ignoreCase = true)
                        }
                        if (!exists) {
                            ItemStore.add(
                                this@IndexActivity,
                                Item(
                                    name = name,
                                    location = item.location,
                                    room = item.room,
                                    photoPath = item.photoPath,
                                    analyzed = true,
                                )
                            )
                            created++
                        }
                    }
                    appendLog(getString(R.string.index_log_ok, names.joinToString(", ")))
                } catch (e: Exception) {
                    appendLog(getString(R.string.index_log_error, e.message ?: ""))
                    break
                }
                done++
            }
            binding.progress.visibility = View.GONE
            binding.statusText.text = getString(R.string.index_done, done, created)
            binding.buttonStart.isEnabled = pendingItems().isNotEmpty()
            running = false
        }
    }

    private fun appendLog(line: String) {
        binding.logText.text = "${binding.logText.text}$line\n"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
