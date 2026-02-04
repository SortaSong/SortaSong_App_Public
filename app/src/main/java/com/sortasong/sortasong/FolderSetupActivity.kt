package com.sortasong.sortasong

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sortasong.sortasong.data.CustomGameRepository
import com.sortasong.sortasong.databinding.ActivityFolderSetupBinding
import kotlinx.coroutines.launch

data class GameFolderStatus(
    val folderName: String,
    val gameName: String,
    var exists: Boolean,
    var shouldCreate: Boolean = false,
    val isCustom: Boolean = false
)

class FolderSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderSetupBinding
    private lateinit var folderUri: Uri
    private val folderStatuses = mutableListOf<GameFolderStatus>()
    private lateinit var adapter: FolderSetupAdapter

    companion object {
        const val EXTRA_FOLDER_URI = "folder_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        folderUri = intent.getStringExtra(EXTRA_FOLDER_URI)?.toUri() ?: Uri.EMPTY

        binding.folderPathText.text = getString(R.string.foldersetup_folder_path_display, folderUri.path)

        // Setup RecyclerView
        adapter = FolderSetupAdapter(folderStatuses)
        binding.folderStatusList.layoutManager = LinearLayoutManager(this)
        binding.folderStatusList.adapter = adapter

        // Load data and check folders
        loadDataAndCheckFolders()

        binding.continueButton.setOnClickListener {
            createMissingFolders()
        }
    }

    private fun loadDataAndCheckFolders() {
        binding.loadingText.text = getString(R.string.foldersetup_loading_data)
        binding.continueButton.isEnabled = false

        lifecycleScope.launch {
            // Download data from Supabase
            val result = GameRepository.syncFromSupabase(this@FolderSetupActivity)

            when (result) {
                is LoadResult.Success -> {
                    Log.d("FolderSetup", "Data loaded successfully")
                    checkExistingFolders()
                }
                is LoadResult.Error -> {
                    Log.e("FolderSetup", "Failed to load data: ${result.message}")
                    Toast.makeText(
                        this@FolderSetupActivity,
                        getString(R.string.foldersetup_error_loading, result.message),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                else -> {
                    finish()
                }
            }
        }
    }

    private fun checkExistingFolders() {
        binding.loadingText.text = getString(R.string.foldersetup_checking_folders)

        val rootFolder = DocumentFile.fromTreeUri(this, folderUri)
        if (rootFolder == null || !rootFolder.isDirectory) {
            Toast.makeText(this, getString(R.string.settings_invalid_folder), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Get all official games from repository
        val games = GameRepository.games
        
        // Set official folder names for custom game detection
        val officialFolderNames = games.map { it.folderName }.toSet()
        CustomGameRepository.setOfficialFolderNames(officialFolderNames)

        folderStatuses.clear()

        // Add official games
        for (game in games) {
            // Check if folder exists
            val exists = rootFolder.listFiles().any {
                it.isDirectory && it.name == game.folderName
            }

            folderStatuses.add(
                GameFolderStatus(
                    folderName = game.folderName,
                    gameName = game.game,
                    exists = exists,
                    shouldCreate = false,
                    isCustom = false
                )
            )
        }
        
        // Scan for custom games
        val customGameCount = CustomGameRepository.scanForCustomGames(this, folderUri)
        Log.d("FolderSetup", "Found $customGameCount custom games")
        
        // Add custom games to the list (they always exist since we found them)
        for (customGame in CustomGameRepository.customGames) {
            folderStatuses.add(
                GameFolderStatus(
                    folderName = customGame.folderName,
                    gameName = "${customGame.game} ⭐",  // Mark as custom
                    exists = true,
                    shouldCreate = false,
                    isCustom = true
                )
            )
        }

        // Update UI
        val foundCount = folderStatuses.count { it.exists }
        val missingCount = folderStatuses.count { !it.exists }

        binding.loadingText.text = getString(R.string.foldersetup_found_missing_counts, foundCount, missingCount)
        adapter.notifyDataSetChanged()
        binding.continueButton.isEnabled = true
    }

    private fun createMissingFolders() {
        val toCreate = folderStatuses.filter { !it.exists && it.shouldCreate }

        if (toCreate.isEmpty()) {
            // No folders to create, just continue
            filterAndFinishSetup()
            return
        }

        val rootFolder = DocumentFile.fromTreeUri(this, folderUri)
        if (rootFolder == null || !rootFolder.isDirectory) {
            Toast.makeText(this, getString(R.string.settings_invalid_folder), Toast.LENGTH_SHORT).show()
            return
        }

        var createdCount = 0
        for (status in toCreate) {
            val created = rootFolder.createDirectory(status.folderName)
            if (created != null) {
                createdCount++
                status.exists = true // Mark as now existing
                Log.d("FolderSetup", "Created folder: ${status.folderName}")
            } else {
                Log.e("FolderSetup", "Failed to create folder: ${status.folderName}")
            }
        }

        Toast.makeText(
            this,
            getString(R.string.foldersetup_created_count, createdCount, toCreate.size),
            Toast.LENGTH_SHORT
        ).show()

        filterAndFinishSetup()
    }

    private fun filterAndFinishSetup() {
        // Get all folders that exist (either originally or just created)
        val availableFolders = folderStatuses
            .filter { it.exists }
            .map { it.folderName }
            .toSet()

        Log.d("FolderSetup", "Available folders: ${availableFolders.size}")

        // Filter GameRepository to only include games with available folders
        lifecycleScope.launch {
            GameRepository.filterGamesByAvailableFolders(this@FolderSetupActivity, availableFolders)
            finishSetup()
        }
    }

    private fun finishSetup() {
        // Return to MainActivity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}
