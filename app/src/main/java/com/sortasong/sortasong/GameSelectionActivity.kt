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
            val selectedGames = mutableListOf<GameEntry>()

            for (i in 0 until checkBoxes.count()) {
                val checkBox = checkBoxes[i]
                if (checkBox.isChecked) {
                    val gameName = checkBox.tag as String
                    GameRepository.games.find { it.game == gameName }?.let {
                        selectedGames.add(it)
                    }
                }
            }
            val selectedFolderNames = selectedGames.map { it.folderName }

            if (selectedFolderNames.isEmpty()) {
                Toast.makeText(this, getString(R.string.game_selection_no_game_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🧠 Kartenstapel erstellen
            CardDeckRepository.createDeckFromFolders(selectedFolderNames)

            // 🚀 Spiel starten
            val intent = Intent(this, GamePlayWithoutCardsActivity::class.java)
            startActivity(intent)
        }

    }

    private fun renderGameList() {
        val container = binding.gameListContainer
        container.removeAllViews()

        val validGames = GameRepository.games.filter { game ->
            GameRepository.tracksByFolder[game.folderName]?.isNotEmpty() == true
        }

        validGames.forEach { game ->
            val checkBox = CheckBox(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = game.game
                tag = game.game
                setTextColor(Color.WHITE)
                textSize = 20f
                buttonTintList = ColorStateList.valueOf(Color.WHITE)
            }
            checkBoxes.add(checkBox)
            container.addView(checkBox)
        }
    }

}
