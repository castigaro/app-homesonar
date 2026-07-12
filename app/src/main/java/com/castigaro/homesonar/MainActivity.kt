package com.castigaro.homesonar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.homesonar.data.AppSettings
import com.castigaro.homesonar.data.Item
import com.castigaro.homesonar.data.ItemStore
import com.castigaro.homesonar.data.RoomStore
import com.castigaro.homesonar.databinding.ActivityMainBinding
import com.castigaro.homesonar.databinding.DialogEditItemBinding
import com.castigaro.homesonar.util.Photos
import com.castigaro.homesonar.util.UpdateChecker
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ItemAdapter
    private var pendingPhoto: File? = null
    private var selectedRoom: String? = null // null = alle Räume
    private var searchQuery: String = ""

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
                    .putExtra(AnalyzeActivity.EXTRA_ROOM, selectedRoom ?: "")
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

        // Aufnehmen geht auch ohne API-Key — dann ohne automatische Erkennung.
        binding.fabCapture.setOnClickListener {
            val photo = Photos.newPhotoFile(this)
            pendingPhoto = photo
            takePicture.launch(Photos.uriFor(this, photo))
        }

        // Stille Prüfung beim Start — zeigt nur etwas, wenn wirklich eine neue Version da ist.
        lifecycleScope.launch {
            UpdateChecker.check()?.let { showUpdateSnackbar(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        renderRoomChips()
        refresh()
        invalidateOptionsMenu()
    }

    // ---- Raum-Filter ----

    private fun renderRoomChips() {
        val group = binding.roomChips
        group.removeAllViews()

        val rooms = RoomStore.getAll(this)
        if (selectedRoom != null && selectedRoom !in rooms) selectedRoom = null

        fun addChip(label: String, room: String?): Chip {
            val chip = Chip(this)
            chip.text = label
            chip.isCheckable = true
            chip.isChecked = selectedRoom == room
            chip.setOnClickListener {
                selectedRoom = room
                renderRoomChips()
                refresh()
            }
            group.addView(chip)
            return chip
        }

        addChip(getString(R.string.all_rooms), null)
        rooms.forEach { room ->
            val chip = addChip(room, room)
            chip.setOnLongClickListener { confirmDeleteRoom(room); true }
        }

        val addChipView = Chip(this)
        addChipView.text = getString(R.string.add_room)
        addChipView.isCheckable = false
        addChipView.setOnClickListener { showAddRoomDialog() }
        group.addView(addChipView)
    }

    private fun showAddRoomDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.room_name_hint)
            setSingleLine()
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(48, 16, 48, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_room_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (RoomStore.add(this, name)) {
                    selectedRoom = name
                    renderRoomChips()
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteRoom(room: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_room)
            .setMessage(getString(R.string.delete_room_message, room))
            .setPositiveButton(R.string.delete) { _, _ ->
                RoomStore.delete(this, room)
                if (selectedRoom == room) selectedRoom = null
                renderRoomChips()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Liste & Suche ----

    private fun refresh() {
        var items = ItemStore.getAll(this)
        selectedRoom?.let { room -> items = items.filter { it.room == room } }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            items = items.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.location.contains(q, ignoreCase = true) ||
                    it.room.contains(q, ignoreCase = true)
            }
        }
        adapter.submit(items)
        binding.emptyState.setText(
            if (searchQuery.isNotBlank() || selectedRoom != null) R.string.empty_filtered
            else R.string.empty_state
        )
        binding.emptyState.visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditDialog(item: Item) {
        val dialogBinding = DialogEditItemBinding.inflate(layoutInflater)

        dialogBinding.inputRoom.setAdapter(
            android.widget.ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                RoomStore.getAll(this),
            )
        )
        dialogBinding.inputLocation.setAdapter(
            android.widget.ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                knownLocations(),
            )
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.edit_item)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                item.name = dialogBinding.inputName.text.toString().trim()
                item.location = dialogBinding.inputLocation.text.toString().trim()
                item.room = dialogBinding.inputRoom.text.toString().trim()
                if (item.room.isNotBlank()) RoomStore.add(this, item.room)
                ItemStore.update(this, item)
                renderRoomChips()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Vorbelegen erst nach dem Anzeigen: nur so hebt das Material-Feld
        // sein schwebendes Label zuverlässig über den vorbefüllten Text.
        dialog.setOnShowListener {
            dialogBinding.inputName.setText(item.name)
            dialogBinding.inputRoom.setText(item.room, false)
            dialogBinding.inputLocation.setText(item.location, false)
        }
        dialog.show()
    }

    /** Alle bisher verwendeten Orte (dedupliziert) als Vorschlagsliste. */
    private fun knownLocations(): List<String> =
        ItemStore.getAll(this)
            .map { it.location.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()

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

    // ---- Update-Prüfung ----

    private fun showUpdateSnackbar(info: UpdateChecker.UpdateInfo) {
        Snackbar.make(
            binding.root,
            getString(R.string.update_available, info.versionName),
            Snackbar.LENGTH_INDEFINITE,
        )
            .setAction(R.string.update_download) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
            }
            .show()
    }

    private fun checkForUpdateManually() {
        lifecycleScope.launch {
            val info = UpdateChecker.check()
            if (info != null) {
                showUpdateSnackbar(info)
            } else {
                Toast.makeText(this@MainActivity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- Menü ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText ?: ""
                refresh()
                return true
            }
        })
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val unanalyzed = ItemStore.getAll(this).count { !it.analyzed && it.photoPath != null }
        val indexItem = menu.findItem(R.id.action_index)
        indexItem.isVisible = unanalyzed > 0
        indexItem.title = getString(R.string.action_index, unanalyzed)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_find -> {
            if (!AppSettings.isConfigured(this)) {
                Snackbar.make(binding.root, R.string.find_needs_key, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_settings) {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                    .show()
            } else if (ItemStore.getAll(this).isEmpty()) {
                Snackbar.make(binding.root, R.string.no_items_yet, Snackbar.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, FindActivity::class.java))
            }
            true
        }
        R.id.action_index -> {
            if (!AppSettings.isConfigured(this)) {
                Snackbar.make(binding.root, R.string.index_needs_key, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_settings) {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                    .show()
            } else {
                startActivity(Intent(this, IndexActivity::class.java))
            }
            true
        }
        R.id.action_help -> {
            startActivity(Intent(this, HelpActivity::class.java))
            true
        }
        R.id.action_check_update -> {
            checkForUpdateManually()
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
