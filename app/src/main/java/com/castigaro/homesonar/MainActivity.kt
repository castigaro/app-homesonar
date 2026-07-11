package com.castigaro.homesonar

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.homesonar.data.AppSettings
import com.castigaro.homesonar.data.Item
import com.castigaro.homesonar.data.ItemStore
import com.castigaro.homesonar.databinding.ActivityMainBinding
import com.castigaro.homesonar.databinding.DialogEditItemBinding
import com.castigaro.homesonar.util.Photos
import com.google.android.material.snackbar.Snackbar
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ItemAdapter
    private var pendingPhoto: File? = null

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val photo = pendingPhoto
        pendingPhoto = null
        if (success && photo != null && photo.exists()) {
            Photos.downscale(photo)
            startActivity(
                Intent(this, AnalyzeActivity::class.java)
                    .putExtra(AnalyzeActivity.EXTRA_PHOTO_PATH, photo.absolutePath)
            )
        } else {
            photo?.delete()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = ItemAdapter(
            onClick = { item -> showEditDialog(item) },
            onLongClick = { item -> confirmDelete(item) },
        )
        binding.itemList.layoutManager = LinearLayoutManager(this)
        binding.itemList.adapter = adapter

        binding.fabCapture.setOnClickListener {
            if (!requireApiKey()) return@setOnClickListener
            val photo = Photos.newPhotoFile(this)
            pendingPhoto = photo
            takePicture.launch(Photos.uriFor(this, photo))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = ItemStore.getAll(this)
        adapter.submit(items)
        binding.emptyState.visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun requireApiKey(): Boolean {
        if (AppSettings.isConfigured(this)) return true
        Snackbar.make(binding.root, R.string.no_api_key, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
        return false
    }

    private fun showEditDialog(item: Item) {
        val dialogBinding = DialogEditItemBinding.inflate(layoutInflater)
        dialogBinding.inputName.setText(item.name)
        dialogBinding.inputLocation.setText(item.location)
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_item)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                item.name = dialogBinding.inputName.text.toString().trim()
                item.location = dialogBinding.inputLocation.text.toString().trim()
                ItemStore.update(this, item)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(item: Item) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_item)
            .setMessage(getString(R.string.delete_item_message, item.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                ItemStore.delete(this, item)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_find -> {
            if (requireApiKey()) {
                if (ItemStore.getAll(this).isEmpty()) {
                    Snackbar.make(binding.root, R.string.no_items_yet, Snackbar.LENGTH_LONG).show()
                } else {
                    startActivity(Intent(this, FindActivity::class.java))
                }
            }
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
