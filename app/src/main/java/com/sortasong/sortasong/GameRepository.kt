package com.sortasong.sortasong

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.sortasong.sortasong.data.SupabaseDataService
import com.sortasong.sortasong.data.local.AppDatabase
import com.sortasong.sortasong.data.local.CachedGame
import com.sortasong.sortasong.data.local.CachedGameTrack
import com.sortasong.sortasong.data.local.CachedTrack
import com.sortasong.sortasong.data.FileAvailability
import com.sortasong.sortasong.data.local.TrackWithGameInfo
import java.io.Serializable
import kotlin.math.min

data class GameEntry(
    val game: String,
    val folderName: String,
    val linkIdentifier: String
)

data class TrackEntry(
    val trackId: Int,
    val trackNr: String,
    val song: String,
    val artist: String,
    val releaseDate: String,
    val releaseYear: Int?,
    val folderName: String,
    val fileAvailability: FileAvailability = FileAvailability.NOT_VERIFIED
) : Serializable

sealed class LoadResult {
    data object Success : LoadResult()
    data class Error(val message: String) : LoadResult()
    data object OfflineMode : LoadResult()
}

object GameRepository {
    private const val TAG = "GameRepository"

    val tracksByFolder: MutableMap<String, List<TrackEntry>> = mutableMapOf()
    val invalidTracksByFolder: MutableMap<String, List<TrackEntry>> = mutableMapOf()
    var games: List<GameEntry> = emptyList()
        private set

