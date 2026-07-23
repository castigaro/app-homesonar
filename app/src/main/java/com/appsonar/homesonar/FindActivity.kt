package com.appsonar.homesonar

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.appsonar.homesonar.api.VisionApi
import com.appsonar.homesonar.data.ItemStore
import com.appsonar.homesonar.databinding.ActivityFindBinding
import com.appsonar.homesonar.util.Photos
import kotlinx.coroutines.launch
import java.io.File

/**
 * "Wo gehört das hin?": Foto eines Gegenstands aufnehmen, gegen die
 * gespeicherten Gegenstände matchen und den hinterlegten Ort anzeigen.
 */
class FindActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFindBinding
    private var photo: File? = null

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = photo
        if (success && file != null && file.exists()) {
            Photos.downscale(file)
            binding.photoPreview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            find(file)
        } else {
            file?.delete()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFindBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonAgain.setOnClickListener { capture() }
        capture()
    }

    private fun capture() {
        binding.resultCard.visibility = View.GONE
        binding.buttonAgain.visibility = View.GONE
        val file = Photos.newPhotoFile(this)
        photo = file
        takePicture.launch(Photos.uriFor(this, file))
    }

    private fun find(file: File) {
        binding.progress.visibility = View.VISIBLE
        binding.statusText.setText(R.string.finding)

        lifecycleScope.launch {
            val items = ItemStore.getAll(this@FindActivity)
            try {
                val index = VisionApi.findMatch(this@FindActivity, file, items)
                binding.progress.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE
                binding.buttonAgain.visibility = View.VISIBLE
                if (index != null) {
                    val item = items[index]
                    binding.statusText.setText(R.string.found)
                    binding.resultName.text = item.name
                    binding.resultLocation.text = item.location.ifBlank {
                        getString(R.string.no_location)
                    }
                } else {
                    binding.statusText.setText(R.string.not_found)
                    binding.resultName.setText(R.string.unknown_item)
                    binding.resultLocation.setText(R.string.not_found_hint)
                }
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                binding.buttonAgain.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.analyze_error, e.message ?: "")
            } finally {
                // Suchfotos werden nicht dauerhaft gebraucht.
                file.delete()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
