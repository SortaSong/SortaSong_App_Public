package com.sortasong.sortasong

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sortasong.sortasong.databinding.ActivityLoadingBinding
import com.sortasong.sortasong.databinding.ActivitySetupBinding
import com.sortasong.sortasong.databinding.ActivityStartBinding
import com.sortasong.sortasong.workers.VerificationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS          = "settings"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val UPDATE_INTERVAL_MS = 3000L // Update every 3 seconds
    }

    private var gameStatusAdapter: GameStatusAdapter? = null
    private var verificationUpdateJob: Job? = null

    /** ActivityResult‑Launcher für SAF‑Ordnerauswahl */
    private val folderPicker =
        registerForActivityResult(OpenDocumentTree()) { uri: Uri? ->
            uri?.let { persistAndContinue(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called d")

        decideStartScreen()
    }

    override fun onResume() {
        super.onResume()
        // Start observing verification progress and updating games list
        startVerificationUpdates()
    }

    override fun onPause() {
        super.onPause()
        // Stop updating when activity is not visible
        stopVerificationUpdates()
    }

    private fun startVerificationUpdates() {
        // Cancel any existing job
        verificationUpdateJob?.cancel()

        // Start periodic updates while verification is running
        verificationUpdateJob = lifecycleScope.launch {
            while (true) {
                VerificationManager.getVerificationProgress(this@MainActivity)
                    .collect { _ ->
                        // Update games list
                        updateGamesList()
                        return@collect // Exit after first emission
                    }

                // Wait before next update
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopVerificationUpdates() {
        verificationUpdateJob?.cancel()
        verificationUpdateJob = null
    }

    private fun updateGamesList() {
        gameStatusAdapter?.let { adapter ->
            val updatedStatuses = GameRepository.getAllGameVerificationStatuses()
            adapter.updateGames(updatedStatuses)
            Log.d("MainActivity", "Updated game statuses")
        }
    }

    /* ------------------------------------------------------- */
    /* ------------------------ UI‑FLOW ----------------------- */
    /* ------------------------------------------------------- */

    /** Entscheidet, ob Setup‑ oder Start‑Screen angezeigt wird */
    private fun decideStartScreen() {
        val savedUri = getFolderUri()
        if (savedUri == null) {
            showSetupScreen()
        } else {
            // Load data from Supabase with offline cache
            loadDataAndShowStartScreen(savedUri)
        }
    }

    /** Load data asynchronously and show start screen */
    private fun loadDataAndShowStartScreen(uri: Uri) {
        showLoadingScreen()

        lifecycleScope.launch {
            // Check if we have cached data
            val hasCache = GameRepository.hasCachedData(this@MainActivity)

            val result = if (hasCache) {
                // Load from cache (fast startup)
                Log.d("MainActivity", "Loading from cache...")
                GameRepository.loadFromCacheOnly(this@MainActivity)
            } else {
                // First time or cache cleared - download from Supabase
                Log.d("MainActivity", "No cache found, downloading from Supabase...")
                GameRepository.loadFromSupabase(this@MainActivity)
            }

            when (result) {
                is LoadResult.Success, is LoadResult.OfflineMode -> {
                    Log.d("MainActivity", "Data loaded successfully")

                    // Filter games to only show those with existing folders
                    val existingFolders = getExistingGameFolders(uri)
                    if (existingFolders.isNotEmpty()) {
                        GameRepository.filterGamesByAvailableFolders(
                            this@MainActivity,
                            existingFolders
                        )
                        Log.d("MainActivity", "Filtered to ${existingFolders.size} existing folders")
                    }

                    showStartScreen(uri, fromCache = hasCache || result is LoadResult.OfflineMode)

                    // Automatisch Verifikation starten, wenn ungeprüfte Tracks vorhanden sind
                    checkAndStartAutoVerification(uri)
                }
                is LoadResult.Error -> {
                    Log.e("MainActivity", "Failed to load data: ${result.message}")
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    showSetupScreen()
                }
            }
        }
    }

    /**
     * Prüft, ob ungeprüfte Tracks vorhanden sind und startet automatisch die Verifikation.
     */
    private fun checkAndStartAutoVerification(uri: Uri) {
        lifecycleScope.launch {
            // Prüfe, ob es ungeprüfte Tracks gibt
            val tracksToVerify = GameRepository.getTracksNeedingVerification()

            if (tracksToVerify.isNotEmpty()) {
                Log.d("MainActivity", "Found ${tracksToVerify.size} unverified tracks, starting auto-verification")

                // Starte Verifikation im Hintergrund
                VerificationManager.startVerification(this@MainActivity, uri)
            } else {
                Log.d("MainActivity", "All tracks are verified, no auto-verification needed")
            }
        }
    }

    /** Show loading screen while data is being fetched */
    private fun showLoadingScreen() {
        val binding = ActivityLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    /** 1) Ersteinrichtung: Nutzer wählt einen Ordner */
    private fun showSetupScreen() {
        val binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.chooseFolderButton.setOnClickListener {
            folderPicker.launch(null)               // SAF‑Dialog öffnen
        }
    }

    /** 2) Startseite: „Mit / Ohne Karten" */
    private fun showStartScreen(uri: Uri, fromCache: Boolean = false) {
        val binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show cache indicator if loaded from cache
        if (fromCache) {
            binding.folderHint.text = getString(R.string.activity_start_folder_from_cache, uri.path)
        } else {
            binding.folderHint.text = getString(R.string.activity_start_folder_selected, uri.path)
        }

        // Set up games list with verification status
        val gameStatuses = GameRepository.getAllGameVerificationStatuses()
        gameStatusAdapter = GameStatusAdapter(gameStatuses) { gameStatus ->
            // Handle click on game with missing tracks
            if (gameStatus.unavailableTracks > 0) {
                // Show missing tracks activity
                val intent = Intent(this, MissingTracksActivity::class.java)
                intent.putExtra(MissingTracksActivity.EXTRA_FOLDER_NAME, gameStatus.game.folderName)
                intent.putExtra(MissingTracksActivity.EXTRA_GAME_NAME, gameStatus.game.game)
                startActivity(intent)
            }
        }

        binding.gamesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.gamesRecyclerView.adapter = gameStatusAdapter

        binding.withCardsButton.setOnClickListener {
            launchGame(withCards = true)
        }
        binding.withoutCardsButton.setOnClickListener {
            launchGame(withCards = false)
        }

        // Settings button
        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    /* ------------------------------------------------------- */
    /* -------------------- Ordner‑Persistence ---------------- */
    /* ------------------------------------------------------- */

    private fun persistAndContinue(uri: Uri) {
        /* Persistente Berechtigung, damit du auch nach Neustart Zugriff hast */
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs().edit {
            putString(KEY_FOLDER_URI, uri.toString())
        }

        // Launch folder setup activity to check/create game folders
        val intent = Intent(this, FolderSetupActivity::class.java)
        intent.putExtra(FolderSetupActivity.EXTRA_FOLDER_URI, uri.toString())
        startActivity(intent)
        finish()
    }

    private fun getFolderUri(): Uri? =
        prefs().getString(KEY_FOLDER_URI, null)?.toUri()

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    /**
     * Check which game folders exist in the selected directory.
     * Returns set of folder names that actually exist.
     */
    private suspend fun getExistingGameFolders(uri: Uri): Set<String> {
        val rootFolder = DocumentFile.fromTreeUri(this, uri)
        if (rootFolder == null || !rootFolder.isDirectory) {
            Log.e("MainActivity", "Invalid root folder")
            return emptySet()
        }

        // Get all games from database (not filtered yet)
        val db = GameRepository.getDatabase(this)
        val allGames = db.gameDao().getAllGames()

        // Check which folders exist
        val existingFolders = mutableSetOf<String>()
        val subFolders = rootFolder.listFiles()

        for (game in allGames) {
            val exists = subFolders.any {
                it.isDirectory && it.name == game.folderName
            }
            if (exists) {
                existingFolders.add(game.folderName)
            }
        }

        Log.d("MainActivity", "Found ${existingFolders.size} existing game folders")
        return existingFolders
    }

    /* ------------------------------------------------------- */
    /* -------------------- Game‑Launcher --------------------- */
    /* ------------------------------------------------------- */

    private fun launchGame(withCards: Boolean) {
        if (withCards) {
            val intent = Intent(this, QrScanActivity::class.java)
            intent.putExtra("WITH_CARDS", true)
            startActivity(intent)
        } else {
            val intent = Intent(this, PlayerSetupActivity::class.java)
            startActivity(intent)
        }
    }
}
