package com.sortasong.sortasong

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sortasong.sortasong.data.FileAvailability
import com.sortasong.sortasong.databinding.ActivityMissingTracksBinding

class MissingTracksActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOLDER_NAME = "folder_name"
        const val EXTRA_GAME_NAME = "game_name"
    }

    private lateinit var binding: ActivityMissingTracksBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMissingTracksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: ""
        val gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: ""

        binding.gameNameText.text = gameName

        // Get missing tracks for this game
        val allTracks = GameRepository.tracksByFolder[folderName] ?: emptyList()
        val missingTracks = allTracks.filter {
            it.fileAvailability == FileAvailability.UNAVAILABLE
        }

        // Build text list: "Artist - Song (track_nr)"
        val missingTracksList = buildString {
            append(getString(R.string.missing_tracks_header, missingTracks.size))
            missingTracks.sortedBy { it.trackNr }.forEach { track ->
                append("${track.artist} - ${track.song} (${track.trackNr})\n")
            }
        }

        binding.missingTracksText.text = missingTracksList

        // Copy to clipboard button
        binding.copyToClipboardButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.missing_tracks_clipboard_label), missingTracksList)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.missing_tracks_copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }

        // Close button
        binding.closeButton.setOnClickListener {
            finish()
        }
    }
}
