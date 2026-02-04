package com.sortasong.sortasong.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.sortasong.sortasong.GameEntry
import com.sortasong.sortasong.GameRepository
import com.sortasong.sortasong.TrackEntry
import kotlinx.serialization.json.Json

/**
 * Repository for managing custom games loaded from game_info.json files.
 * Custom games are stored locally in folders and not synced from Supabase.
 */
object CustomGameRepository {
    private const val TAG = "CustomGameRepository"
    private const val GAME_INFO_FILE = "game_info.json"
    
    // Track ID offset for custom games to avoid collision with Supabase IDs
    // Custom game track IDs will be negative
    private var nextCustomTrackId = -1
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Custom games loaded from folders
    val customGames: MutableList<GameEntry> = mutableListOf()
    val customTracksByFolder: MutableMap<String, List<TrackEntry>> = mutableMapOf()
    
    // Store full CustomGameInfo for management screen
    private val customGameInfos: MutableMap<String, CustomGameInfo> = mutableMapOf()
    
    // Store root URI for delete operations
    private var rootFolderUri: Uri? = null
    
    // Set of official folder names (from Supabase) to exclude from custom game detection
    private var officialFolderNames: Set<String> = emptySet()
    
    /**
     * Set the list of official folder names from Supabase games.
     * These folders will be excluded when scanning for custom games.
     */
    fun setOfficialFolderNames(folderNames: Set<String>) {
        officialFolderNames = folderNames
        Log.d(TAG, "Set ${folderNames.size} official folder names")
    }
    
    /**
     * Scan a root folder for custom games (folders with game_info.json that aren't official games).
     */
    fun scanForCustomGames(context: Context, rootUri: Uri): Int {
        customGames.clear()
        customTracksByFolder.clear()
        customGameInfos.clear()
        nextCustomTrackId = -1
        rootFolderUri = rootUri
        
        val rootFolder = DocumentFile.fromTreeUri(context, rootUri)
        if (rootFolder == null || !rootFolder.isDirectory) {
            Log.e(TAG, "Invalid root folder")
            return 0
        }
        
        var foundCount = 0
        
        for (subfolder in rootFolder.listFiles()) {
            if (!subfolder.isDirectory) continue
            
            val folderName = subfolder.name ?: continue
            
            // Skip if this is an official game folder
            if (folderName in officialFolderNames) {
                Log.d(TAG, "Skipping official folder: $folderName")
                continue
            }
            
            // Check for game_info.json
            val gameInfoFile = subfolder.findFile(GAME_INFO_FILE)
            if (gameInfoFile != null && gameInfoFile.isFile) {
                val gameInfo = loadGameInfo(context, gameInfoFile.uri)
                if (gameInfo != null) {
                    addCustomGame(gameInfo)
                    foundCount++
                    Log.d(TAG, "Found custom game: ${gameInfo.game} in $folderName")
                }
            }
        }
        
        Log.d(TAG, "Scan complete: found $foundCount custom games")
        return foundCount
    }
    
    /**
     * Load and parse a game_info.json file.
     */
    private fun loadGameInfo(context: Context, uri: Uri): CustomGameInfo? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader()?.use { it.readText() } ?: return null
            json.decodeFromString<CustomGameInfo>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading game_info.json: ${e.message}", e)
            null
        }
    }
    
    /**
     * Add a custom game to the repository, converting to GameEntry/TrackEntry format.
     */
    private fun addCustomGame(gameInfo: CustomGameInfo) {
        // Store full info for management
        customGameInfos[gameInfo.folderName] = gameInfo
        
        // Create GameEntry
        val gameEntry = GameEntry(
            game = gameInfo.game,
            folderName = gameInfo.folderName,
            linkIdentifier = gameInfo.linkIdentifier
        )
        customGames.add(gameEntry)
        
        // Create TrackEntry list with negative IDs
        val trackEntries = gameInfo.tracks.map { track ->
            TrackEntry(
                trackId = nextCustomTrackId--,
                trackNr = track.trackNr,
                song = track.song,
                artist = track.artist,
                releaseDate = track.releaseDate,
                releaseYear = track.releaseYear,
                folderName = gameInfo.folderName,
                fileAvailability = FileAvailability.NOT_VERIFIED,
                originalFileName = track.originalFileName
            )
        }
        customTracksByFolder[gameInfo.folderName] = trackEntries
    }
    
    /**
     * Check if a track is from a custom game (has negative ID).
     */
    fun isCustomTrack(trackId: Int): Boolean = trackId < 0
    
    /**
     * Check if a folder contains a custom game.
     */
    fun isCustomGameFolder(folderName: String): Boolean = folderName in customTracksByFolder.keys
    
    /**
     * Get all games (official + custom) merged.
     */
    fun getAllGames(): List<GameEntry> {
        return GameRepository.games + customGames
    }
    
    /**
     * Get all tracks for a folder (checks both official and custom).
     */
    fun getTracksForFolder(folderName: String): List<TrackEntry>? {
        return GameRepository.tracksByFolder[folderName] 
            ?: customTracksByFolder[folderName]
    }
    
    /**
     * Clear all custom games.
     */
    fun clear() {
        customGames.clear()
        customTracksByFolder.clear()
        customGameInfos.clear()
        nextCustomTrackId = -1
    }
    
    /**
     * Get all custom game infos for management screen.
     */
    fun getCustomGameInfos(): List<CustomGameInfo> = customGameInfos.values.toList()
    
    /**
     * Get a specific custom game info by folder name.
     */
    fun getCustomGameInfo(folderName: String): CustomGameInfo? = customGameInfos[folderName]
    
    /**
     * Delete a custom game by removing its game_info.json file.
     * Music files are kept.
     */
    fun deleteCustomGame(context: Context, folderName: String): Boolean {
        val rootUri = rootFolderUri ?: return false
        val rootFolder = DocumentFile.fromTreeUri(context, rootUri) ?: return false
        
        val gameFolder = rootFolder.findFile(folderName)
        if (gameFolder == null || !gameFolder.isDirectory) {
            Log.e(TAG, "Game folder not found: $folderName")
            return false
        }
        
        val gameInfoFile = gameFolder.findFile(GAME_INFO_FILE)
        return if (gameInfoFile != null && gameInfoFile.delete()) {
            // Remove from memory
            customGames.removeAll { it.folderName == folderName }
            customTracksByFolder.remove(folderName)
            customGameInfos.remove(folderName)
            Log.d(TAG, "Deleted custom game: $folderName")
            true
        } else {
            Log.e(TAG, "Failed to delete game_info.json for: $folderName")
            false
        }
    }
}
