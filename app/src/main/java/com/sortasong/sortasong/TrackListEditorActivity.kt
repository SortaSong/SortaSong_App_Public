package com.sortasong.sortasong

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sortasong.sortasong.data.SupabaseClient
import com.sortasong.sortasong.databinding.ActivityTrackListEditorBinding
import com.sortasong.sortasong.databinding.DialogTrackEditBinding
import com.sortasong.sortasong.databinding.ItemTrackRowBinding
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Activity for editing the tracklist of a custom game.
 * Landscape orientation, simple row-based display.
 */
class TrackListEditorActivity : AppCompatActivity() {

    companion object {
        // Shared state for passing tracks between activities
        var tracksToEdit = mutableListOf<EditableTrack>()
    }

    private lateinit var binding: ActivityTrackListEditorBinding
    private lateinit var adapter: TrackRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityTrackListEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        
        setupUI()
    }

    private fun setupUI() {
        adapter = TrackRowAdapter(
            tracks = tracksToEdit,
            onEdit = { position -> showEditDialog(position) },
            onDelete = { position -> deleteTrack(position) }
        )
        
        binding.tracksRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.tracksRecyclerView.adapter = adapter
        
        binding.backButton.setOnClickListener { 
            setResult(RESULT_OK)
            finish() 
        }
        
        binding.saveButton.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
        
        binding.addButton.setOnClickListener { showAddTrackDialog() }
        
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (tracksToEdit.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.tracksRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateText.visibility = View.GONE
            binding.tracksRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showAddTrackDialog() {
        val options = arrayOf(
            getString(R.string.add_track_search_all),
            getString(R.string.add_track_search_local),
            getString(R.string.add_track_manual)
        )
        
        AlertDialog.Builder(this)
            .setTitle(R.string.add_track_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSearchDialog(localOnly = false)
                    1 -> showSearchDialog(localOnly = true)
                    2 -> showEditDialog(-1) // -1 means new track
                }
            }
            .setNegativeButton(R.string.track_edit_cancel, null)
            .show()
    }

    private fun showSearchDialog(localOnly: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_search_tracks, null)
        val searchInput = dialogView.findViewById<android.widget.EditText>(R.id.searchInput)
        val resultsRecycler = dialogView.findViewById<RecyclerView>(R.id.resultsRecyclerView)
        val noResultsText = dialogView.findViewById<android.widget.TextView>(R.id.noResultsText)
        val minCharsText = dialogView.findViewById<android.widget.TextView>(R.id.minCharsText)
        
        resultsRecycler.layoutManager = LinearLayoutManager(this)
        
        var searchJob: Job? = null
        val searchResults = mutableListOf<TrackEntry>()
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.search_tracks_title)
            .setView(dialogView)
            .setNegativeButton(R.string.track_edit_cancel, null)
            .create()
        
        val resultsAdapter = SearchResultAdapter(searchResults) { track ->
            // Add selected track
            tracksToEdit.add(EditableTrack(
                artist = track.artist,
                title = track.song,
                releaseDate = track.releaseDate,
                year = track.releaseYear,
                originalFileName = ""
            ))
            adapter.notifyItemInserted(tracksToEdit.size - 1)
            updateEmptyState()
            dialog.dismiss()
            Toast.makeText(this, "Track hinzugefügt", Toast.LENGTH_SHORT).show()
        }
        resultsRecycler.adapter = resultsAdapter
        
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                
                searchJob?.cancel()
                
                if (query.length < 3) {
                    searchResults.clear()
                    resultsAdapter.notifyDataSetChanged()
                    noResultsText.visibility = View.GONE
                    minCharsText.visibility = View.VISIBLE
                    resultsRecycler.visibility = View.GONE
                    return
                }
                
                minCharsText.visibility = View.GONE
                
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
                    
                    val results = withContext(Dispatchers.IO) {
                        if (localOnly) {
                            searchLocalTracks(query)
                        } else {
                            searchAllTracks(query)
                        }
                    }
                    
                    searchResults.clear()
                    searchResults.addAll(results)
                    resultsAdapter.notifyDataSetChanged()
                    
                    if (results.isEmpty()) {
                        noResultsText.visibility = View.VISIBLE
                        resultsRecycler.visibility = View.GONE
                    } else {
                        noResultsText.visibility = View.GONE
                        resultsRecycler.visibility = View.VISIBLE
                    }
                }
            }
        })
        
        dialog.show()
    }

    private fun searchLocalTracks(query: String): List<TrackEntry> {
        val lowerQuery = query.lowercase()
        return GameRepository.tracksByFolder.values.flatten()
            .filter { track ->
                track.artist.lowercase().contains(lowerQuery) ||
                track.song.lowercase().contains(lowerQuery)
            }
            .take(50)
    }

    private suspend fun searchAllTracks(query: String): List<TrackEntry> {
        // Search online from Supabase tracks table
        return try {
            if (!SupabaseClient.isConfigured()) {
                return searchLocalTracks(query)
            }
            
            val searchPattern = "*$query*"
            val tracks = SupabaseClient.client.postgrest
                .from("tracks")
                .select {
                    filter {
                        or {
                            ilike("artist", searchPattern)
                            ilike("song", searchPattern)
                        }
                    }
                    limit(50)
                }
                .decodeList<OnlineTrack>()
            
            tracks.map { track ->
                TrackEntry(
                    trackId = track.id,
                    trackNr = "",
                    song = track.song,
                    artist = track.artist,
                    releaseDate = track.releaseDate ?: "",
                    releaseYear = extractYearFromDate(track.releaseDate),
                    folderName = ""
                )
            }
        } catch (e: Exception) {
            Log.e("TrackSearch", "Error searching online tracks", e)
            emptyList()
        }
    }
    
    private fun extractYearFromDate(dateStr: String?): Int? {
        if (dateStr == null) return null
        val match = Regex("""(\d{4})""").find(dateStr)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
    
    @Serializable
    data class OnlineTrack(
        val id: Int,
        val song: String,
        val artist: String,
        @SerialName("release_date")
        val releaseDate: String?
    )

    private fun showEditDialog(position: Int) {
        val isNew = position < 0
        val track = if (isNew) EditableTrack() else tracksToEdit[position]
        
        val dialogBinding = DialogTrackEditBinding.inflate(layoutInflater)
        
        dialogBinding.artistInput.setText(track.artist)
        dialogBinding.titleInput.setText(track.title)
        dialogBinding.releaseDateInput.setText(track.releaseDate)
        dialogBinding.yearInput.setText(track.year?.toString() ?: "")
        
        dialogBinding.swapButton.setOnClickListener {
            val tempArtist = dialogBinding.artistInput.text.toString()
            dialogBinding.artistInput.setText(dialogBinding.titleInput.text.toString())
            dialogBinding.titleInput.setText(tempArtist)
        }
        
        AlertDialog.Builder(this)
            .setTitle(if (isNew) R.string.track_edit_title_new else R.string.track_edit_title_edit)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.track_edit_save) { _, _ ->
                val editedTrack = EditableTrack(
                    artist = dialogBinding.artistInput.text.toString(),
                    title = dialogBinding.titleInput.text.toString(),
                    releaseDate = dialogBinding.releaseDateInput.text.toString(),
                    year = dialogBinding.yearInput.text.toString().toIntOrNull(),
                    originalFileName = track.originalFileName
                )
                
                if (isNew) {
                    tracksToEdit.add(editedTrack)
                    adapter.notifyItemInserted(tracksToEdit.size - 1)
                } else {
                    tracksToEdit[position] = editedTrack
                    adapter.notifyItemChanged(position)
                }
                updateEmptyState()
            }
            .setNegativeButton(R.string.track_edit_cancel, null)
            .show()
    }

    private fun deleteTrack(position: Int) {
        tracksToEdit.removeAt(position)
        adapter.notifyItemRemoved(position)
        adapter.notifyItemRangeChanged(position, tracksToEdit.size - position)
        updateEmptyState()
    }

    override fun onBackPressed() {
        setResult(RESULT_OK)
        super.onBackPressed()
    }

    // Simple row adapter for track list
    inner class TrackRowAdapter(
        private val tracks: List<EditableTrack>,
        private val onEdit: (Int) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<TrackRowAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemTrackRowBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTrackRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = tracks[position]
            
            holder.binding.artistText.text = track.artist.ifBlank { "-" }
            holder.binding.titleText.text = track.title.ifBlank { "-" }
            holder.binding.dateText.text = track.releaseDate.ifBlank { "-" }
            holder.binding.yearText.text = track.year?.toString() ?: "-"
            
            // Click anywhere except delete opens edit
            holder.binding.root.setOnClickListener { 
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onEdit(pos) 
            }
            holder.binding.deleteButton.setOnClickListener { 
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onDelete(pos) 
            }
        }

        override fun getItemCount() = tracks.size
    }

    // Adapter for search results
    inner class SearchResultAdapter(
        private val results: List<TrackEntry>,
        private val onSelect: (TrackEntry) -> Unit
    ) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemTrackRowBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTrackRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            // Hide delete button for search results
            binding.deleteButton.visibility = View.GONE
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = results[position]
            
            holder.binding.artistText.text = track.artist
            holder.binding.titleText.text = track.song
            holder.binding.dateText.text = track.releaseDate
            holder.binding.yearText.text = track.releaseYear.toString()
            
            holder.binding.root.setOnClickListener { onSelect(track) }
        }

        override fun getItemCount() = results.size
    }
}
