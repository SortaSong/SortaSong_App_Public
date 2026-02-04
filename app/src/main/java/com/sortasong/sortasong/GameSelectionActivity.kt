package com.sortasong.sortasong

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sortasong.sortasong.data.CustomGameRepository
import com.sortasong.sortasong.databinding.ActivityGameSelectionBinding

class GameSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameSelectionBinding
    private val checkBoxes = mutableListOf<CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderGameList()

        binding.confirmButton.setOnClickListener {
            val selectedFolderNames = mutableListOf<String>()

            for (checkBox in checkBoxes) {
                if (checkBox.isChecked) {
                    val folderName = checkBox.tag as String
                    selectedFolderNames.add(folderName)
                }
            }

            if (selectedFolderNames.isEmpty()) {
                Toast.makeText(this, getString(R.string.game_selection_no_game_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🧠 Kartenstapel erstellen (includes both official and custom games)
            CardDeckRepository.createDeckFromFolders(selectedFolderNames)

            // 🚀 Spiel starten
            val intent = Intent(this, GamePlayWithoutCardsActivity::class.java)
            startActivity(intent)
        }

    }

    private fun renderGameList() {
        val container = binding.gameListContainer
        container.removeAllViews()
        checkBoxes.clear()

        // Get official games with tracks
        val officialGames = GameRepository.games.filter { game ->
            GameRepository.tracksByFolder[game.folderName]?.isNotEmpty() == true
        }
        
        // Get custom games with tracks
        val customGames = CustomGameRepository.customGames.filter { game ->
            CustomGameRepository.customTracksByFolder[game.folderName]?.isNotEmpty() == true
        }

        // Render official games
        officialGames.forEach { game ->
            addGameCheckbox(container, game.game, game.folderName, isCustom = false)
        }
        
        // Render custom games
        customGames.forEach { game ->
            addGameCheckbox(container, game.game, game.folderName, isCustom = true)
        }
    }
    
    private fun addGameCheckbox(container: LinearLayout, gameName: String, folderName: String, isCustom: Boolean) {
        val displayName = if (isCustom) "⭐ $gameName" else gameName
        val checkBox = CheckBox(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = displayName
            tag = folderName  // Store folder name for deck creation
            setTextColor(if (isCustom) Color.parseColor("#FFD700") else Color.WHITE)  // Gold for custom
            textSize = 20f
            buttonTintList = ColorStateList.valueOf(Color.WHITE)
        }
        checkBoxes.add(checkBox)
        container.addView(checkBox)
    }

}
