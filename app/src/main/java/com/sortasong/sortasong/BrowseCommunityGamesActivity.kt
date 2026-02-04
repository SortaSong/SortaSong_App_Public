package com.sortasong.sortasong

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.GsonBuilder
import com.sortasong.sortasong.data.CommunityGame
import com.sortasong.sortasong.data.CommunityGameService
import com.sortasong.sortasong.data.CommunityGamesResult
import com.sortasong.sortasong.data.CustomGameRepository
import com.sortasong.sortasong.data.SortOrder
import com.sortasong.sortasong.data.VoteResult
import com.sortasong.sortasong.databinding.ActivityBrowseCommunityGamesBinding
import com.sortasong.sortasong.databinding.ItemCommunityGameBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowseCommunityGamesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowseCommunityGamesBinding
    private lateinit var adapter: CommunityGamesAdapter
    private var allGames: List<CommunityGame> = emptyList()
    private var currentSortOrder = SortOrder.NEWEST
    private val votedGames = mutableSetOf<String>()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityBrowseCommunityGamesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        
        loadVotedGames()
        setupUI()
        loadGames()
    }

    private fun loadVotedGames() {
        val prefs = getSharedPreferences("community_votes", Context.MODE_PRIVATE)
        val voted = prefs.getStringSet("voted_games", emptySet()) ?: emptySet()
        votedGames.addAll(voted)
    }

    private fun saveVotedGame(gameId: String) {
        votedGames.add(gameId)
        val prefs = getSharedPreferences("community_votes", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("voted_games", votedGames).apply()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        binding.retryButton.setOnClickListener { loadGames() }
        
        // Sort spinner
        val sortOptions = arrayOf(
            getString(R.string.browse_community_sort_newest),
            getString(R.string.browse_community_sort_votes),
            getString(R.string.browse_community_sort_downloads)
        )
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.sortSpinner.adapter = spinnerAdapter
        binding.sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSortOrder = when (position) {
                    0 -> SortOrder.NEWEST
                    1 -> SortOrder.MOST_VOTES
                    2 -> SortOrder.MOST_DOWNLOADS
                    else -> SortOrder.NEWEST
                }
                applyFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Search
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })
        
        // Adapter
        adapter = CommunityGamesAdapter(
            onVote = { game -> voteForGame(game) },
            onDownload = { game -> showDownloadDialog(game) },
            isVoted = { gameId -> gameId in votedGames }
        )
        
        binding.gamesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.gamesRecyclerView.adapter = adapter
    }

    private fun loadGames() {
        showLoading()
        
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CommunityGameService.fetchCommunityGames(currentSortOrder)
            }
            
            when (result) {
                is CommunityGamesResult.Success -> {
                    allGames = result.games
                    applyFilter()
                }
                is CommunityGamesResult.Error -> {
                    showError(result.message)
                }
            }
        }
    }

    private fun applyFilter() {
        val query = binding.searchInput.text.toString().lowercase()
        
        var filtered = if (query.isBlank()) {
            allGames
        } else {
            allGames.filter { 
                it.gameName.lowercase().contains(query) ||
                (it.submittedByName?.lowercase()?.contains(query) == true)
            }
        }
        
        // Sort
        filtered = when (currentSortOrder) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOrder.MOST_VOTES -> filtered.sortedByDescending { it.voteCount }
            SortOrder.MOST_DOWNLOADS -> filtered.sortedByDescending { it.downloadCount }
        }
        
        adapter.updateGames(filtered)
        
        if (filtered.isEmpty()) {
            showEmpty()
        } else {
            showContent()
        }
    }

    private fun showLoading() {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
        binding.gamesRecyclerView.visibility = View.GONE
        binding.emptyStateText.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loadingLayout.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
        binding.gamesRecyclerView.visibility = View.GONE
        binding.emptyStateText.visibility = View.GONE
        binding.errorText.text = getString(R.string.browse_community_error, message)
    }

    private fun showContent() {
        binding.loadingLayout.visibility = View.GONE
        binding.errorLayout.visibility = View.GONE
        binding.gamesRecyclerView.visibility = View.VISIBLE
        binding.emptyStateText.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.loadingLayout.visibility = View.GONE
        binding.errorLayout.visibility = View.GONE
        binding.gamesRecyclerView.visibility = View.GONE
        binding.emptyStateText.visibility = View.VISIBLE
    }

    private fun voteForGame(game: CommunityGame) {
        if (game.id in votedGames) {
            Toast.makeText(this, getString(R.string.browse_community_voted), Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CommunityGameService.voteForGame(game.id)
            }
            
            when (result) {
                is VoteResult.Success -> {
                    saveVotedGame(game.id)
                    Toast.makeText(this@BrowseCommunityGamesActivity, 
                        getString(R.string.browse_community_voted), Toast.LENGTH_SHORT).show()
                    // Reload to update counts
                    loadGames()
                }
                is VoteResult.AlreadyVoted -> {
                    saveVotedGame(game.id)
                    Toast.makeText(this@BrowseCommunityGamesActivity, 
                        getString(R.string.browse_community_voted), Toast.LENGTH_SHORT).show()
                }
                is VoteResult.Error -> {
                    Toast.makeText(this@BrowseCommunityGamesActivity, 
                        result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDownloadDialog(game: CommunityGame) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val rootUri = prefs.getString("folder_uri", null)
        
        if (rootUri == null) {
            Toast.makeText(this, getString(R.string.browse_community_no_folder), Toast.LENGTH_LONG).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.browse_community_download_title)
            .setMessage(getString(R.string.browse_community_download_message, game.gameName, game.folderName))
            .setPositiveButton(R.string.browse_community_download_confirm) { _, _ ->
                downloadGame(game, rootUri)
            }
            .setNegativeButton(R.string.share_dialog_cancel, null)
            .show()
    }

    private fun downloadGame(game: CommunityGame, rootUriString: String) {
        Toast.makeText(this, getString(R.string.browse_community_downloading), Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val rootUri = rootUriString.toUri()
                
                withContext(Dispatchers.IO) {
                    val rootFolder = DocumentFile.fromTreeUri(this@BrowseCommunityGamesActivity, rootUri)
                        ?: throw Exception("Cannot access root folder")
                    
                    // Create or find the game folder
                    var gameFolder = rootFolder.findFile(game.folderName)
                    if (gameFolder == null) {
                        gameFolder = rootFolder.createDirectory(game.folderName)
                            ?: throw Exception("Cannot create folder")
                    }
                    
                    // Delete existing game_info.json if present
                    gameFolder.findFile("game_info.json")?.delete()
                    
                    // Create game_info.json
                    val gameInfoFile = gameFolder.createFile("application/json", "game_info.json")
                        ?: throw Exception("Cannot create game_info.json")
                    
                    contentResolver.openOutputStream(gameInfoFile.uri)?.use { output ->
                        output.write(gson.toJson(game.gameData).toByteArray(Charsets.UTF_8))
                    }
                    
                    // Increment download count
                    CommunityGameService.incrementDownload(game.id)
                }
                
                // Refresh custom games
                val officialFolderNames = GameRepository.games.map { it.folderName }.toSet()
                CustomGameRepository.setOfficialFolderNames(officialFolderNames)
                CustomGameRepository.scanForCustomGames(this@BrowseCommunityGamesActivity, rootUriString.toUri())
                
                AlertDialog.Builder(this@BrowseCommunityGamesActivity)
                    .setTitle("✅")
                    .setMessage(getString(R.string.browse_community_download_success, game.folderName))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                
            } catch (e: Exception) {
                Toast.makeText(this@BrowseCommunityGamesActivity,
                    getString(R.string.browse_community_download_error, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Adapter
    class CommunityGamesAdapter(
        private val onVote: (CommunityGame) -> Unit,
        private val onDownload: (CommunityGame) -> Unit,
        private val isVoted: (String) -> Boolean
    ) : RecyclerView.Adapter<CommunityGamesAdapter.ViewHolder>() {

        private val games = mutableListOf<CommunityGame>()

        fun updateGames(newGames: List<CommunityGame>) {
            games.clear()
            games.addAll(newGames)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCommunityGameBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(games[position])
        }

        override fun getItemCount() = games.size

        inner class ViewHolder(private val binding: ItemCommunityGameBinding) : 
            RecyclerView.ViewHolder(binding.root) {

            fun bind(game: CommunityGame) {
                val context = binding.root.context
                
                binding.gameNameText.text = game.gameName
                
                // Submitter
                val submitter = game.submittedByName?.takeIf { it.isNotBlank() } ?: "Anonymous"
                binding.submitterText.text = context.getString(R.string.browse_community_by, submitter)
                
                // Track count
                binding.trackCountText.text = context.getString(R.string.browse_community_tracks, game.trackCount)
                
                // Description
                if (!game.description.isNullOrBlank()) {
                    binding.descriptionText.text = game.description
                    binding.descriptionText.visibility = View.VISIBLE
                } else {
                    binding.descriptionText.visibility = View.GONE
                }
                
                // Stats
                val voteText = if (isVoted(game.id)) "✅ ${game.voteCount}" else "👍 ${game.voteCount}"
                binding.voteButton.text = voteText
                binding.downloadsText.text = "⬇️ ${game.downloadCount}"
                
                // Actions
                binding.voteButton.setOnClickListener { onVote(game) }
                binding.downloadButton.setOnClickListener { onDownload(game) }
            }
        }
    }
}
