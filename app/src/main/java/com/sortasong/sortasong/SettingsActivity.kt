package com.sortasong.sortasong

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.sortasong.sortasong.databinding.ActivitySettingsBinding
import com.sortasong.sortasong.workers.VerificationManager
import com.sortasong.sortasong.workers.VerificationProgress
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    companion object {
        private const val PREFS = "settings"
        private const val KEY_FOLDER_URI = "folder_uri"
    }

    /** ActivityResult‑Launcher für SAF‑Ordnerauswahl */
    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { changeFolder(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show current folder
        val currentFolder = getFolderUri()
        binding.currentFolderText.text = getString(R.string.activity_settings_current_folder_display,
            getString(R.string.activity_settings_current_folder_label),
            currentFolder?.path ?: getString(R.string.activity_settings_not_set))

        // Sync button
        binding.syncButton.setOnClickListener {
            syncData()
        }

        // Change folder button
        binding.changeFolderButton.setOnClickListener {
            folderPicker.launch(null)
        }

        // Rescan folder button
        binding.rescanFolderButton.setOnClickListener {
            rescanFolder()
        }

        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }

        // Verification button - verify missing/unverified tracks
        binding.verifyTracksButton.setOnClickListener {
            startVerification(resetAll = false)
        }

        // Verify all button - reset and verify all tracks
        binding.verifyAllTracksButton.setOnClickListener {
            startVerification(resetAll = true)
        }

        // Cancel verification button
        binding.cancelVerificationButton.setOnClickListener {
            cancelVerification()
        }

        // Observe verification progress
        observeVerificationProgress()
    }

    private fun syncData() {
        Toast.makeText(this, getString(R.string.settings_syncing_data), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = GameRepository.syncFromSupabase(this@SettingsActivity)

            when (result) {
                is LoadResult.Success -> {
                    Log.d("Settings", "Data synced successfully")
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.settings_data_synced_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is LoadResult.Error -> {
                    Log.e("Settings", "Sync failed: ${result.message}")
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.settings_sync_failed, result.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {}
            }

        }
    }

    private fun changeFolder(uri: Uri) {
        // Persist new folder
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs().edit {
            putString(KEY_FOLDER_URI, uri.toString())
        }

        // Launch folder setup activity
        val intent = Intent(this, FolderSetupActivity::class.java)
        intent.putExtra(FolderSetupActivity.EXTRA_FOLDER_URI, uri.toString())
        startActivity(intent)
        finish()
    }

    private fun rescanFolder() {
        val folderUri = getFolderUri()
        if (folderUri == null) {
            Toast.makeText(this, getString(R.string.settings_no_folder_selected), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.settings_scanning_folder), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            // Check which folders exist
            val rootFolder = DocumentFile.fromTreeUri(this@SettingsActivity, folderUri)
            if (rootFolder == null || !rootFolder.isDirectory) {
                Toast.makeText(this@SettingsActivity, getString(R.string.settings_invalid_folder), Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Make sure we have data loaded
            if (GameRepository.games.isEmpty()) {
                // Load from cache first
                GameRepository.loadFromCacheOnly(this@SettingsActivity)
            }

            // Get all games from database (not filtered)
            val db = GameRepository.getDatabase(this@SettingsActivity)
            val allGames = db.gameDao().getAllGames()

            // Check which folders exist
            val existingFolders = mutableSetOf<String>()
            for (game in allGames) {
                val exists = rootFolder.listFiles().any {
                    it.isDirectory && it.name == game.folderName
                }
                if (exists) {
                    existingFolders.add(game.folderName)
                }
            }

            Log.d("Settings", "Found ${existingFolders.size} game folders")

            // Filter GameRepository
            GameRepository.filterGamesByAvailableFolders(this@SettingsActivity, existingFolders)

            Toast.makeText(
                this@SettingsActivity,
                getString(R.string.settings_folder_count_found, existingFolders.size),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startVerification(resetAll: Boolean) {
        Log.d("Settings", "startVerification called (resetAll=$resetAll)")

        val folderUri = getFolderUri()
        if (folderUri == null) {
            Log.e("Settings", "No folder URI found")
            Toast.makeText(this, getString(R.string.settings_no_folder_selected), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            if (resetAll) {
                // Reset all tracks to NOT_VERIFIED before starting
                GameRepository.resetAllTracksToUnverified(this@SettingsActivity)
                Log.d("Settings", "Reset all tracks to NOT_VERIFIED")
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_reset_and_start_verify),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_verification_started),
                    Toast.LENGTH_SHORT
                ).show()
            }

            Log.d("Settings", "Starting verification for: $folderUri")
            VerificationManager.startVerification(this@SettingsActivity, folderUri)
        }
    }

    private fun cancelVerification() {
        VerificationManager.cancelVerification(this)
        Toast.makeText(this, getString(R.string.settings_verification_cancelled), Toast.LENGTH_SHORT).show()
    }

    private fun observeVerificationProgress() {
        lifecycleScope.launch {
            VerificationManager.getVerificationProgress(this@SettingsActivity)
                .collect { progress ->
                    when (progress) {
                        is VerificationProgress.NotRunning -> {
                            binding.verificationStatusText.text = getString(R.string.settings_status_ready)
                            binding.verificationProgressBar.visibility = View.GONE
                            binding.verifyTracksButton.visibility = View.VISIBLE
                            binding.verifyAllTracksButton.visibility = View.VISIBLE
                            binding.cancelVerificationButton.visibility = View.GONE
                        }
                        is VerificationProgress.Running -> {
                            val percentage = if (progress.total > 0) {
                                (progress.current * 100) / progress.total
                            } else 0
                            binding.verificationStatusText.text =
                                getString(R.string.settings_status_checking, progress.current, progress.total, percentage, progress.folderName)
                            binding.verificationProgressBar.visibility = View.VISIBLE
                            binding.verificationProgressBar.max = progress.total
                            binding.verificationProgressBar.progress = progress.current
                            binding.verifyTracksButton.visibility = View.GONE
                            binding.verifyAllTracksButton.visibility = View.GONE
                            binding.cancelVerificationButton.visibility = View.VISIBLE
                        }
                        is VerificationProgress.Completed -> {
                            binding.verificationStatusText.text = getString(R.string.settings_status_completed)
                            binding.verificationProgressBar.visibility = View.GONE
                            binding.verifyTracksButton.visibility = View.VISIBLE
                            binding.verifyAllTracksButton.visibility = View.VISIBLE
                            binding.cancelVerificationButton.visibility = View.GONE
                        }
                        is VerificationProgress.Failed -> {
                            binding.verificationStatusText.text = getString(R.string.settings_status_failed)
                            binding.verificationProgressBar.visibility = View.GONE
                            binding.verifyTracksButton.visibility = View.VISIBLE
                            binding.verifyAllTracksButton.visibility = View.VISIBLE
                            binding.cancelVerificationButton.visibility = View.GONE
                        }
                    }
                }
        }
    }

    private fun getFolderUri(): Uri? =
        prefs().getString(KEY_FOLDER_URI, null)?.toUri()

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)
}
