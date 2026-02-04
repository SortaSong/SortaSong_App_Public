package com.sortasong.sortasong

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sortasong.sortasong.data.CustomGameInfo
import com.sortasong.sortasong.data.CustomGameRepository
import com.sortasong.sortasong.databinding.ActivityManageCustomGamesBinding
import com.sortasong.sortasong.databinding.ItemCustomGameBinding

class ManageCustomGamesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageCustomGamesBinding
    private lateinit var adapter: CustomGamesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityManageCustomGamesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        refreshGamesList()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        
        // PC editor link
        binding.pcEditorLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sortasong.github.io/SortaSong_App_Public/editor/"))
            startActivity(intent)
        }
        
        binding.createButton.setOnClickListener {
            startActivity(Intent(this, CustomGameEditorActivity::class.java))
        }
        
        binding.browseCommunityButton.setOnClickListener {
            startActivity(Intent(this, BrowseCommunityGamesActivity::class.java))
        }
        
        binding.mySubmissionsButton.setOnClickListener {
            startActivity(Intent(this, MySubmissionsActivity::class.java))
        }
        
        adapter = CustomGamesAdapter(
            onEdit = { gameInfo ->
                val intent = Intent(this, CustomGameEditorActivity::class.java)
                intent.putExtra(CustomGameEditorActivity.EXTRA_FOLDER_NAME, gameInfo.folderName)
                startActivity(intent)
            },
            onDelete = { gameInfo ->
                showDeleteConfirmation(gameInfo)
            }
        )
        
        binding.gamesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.gamesRecyclerView.adapter = adapter
    }

    private fun refreshGamesList() {
        val customGames = CustomGameRepository.getCustomGameInfos()
        adapter.updateGames(customGames)
        
        if (customGames.isEmpty()) {
            binding.gamesRecyclerView.visibility = View.GONE
            binding.emptyStateText.visibility = View.VISIBLE
        } else {
            binding.gamesRecyclerView.visibility = View.VISIBLE
            binding.emptyStateText.visibility = View.GONE
        }
    }

    private fun showDeleteConfirmation(gameInfo: CustomGameInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.manage_games_delete_confirm_title)
            .setMessage(getString(R.string.manage_games_delete_confirm_message, gameInfo.game))
            .setPositiveButton(R.string.manage_games_delete) { _, _ ->
                CustomGameRepository.deleteCustomGame(this, gameInfo.folderName)
                Toast.makeText(this, R.string.manage_games_deleted, Toast.LENGTH_SHORT).show()
                refreshGamesList()
            }
            .setNegativeButton(R.string.track_edit_cancel, null)
            .show()
    }

    // Adapter for custom games list
    class CustomGamesAdapter(
        private val onEdit: (CustomGameInfo) -> Unit,
        private val onDelete: (CustomGameInfo) -> Unit
    ) : RecyclerView.Adapter<CustomGamesAdapter.ViewHolder>() {

        private val games = mutableListOf<CustomGameInfo>()

        fun updateGames(newGames: List<CustomGameInfo>) {
            games.clear()
            games.addAll(newGames)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCustomGameBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(games[position])
        }

        override fun getItemCount() = games.size

        inner class ViewHolder(private val binding: ItemCustomGameBinding) : 
            RecyclerView.ViewHolder(binding.root) {

            fun bind(gameInfo: CustomGameInfo) {
                binding.gameNameText.text = gameInfo.game
                binding.trackCountText.text = binding.root.context.getString(
                    R.string.track_editor_track_count, 
                    gameInfo.tracks.size
                )
                
                binding.editButton.setOnClickListener { onEdit(gameInfo) }
                binding.deleteButton.setOnClickListener { onDelete(gameInfo) }
            }
        }
    }
}
