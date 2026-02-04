package com.sortasong.sortasong

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sortasong.sortasong.data.CustomGameInfo
import com.sortasong.sortasong.data.CustomGameRepository
import com.sortasong.sortasong.data.CustomTrack
import com.sortasong.sortasong.data.SubmissionCredential
import com.sortasong.sortasong.data.SubmissionResult
import com.sortasong.sortasong.data.SubmissionService
import com.sortasong.sortasong.databinding.ActivityCustomGameEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for editing custom game metadata.
 * Tracklist editing is done in TrackListEditorActivity.
 */
class CustomGameEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOLDER_NAME = "folder_name"
        const val REQUEST_EDIT_TRACKS = 1001
        private const val TAG = "CustomGameEditor"
    }

    private lateinit var binding: ActivityCustomGameEditorBinding
    private var selectedFolderUri: Uri? = null
    private var editingFolderName: String? = null
    private var tracks = mutableListOf<EditableTrack>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedFolderUri = uri
            onFolderSelected(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityCustomGameEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle window insets - add base padding plus insets
        val basePadding = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = insets.left + basePadding,
                top = insets.top + basePadding,
                right = insets.right + basePadding,
                bottom = insets.bottom + basePadding
            )
            WindowInsetsCompat.CONSUMED
        }

        editingFolderName = intent.getStringExtra(EXTRA_FOLDER_NAME)
        
        setupUI()
        
        if (editingFolderName != null) {
            loadExistingGame(editingFolderName!!)
        }
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        binding.selectFolderButton.setOnClickListener { folderPicker.launch(null) }
        binding.editTracklistButton.setOnClickListener { openTracklistEditor() }
        binding.saveButton.setOnClickListener { saveGame() }
        binding.shareButton.setOnClickListener { showShareDialog() }
        
        updateTrackCount()
    }

    private fun loadExistingGame(folderName: String) {
        val gameInfo = CustomGameRepository.getCustomGameInfo(folderName)
        if (gameInfo != null) {
            binding.gameNameInput.setText(gameInfo.game)
            binding.folderNameInput.setText(gameInfo.folderName)
            binding.folderNameInput.isEnabled = false // Can't change folder name when editing
            
            // Load tracks
            tracks = gameInfo.tracks.map { track ->
                EditableTrack(
                    artist = track.artist,
                    title = track.song,
                    releaseDate = track.releaseDate,
                    year = track.releaseYear,
                    originalFileName = track.originalFileName ?: ""
                )
            }.toMutableList()
            
            // Find the folder URI - use correct prefs name and key
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val rootUri = prefs.getString("folder_uri", null)?.toUri()
            if (rootUri != null) {
                val rootFolder = DocumentFile.fromTreeUri(this, rootUri)
                val gameFolder = rootFolder?.findFile(folderName)
                selectedFolderUri = gameFolder?.uri
                binding.selectFolderButton.text = getString(R.string.editor_folder_selected, folderName)
            }
            
            updateTrackCount()
        }
    }

    private fun onFolderSelected(uri: Uri) {
        val folder = DocumentFile.fromTreeUri(this, uri)
        val folderName = folder?.name ?: "Unknown"
        
        binding.selectFolderButton.text = getString(R.string.editor_folder_selected, folderName)
        binding.folderNameInput.setText(folderName)
        
        if (binding.gameNameInput.text.isNullOrBlank()) {
            val prettyName = folderName.replace("_", " ").replace("-", " ")
            binding.gameNameInput.setText(prettyName)
        }
        
        // Auto-scan folder
        scanFolder(uri)
    }

    private fun scanFolder(folderUri: Uri) {
        lifecycleScope.launch {
            val scannedTracks = withContext(Dispatchers.IO) {
                scanAudioFiles(folderUri)
            }

            tracks.clear()
            tracks.addAll(scannedTracks)
            updateTrackCount()
            
            if (scannedTracks.isEmpty()) {
                Toast.makeText(this@CustomGameEditorActivity, 
                    getString(R.string.editor_no_audio_files), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@CustomGameEditorActivity,
                    getString(R.string.editor_tracks_found, scannedTracks.size), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scanAudioFiles(folderUri: Uri): List<EditableTrack> {
        val folder = DocumentFile.fromTreeUri(this, folderUri) ?: return emptyList()
        val audioExtensions = listOf(".mp3", ".m4a", ".flac", ".ogg", ".opus", ".wav", ".aac")
        val result = mutableListOf<EditableTrack>()

        val audioFiles = folder.listFiles()
            .filter { file -> 
                file.isFile && audioExtensions.any { file.name?.lowercase()?.endsWith(it) == true }
            }
            .sortedBy { it.name }

        for (file in audioFiles) {
            val track = readMetadata(file)
            result.add(track)
        }

        return result
    }

    private fun readMetadata(file: DocumentFile): EditableTrack {
        val fileName = file.name ?: ""
        var artist = ""
        var title = ""
        var year: Int? = null
        var releaseDate = ""

        try {
            val retriever = MediaMetadataRetriever()
            contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                year = yearStr?.take(4)?.toIntOrNull()
                val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                if (date != null) {
                    releaseDate = date
                    if (year == null) {
                        year = extractYear(date)
                    }
                }
            }
            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read metadata for $fileName", e)
        }

        // Fallback to filename parsing
        if (artist.isBlank() || title.isBlank()) {
            val parsed = parseFilename(fileName)
            if (artist.isBlank()) artist = parsed.first
            if (title.isBlank()) title = parsed.second
        }

        return EditableTrack(
            artist = artist,
            title = title,
            releaseDate = releaseDate,
            year = year,
            originalFileName = fileName
        )
    }

    private fun parseFilename(filename: String): Pair<String, String> {
        val name = filename.substringBeforeLast('.')
        val cleaned = name.replace(Regex("^\\d+[.\\-_\\s]+"), "")
        
        val separators = listOf(" - ", " – ", " — ", "_-_")
        for (sep in separators) {
            if (cleaned.contains(sep)) {
                val parts = cleaned.split(sep, limit = 2)
                if (parts.size >= 2) {
                    return Pair(parts[0].trim(), parts[1].trim())
                }
            }
        }
        
        return Pair("", cleaned.trim())
    }

    private fun extractYear(dateStr: String): Int? {
        val yearMatch = Regex("(\\d{4})").find(dateStr)
        return yearMatch?.value?.toIntOrNull()
    }

    private fun updateTrackCount() {
        binding.trackCountText.text = getString(R.string.track_editor_track_count, tracks.size)
    }

    private fun openTracklistEditor() {
        if (tracks.isEmpty() && selectedFolderUri == null) {
            Toast.makeText(this, getString(R.string.editor_select_folder_first), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save tracks to shared state
        TrackListEditorActivity.tracksToEdit = tracks
        
        val intent = Intent(this, TrackListEditorActivity::class.java)
        startActivityForResult(intent, REQUEST_EDIT_TRACKS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_TRACKS && resultCode == RESULT_OK) {
            // Get updated tracks
            tracks = TrackListEditorActivity.tracksToEdit.toMutableList()
            updateTrackCount()
        }
    }

    private fun saveGame() {
        lifecycleScope.launch {
            try {
                val success = saveGameInternal()
                if (success) {
                    Toast.makeText(this@CustomGameEditorActivity, 
                        getString(R.string.editor_saved_success), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving game", e)
                Toast.makeText(this@CustomGameEditorActivity,
                    getString(R.string.editor_save_error, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private suspend fun saveGameInternal(): Boolean {
        val gameName = binding.gameNameInput.text.toString().trim()
        val folderName = binding.folderNameInput.text.toString().trim()

        if (gameName.isBlank() || folderName.isBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CustomGameEditorActivity, 
                    getString(R.string.editor_fill_required), Toast.LENGTH_SHORT).show()
            }
            return false
        }

        if (tracks.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CustomGameEditorActivity, 
                    getString(R.string.editor_add_tracks_first), Toast.LENGTH_SHORT).show()
            }
            return false
        }

        val uri = selectedFolderUri
        if (uri == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CustomGameEditorActivity, 
                    getString(R.string.editor_select_folder_first), Toast.LENGTH_SHORT).show()
            }
            return false
        }

        val gameInfo = CustomGameInfo(
            game = gameName,
            folderName = folderName,
            linkIdentifier = "",
            cardPurchaseUrl = "",
            hasPhysicalCards = false,
            isCustom = true,
            createdAt = java.time.Instant.now().toString(),
            tracks = tracks.mapIndexed { index, track ->
                CustomTrack(
                    trackNr = (index + 1).toString(),
                    song = track.title,
                    artist = track.artist,
                    releaseDate = track.releaseDate,
                    releaseYear = track.year ?: 0,
                    originalFileName = track.originalFileName
                )
            }
        )

        withContext(Dispatchers.IO) {
            val folder = DocumentFile.fromTreeUri(this@CustomGameEditorActivity, uri)
            if (folder == null || !folder.canWrite()) {
                throw Exception(getString(R.string.editor_cannot_write))
            }

            folder.findFile("game_info.json")?.delete()

            val gameInfoFile = folder.createFile("application/json", "game_info.json")
                ?: throw Exception("Failed to create file")

            contentResolver.openOutputStream(gameInfoFile.uri)?.use { output ->
                output.write(gson.toJson(gameInfo).toByteArray(Charsets.UTF_8))
            }
        }

        // Refresh custom games - use correct prefs name and key
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val rootUri = prefs.getString("folder_uri", null)?.toUri()
        if (rootUri != null) {
            val officialFolderNames = GameRepository.games.map { it.folderName }.toSet()
            CustomGameRepository.setOfficialFolderNames(officialFolderNames)
            CustomGameRepository.scanForCustomGames(this@CustomGameEditorActivity, rootUri)
        }
        
        return true
    }
    
    private fun showShareDialog() {
        val gameName = binding.gameNameInput.text.toString().trim()
        val folderName = binding.folderNameInput.text.toString().trim()
        
        // Validate inputs
        if (gameName.isBlank() || folderName.isBlank()) {
            Toast.makeText(this, getString(R.string.editor_fill_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (tracks.isEmpty()) {
            Toast.makeText(this, getString(R.string.editor_add_tracks_first), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if game needs to be saved
        val needsSave = editingFolderName == null || !CustomGameRepository.isCustomGameFolder(folderName)
        
        if (needsSave) {
            // Save first, then share
            saveGameAndShare()
        } else {
            // Already saved, proceed to share
            proceedToShare(folderName)
        }
    }
    
    private fun saveGameAndShare() {
        lifecycleScope.launch {
            try {
                val success = saveGameInternal()
                if (success) {
                    val folderName = binding.folderNameInput.text.toString().trim()
                    proceedToShare(folderName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving before share", e)
                Toast.makeText(this@CustomGameEditorActivity,
                    getString(R.string.editor_save_error, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun proceedToShare(folderName: String) {
        // Check for existing submission
        val existingCredential = SubmissionService.getCredentialForFolder(this, folderName)
        if (existingCredential != null) {
            showResubmitDialog(existingCredential)
            return
        }
        
        showNewSubmissionDialog(folderName)
    }
    
    private fun showResubmitDialog(existingCredential: SubmissionCredential) {
        AlertDialog.Builder(this)
            .setTitle(R.string.share_resubmit_title)
            .setMessage(R.string.share_resubmit_message)
            .setPositiveButton(R.string.share_resubmit_confirm) { _, _ ->
                resubmitGame(existingCredential)
            }
            .setNegativeButton(R.string.share_dialog_cancel, null)
            .show()
    }
    
    private fun showNewSubmissionDialog(folderName: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_share_game, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.submitterNameInput)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.descriptionInput)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.share_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.share_dialog_submit) { _, _ ->
                val submitterName = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                submitGame(folderName, submitterName, description)
            }
            .setNegativeButton(R.string.share_dialog_cancel, null)
            .show()
    }
    
    private fun submitGame(folderName: String, submitterName: String, description: String) {
        val gameInfo = buildCurrentGameInfo() ?: return
        val password = SubmissionService.generatePassword()
        
        Toast.makeText(this, getString(R.string.share_submitting), Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SubmissionService.submitGame(gameInfo, description, submitterName, password)
            }
            
            when (result) {
                is SubmissionResult.Success -> {
                    // Save credentials locally
                    val credential = SubmissionCredential(
                        folderName = folderName,
                        gameName = gameInfo.game,
                        submissionId = result.submissionId,
                        passwordHash = SubmissionService.hashPassword(password)
                    )
                    SubmissionService.saveCredential(this@CustomGameEditorActivity, credential)
                    
                    showSuccessDialog(result.submissionId, password)
                }
                is SubmissionResult.Error -> {
                    Toast.makeText(this@CustomGameEditorActivity,
                        getString(R.string.share_error, result.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun resubmitGame(existingCredential: SubmissionCredential) {
        val gameInfo = buildCurrentGameInfo() ?: return
        
        Toast.makeText(this, getString(R.string.share_submitting), Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SubmissionService.updateSubmission(
                    submissionId = existingCredential.submissionId,
                    passwordHash = existingCredential.passwordHash,
                    gameInfo = gameInfo
                )
            }
            
            when (result) {
                is SubmissionResult.Success -> {
                    // Update cached status
                    SubmissionService.updateCachedStatus(
                        this@CustomGameEditorActivity,
                        existingCredential.folderName,
                        "pending"
                    )
                    Toast.makeText(this@CustomGameEditorActivity,
                        getString(R.string.share_update_success), Toast.LENGTH_SHORT).show()
                }
                is SubmissionResult.Error -> {
                    Toast.makeText(this@CustomGameEditorActivity,
                        getString(R.string.share_error, result.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun buildCurrentGameInfo(): CustomGameInfo? {
        val gameName = binding.gameNameInput.text.toString().trim()
        val folderName = binding.folderNameInput.text.toString().trim()
        
        if (gameName.isBlank() || folderName.isBlank() || tracks.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_save_first), Toast.LENGTH_SHORT).show()
            return null
        }
        
        return CustomGameInfo(
            game = gameName,
            folderName = folderName,
            linkIdentifier = "",
            cardPurchaseUrl = "",
            hasPhysicalCards = false,
            isCustom = true,
            tracks = tracks.mapIndexed { index, track ->
                CustomTrack(
                    trackNr = (index + 1).toString(),
                    song = track.title,
                    artist = track.artist,
                    releaseDate = track.releaseDate,
                    releaseYear = track.year ?: 0,
                    originalFileName = track.originalFileName
                )
            }
        )
    }
    
    private fun showSuccessDialog(submissionId: String, password: String) {
        val message = getString(R.string.share_success_message, submissionId, password)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.share_success_title)
            .setMessage(message)
            .setPositiveButton(R.string.share_success_copy) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("SortaSong Credentials", 
                    "Submission ID: $submissionId\nPassword: $password")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.share_credentials_copied), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }
}

// Shared data class for editable tracks
data class EditableTrack(
    var artist: String = "",
    var title: String = "",
    var releaseDate: String = "",
    var year: Int? = null,
    var originalFileName: String = ""
)
