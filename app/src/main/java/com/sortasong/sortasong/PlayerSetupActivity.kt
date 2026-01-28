package com.sortasong.sortasong


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sortasong.sortasong.databinding.ActivityPlayerSetupBinding
import kotlin.collections.any
import kotlin.collections.forEachIndexed
import kotlin.jvm.java
import kotlin.text.isNotEmpty
import kotlin.text.trim

class PlayerSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerSetupBinding

    companion object {
        private const val REQUEST_CODE_GAME_SELECTION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PlayerRepository.load(this)
        renderPlayerList()

        binding.addPlayerButton.setOnClickListener {
            val playerName = binding.playerNameInput.text.toString().trim()
            if (playerName.isNotEmpty()) {
                if (PlayerRepository.players.any { it.name == playerName }) {
                    Toast.makeText(this, getString(R.string.player_setup_name_exists), Toast.LENGTH_SHORT).show()
                } else {
                    PlayerRepository.players.add(Player(playerName))
                    PlayerRepository.save(this)
                    binding.playerNameInput.setText("")
                    renderPlayerList()
                }
            } else {
                Toast.makeText(this, getString(R.string.player_setup_enter_name), Toast.LENGTH_SHORT).show()
            }
        }

        binding.selectGamesButton.text = getString(R.string.activity_player_setup_select_games)
        binding.selectGamesButton.setOnClickListener {
            val intent = Intent(this, GameSelectionActivity::class.java)
            startActivity(intent)
        }
    }

    private fun renderPlayerList() {
        binding.playerList.removeAllViews()
        val inflater = LayoutInflater.from(this)

        PlayerRepository.players.forEachIndexed { index, player ->
            val playerView = inflater.inflate(R.layout.item_player, binding.playerList, false) as ViewGroup

            val nameField = playerView.findViewById<EditText>(R.id.playerNameField)
            nameField.setText(player.name)
            nameField.isEnabled = false


            val deleteButton = playerView.findViewById<Button>(R.id.deletePlayerButton)
            deleteButton.setOnClickListener {
                PlayerRepository.players.removeAt(index)
                PlayerRepository.save(this)
                renderPlayerList()
            }

            binding.playerList.addView(playerView)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_GAME_SELECTION && resultCode == RESULT_OK) {
            val selectedFolders = data?.getStringArrayListExtra("selectedFolderNames") ?: arrayListOf()
            Toast.makeText(this, getString(R.string.player_setup_selected_games_prefix, selectedFolders.toString()), Toast.LENGTH_LONG).show()

            // TODO: Hier weiterverarbeiten (z.B. Filter für Tracks setzen, Spiel starten, etc.)
        }
    }
}
