package com.castigaro.homesonar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.castigaro.homesonar.databinding.RowDetectedBinding

/**
 * Liste der erkannten Gegenstände: pro Zeile Checkbox, editierbarer Name
 * und editierbarer Ort.
 */
class DetectedAdapter : RecyclerView.Adapter<DetectedAdapter.Holder>() {

    data class Entry(var name: String, var location: String = "", var checked: Boolean = true)

    private val entries = mutableListOf<Entry>()

    fun submit(names: List<String>) {
        entries.clear()
        names.forEach { entries.add(Entry(name = it)) }
        notifyDataSetChanged()
    }

    fun getSelected(): List<Entry> =
        entries.filter { it.checked && it.name.isNotBlank() }

    class Holder(val binding: RowDetectedBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowDetectedBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        holder.binding.checkSelected.setOnCheckedChangeListener(null)
        holder.binding.checkSelected.isChecked = entry.checked
        holder.binding.checkSelected.setOnCheckedChangeListener { _, checked ->
            entry.checked = checked
        }

        if (holder.binding.inputName.text.toString() != entry.name) {
            holder.binding.inputName.setText(entry.name)
        }
        if (holder.binding.inputLocation.text.toString() != entry.location) {
            holder.binding.inputLocation.setText(entry.location)
        }
        holder.binding.inputName.doAfterTextChanged { text ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) entries[pos].name = text.toString()
        }
        holder.binding.inputLocation.doAfterTextChanged { text ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) entries[pos].location = text.toString()
        }
    }
}
