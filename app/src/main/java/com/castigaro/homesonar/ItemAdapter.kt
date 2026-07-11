package com.castigaro.homesonar

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.castigaro.homesonar.data.Item
import com.castigaro.homesonar.databinding.RowItemBinding
import java.io.File

class ItemAdapter(
    private val onClick: (Item) -> Unit,
    private val onLongClick: (Item) -> Unit,
) : RecyclerView.Adapter<ItemAdapter.Holder>() {

    private val items = mutableListOf<Item>()

    fun submit(newItems: List<Item>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class Holder(val binding: RowItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.itemName.text = item.name
        holder.binding.itemLocation.text = item.location.ifBlank {
            holder.binding.root.context.getString(R.string.no_location)
        }

        val photo = item.photoPath?.let { File(it) }
        if (photo != null && photo.exists()) {
            holder.binding.itemThumb.setImageBitmap(
                BitmapFactory.decodeFile(photo.absolutePath)
            )
        } else {
            holder.binding.itemThumb.setImageResource(R.drawable.ic_item_placeholder)
        }

        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.root.setOnLongClickListener { onLongClick(item); true }
    }
}
