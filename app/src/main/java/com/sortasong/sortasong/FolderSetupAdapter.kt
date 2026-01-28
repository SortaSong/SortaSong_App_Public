package com.sortasong.sortasong

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sortasong.sortasong.databinding.ItemFolderStatusBinding

class FolderSetupAdapter(
    private val folders: List<GameFolderStatus>
) : RecyclerView.Adapter<FolderSetupAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFolderStatusBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFolderStatusBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]

        holder.binding.gameName.text = folder.gameName
        holder.binding.folderName.text = folder.folderName

        if (folder.exists) {
            // Folder exists - show as found
            holder.binding.statusText.text = "✓ Vorhanden"
            holder.binding.statusText.setTextColor(Color.parseColor("#4CAF50")) // Green
            holder.binding.createCheckbox.isEnabled = false
            holder.binding.createCheckbox.isChecked = false
            holder.binding.createCheckbox.alpha = 0.3f
            holder.itemView.setBackgroundColor(Color.parseColor("#1B5E20")) // Dark green background
        } else {
            // Folder missing - allow creation
            holder.binding.statusText.text = "✗ Fehlt"
            holder.binding.statusText.setTextColor(Color.parseColor("#F44336")) // Red
            holder.binding.createCheckbox.isEnabled = true
            holder.binding.createCheckbox.isChecked = folder.shouldCreate
            holder.binding.createCheckbox.alpha = 1.0f
            holder.itemView.setBackgroundColor(Color.parseColor("#424242")) // Dark gray background

            holder.binding.createCheckbox.setOnCheckedChangeListener { _, isChecked ->
                folder.shouldCreate = isChecked
            }
        }
    }

    override fun getItemCount() = folders.size
}