    private var database: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return database ?: AppDatabase.getInstance(context).also { database = it }
    }

    /**
     * Check if cache has data without loading it.
     */
    suspend fun hasCachedData(context: Context): Boolean {
        val db = getDatabase(context)
        val cachedGames = db.gameDao().getAllGames()
        return cachedGames.isNotEmpty()
    }

    /**
     * Load data from cache only (fast startup).
     */
    suspend fun loadFromCacheOnly(context: Context): LoadResult {
        val db = getDatabase(context)

        return if (loadFromCache(db)) {
            LoadResult.Success
        } else {
            LoadResult.Error("No cached data available.")
        }
    }

    /**
     * Sync data from Supabase and update cache.
     */
    suspend fun syncFromSupabase(context: Context): LoadResult {
        val db = getDatabase(context)

        // Fetch from Supabase
        val result = SupabaseDataService.fetchAllData()

        return if (result.isSuccess) {
            val data = result.getOrThrow()
            Log.d(TAG, "Fetched from Supabase: ${data.games.size} games, ${data.tracks.size} tracks")

            // Update local cache
            updateCache(db, data.games, data.tracks, data.gameTracks)

            // Build in-memory data structures
            buildInMemoryData(db)

            LoadResult.Success
        } else {
            Log.w(TAG, "Supabase fetch failed: ${result.exceptionOrNull()?.message}")
            LoadResult.Error("Failed to sync: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * Load data from Supabase with offline cache fallback.
     * This is the primary data loading method.
     */
    suspend fun loadFromSupabase(context: Context): LoadResult {
        val db = getDatabase(context)

        // Try to fetch from Supabase first
        val result = SupabaseDataService.fetchAllData()

        return if (result.isSuccess) {
            val data = result.getOrThrow()
            Log.d(TAG, "Fetched from Supabase: ${data.games.size} games, ${data.tracks.size} tracks")

            // Update local cache
            updateCache(db, data.games, data.tracks, data.gameTracks)

            // Build in-memory data structures
            buildInMemoryData(db)

            LoadResult.Success
        } else {
            Log.w(TAG, "Supabase fetch failed, trying cache: ${result.exceptionOrNull()?.message}")

            // Try to load from cache
            if (loadFromCache(db)) {
                LoadResult.OfflineMode
            } else {
                LoadResult.Error("No data available. Please connect to the internet.")
            }
        }
    }

    private suspend fun updateCache(
        db: AppDatabase,
        games: List<CachedGame>,
        tracks: List<CachedTrack>,
        gameTracks: List<CachedGameTrack>
    ) {
        // Clear and repopulate (no FK constraints, so order doesn't matter)
        db.gameTrackDao().deleteAll()
        db.trackDao().deleteAll()
        db.gameDao().deleteAll()

        db.gameDao().insertAll(games)
        db.trackDao().insertAll(tracks)
        db.gameTrackDao().insertAll(gameTracks)

        Log.d(TAG, "Cache updated: ${games.size} games, ${tracks.size} tracks, ${gameTracks.size} mappings")
    }

    private suspend fun loadFromCache(db: AppDatabase): Boolean {
        val cachedGames = db.gameDao().getAllGames()
        if (cachedGames.isEmpty()) {
            Log.d(TAG, "Cache is empty")
            return false
        }

        Log.d(TAG, "Loading from cache: ${cachedGames.size} games")
        buildInMemoryData(db)
        return true
    }

    private suspend fun buildInMemoryData(db: AppDatabase, availableFolders: Set<String>? = null) {
        // Build games list
        val cachedGames = db.gameDao().getAllGames()

        // Filter games based on available folders if specified
        val filteredGames = if (availableFolders != null) {
            cachedGames.filter { it.folderName in availableFolders }
        } else {
            cachedGames
        }

        games = filteredGames.map { it.toGameEntry() }

        // Build tracksByFolder map
        tracksByFolder.clear()
        invalidTracksByFolder.clear()

        for (game in filteredGames) {
            val tracksForFolder = db.gameTrackDao().getTracksForFolder(game.folderName)

            val validTracks = mutableListOf<TrackEntry>()
            val invalidTracks = mutableListOf<TrackEntry>()

            for (trackInfo in tracksForFolder) {
                val entry = trackInfo.toTrackEntry()
                if (entry.releaseYear != null) {
                    validTracks.add(entry)
                } else {
                    invalidTracks.add(entry)
                }
            }

            tracksByFolder[game.folderName] = validTracks
            if (invalidTracks.isNotEmpty()) {
                invalidTracksByFolder[game.folderName] = invalidTracks
            }
        }

        Log.d(TAG, "Built in-memory data: ${games.size} games (filtered from ${cachedGames.size}), ${tracksByFolder.values.sumOf { it.size }} tracks")
    }

    /**
     * Filter games to only include those with available folders.
     * Call this after folder setup to update the games list.
     */
    suspend fun filterGamesByAvailableFolders(context: Context, availableFolders: Set<String>) {
        val db = getDatabase(context)
        buildInMemoryData(db, availableFolders)
    }

    /**
     * Verify if the music file exists for a track.
     * Updates the database with the result.
     */
    suspend fun verifyTrackFile(context: Context, track: TrackEntry, rootUri: Uri): FileAvailability {
        // Construct expected filename: "Artist - Song"
        val expectedFileName = "${track.artist} - ${track.song}"

        Log.d(TAG, "Verifying track: ${track.folderName}/$expectedFileName")

        // Use existing file finding logic with fuzzy matching
        val fileUri = findFileInSubfolder(context, rootUri, track.folderName, expectedFileName)

        val availability = if (fileUri != null) {
            Log.d(TAG, "  Found: $fileUri")
            FileAvailability.AVAILABLE
        } else {
            Log.d(TAG, "  Not found")
            FileAvailability.UNAVAILABLE
        }

        // Update database
        val db = getDatabase(context)
        db.trackDao().updateFileAvailability(track.trackId, availability.name)

        // Update in-memory track
        updateTrackAvailabilityInMemory(track.trackId, availability)

        return availability
    }

    /**
     * Update track availability in the in-memory tracksByFolder map.
     */
    private fun updateTrackAvailabilityInMemory(trackId: Int, availability: FileAvailability) {
        for ((folderName, tracks) in tracksByFolder) {
            val index = tracks.indexOfFirst { it.trackId == trackId }
            if (index != -1) {
                val updatedTrack = tracks[index].copy(fileAvailability = availability)
                tracksByFolder[folderName] = tracks.toMutableList().apply {
                    set(index, updatedTrack)
                }
                break
            }
        }
    }

    /**
     * Get all tracks that need verification (NOT_VERIFIED or UNAVAILABLE status).
     * This includes both unverified tracks and tracks that were previously found missing.
     */
    fun getTracksNeedingVerification(): List<TrackEntry> {
        return tracksByFolder.values.flatten().filter {
            it.fileAvailability == FileAvailability.NOT_VERIFIED ||
            it.fileAvailability == FileAvailability.UNAVAILABLE
        }
    }

    /**
     * Reset all tracks to NOT_VERIFIED status.
     * This forces a complete re-verification of all tracks.
     */
    suspend fun resetAllTracksToUnverified(context: Context) {
        val db = getDatabase(context)

        // Update database - set all tracks to NOT_VERIFIED
        db.trackDao().resetAllToNotVerified()

        // Update in-memory data
        tracksByFolder.values.flatten().forEach { track ->
            updateTrackAvailabilityInMemory(track.trackId, FileAvailability.NOT_VERIFIED)
        }

        Log.d(TAG, "Reset all tracks to NOT_VERIFIED")
    }

    /**
     * Get total track count across all enabled games.
     */
    fun getTotalTrackCount(): Int {
        return tracksByFolder.values.sumOf { it.size }
    }

    /**
     * Get count of already verified tracks (only AVAILABLE).
     * UNAVAILABLE tracks will be re-verified on next scan.
     */
    fun getVerifiedTrackCount(): Int {
        return tracksByFolder.values.flatten().count {
            it.fileAvailability == FileAvailability.AVAILABLE
        }
    }

    /**
     * Get verification statistics for a specific game folder.
     */
    fun getVerificationStats(folderName: String): Map<FileAvailability, Int> {
        val tracks = tracksByFolder[folderName] ?: return emptyMap()
        return tracks.groupingBy { it.fileAvailability }.eachCount()
    }

    /**
     * Get verification status for all games.
     * Returns list of GameVerificationStatus sorted by game name.
     */
    fun getAllGameVerificationStatuses(): List<GameVerificationStatus> {
        return games.map { game ->
            val stats = getVerificationStats(game.folderName)
            val totalTracks = tracksByFolder[game.folderName]?.size ?: 0

            GameVerificationStatus(
                game = game,
                totalTracks = totalTracks,
                availableTracks = stats[FileAvailability.AVAILABLE] ?: 0,
                unavailableTracks = stats[FileAvailability.UNAVAILABLE] ?: 0,
                notVerifiedTracks = stats[FileAvailability.NOT_VERIFIED] ?: 0
            )
        }.sortedBy { it.game.game }
    }

    private fun CachedGame.toGameEntry() = GameEntry(
        game = game,
        folderName = folderName,
        linkIdentifier = linkIdentifier
    )

    private fun TrackWithGameInfo.toTrackEntry() = TrackEntry(
        trackId = trackId,
        trackNr = trackNr,
        song = song,
        artist = artist,
        releaseDate = releaseDate,
        releaseYear = releaseYear,
        folderName = folderName,
        fileAvailability = try {
            FileAvailability.valueOf(fileAvailability)
        } catch (e: IllegalArgumentException) {
            FileAvailability.NOT_VERIFIED
        }
    )

    // ============================================================
    // File System Methods (kept for MP3 lookup)
    // ============================================================

    fun findFileInSubfolder(
        context: Context,
        rootUri: Uri,
        folderName: String,
        fileName: String
    ): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            rootUri,
            DocumentsContract.getTreeDocumentId(rootUri)
        )

        val subfolderUri = getSubfolderUri(context, childrenUri, folderName) ?: return null

        val fileUri = getFileUriInFolder(context, subfolderUri, fileName)
        return fileUri
    }
    fun getSubfolderUri(context: Context, parentUri: Uri, folderName: String): Uri? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val cursor = context.contentResolver.query(
            parentUri, projection, null, null, null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val docId = it.getString(idIndex)
                val mime = it.getString(mimeIndex)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR && name == folderName) {
                    return DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                }
            }
        }

        return null
    }

    fun getFileUriInFolder(context: Context, folderUri: Uri, filename: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getDocumentId(folderUri)
        )

        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )
        var fileUri: Uri?
        fileUri = null
        cursor?.use {
            val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val fileNameEscaped = Regex.escape(filename)
            val fileNameRegEx = Regex("$fileNameEscaped.*")
            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val docId = it.getString(idIndex)
                if (fileNameRegEx.matches(name)) {
                //if (name.equals(filename, ignoreCase = true)) {
                    fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                }
            }
        }
        if (fileUri == null){
            fileUri = getFileUriWithConfidenceAndBoost(context, folderUri,filename, 0.8)
        }
        return fileUri
    }

    fun findFileInFolder(context: Context, folderUri: Uri, filename: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getDocumentId(folderUri)
        )

        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val docId = it.getString(idIndex)
                if (name.equals(filename, ignoreCase = true)) {
                    return DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                }
            }
        }

        return null
    }
    fun getFolderForTrack(track: TrackEntry): String {
        // Suche das GameEntry, dessen Tracks die TrackEntry enthalten
        for (game in games) {
            val tracks = tracksByFolder[game.folderName] ?: continue
            if (tracks.any { it == track }) {
                return game.folderName
            }
        }
        return ""
    }

    fun getFileUriWithConfidenceAndBoost(
        context: Context,
        folderUri: Uri,
        targetName: String,
        confidenceThreshold: Double = 0.85
    ): Uri? {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        if (!folder.isDirectory) return null

        var bestMatch: DocumentFile? = null
        var bestScore = 0.0

        for (file in folder.listFiles()) {
            if (!file.isFile) continue
            val fileName = file.name?.substringBeforeLast('.') ?: continue
            val score = boostedSimilarity(fileName, targetName)
            if (score > bestScore) {
                bestScore = score
                bestMatch = file
            }
        }

        return if (bestScore >= confidenceThreshold) bestMatch?.uri else null
    }
    fun boostedSimilarity(fileName: String, target: String): Double {
        val cleanFileName = fileName
            .replace(Regex("[^\\p{L}\\p{Nd} ]+"), " ")
            .lowercase()
            .trim()

        val cleanTarget = target
            .replace(Regex("[^\\p{L}\\p{Nd} ]+"), " ")
            .lowercase()
            .trim()

        val similarity = jaroWinkler(cleanFileName, cleanTarget)
        val bonus = if (cleanFileName.contains(cleanTarget)) 0.2 else 0.0

        return min(1.0, similarity + bonus)
    }
    fun jaroWinkler(s1: String, s2: String): Double {
        val mtp = matches(s1, s2)
        val m = mtp[0].toDouble()
        if (m == 0.0) return 0.0
        val j = ((m / s1.length) + (m / s2.length) + ((m - mtp[1]) / m)) / 3
        val jw = if (j < 0.7) j else j + min(0.1, 1.0 / mtp[3]) * mtp[2] * (1 - j)
        return jw
    }

    fun matches(s1: String, s2: String): IntArray {
        val max = if (s1.length > s2.length) s1 else s2
        val min = if (s1.length > s2.length) s2 else s1
        val range = max.length / 2 - 1
        val matchIndexes = IntArray(min.length) { -1 }
        val matchFlags = BooleanArray(max.length)
        var matches = 0

        for (mi in min.indices) {
            val start = maxOf(0, mi - range)
            val end = minOf(mi + range + 1, max.length)
            for (xi in start until end) {
                if (!matchFlags[xi] && min[mi] == max[xi]) {
                    matchIndexes[mi] = xi
                    matchFlags[xi] = true
                    matches++
                    break
                }
            }
        }

        val ms1 = CharArray(matches)
        val ms2 = CharArray(matches)
        var si = 0
        for (i in min.indices) {
            if (matchIndexes[i] != -1) {
                ms1[si] = min[i]
                si++
            }
        }
        si = 0
        for (i in max.indices) {
            if (matchFlags[i]) {
                ms2[si] = max[i]
                si++
            }
        }

        var transpositions = 0
        for (mi in ms1.indices) {
            if (ms1[mi] != ms2[mi]) transpositions++
        }

        var prefix = 0
        for (i in min.indices) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        return intArrayOf(matches, transpositions / 2, prefix, max.length)
    }


}

