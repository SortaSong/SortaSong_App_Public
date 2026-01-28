package com.sortasong.sortasong

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GamePlayWithoutCardsActivity : AppCompatActivity() {
    private lateinit var startGameButton: Button
    private var gameStarted = false
    private lateinit var currentPlayerText: TextView
    private lateinit var deckCountText: TextView
    private lateinit var playPauseButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var confirmButton: Button
    private lateinit var moveUpButton: Button
    private lateinit var NextButton: Button
    private lateinit var playBackErrorText: TextView
    private lateinit var oldestYear: TextView
    private lateinit var youngestYear: TextView
    private lateinit var moveDownButton: Button
    private lateinit var playerLeaderboardContainer: LinearLayout
    private lateinit var playbackError: LinearLayout
    private lateinit var playbackCard: LinearLayout
    private lateinit var sortContainer: FrameLayout
    private lateinit var trackRecyclerView: RecyclerView

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())

    private var currentPlayerIndex = 0
    private val players get() = PlayerRepository.players
    private val currentPlayer get() = players.getOrNull(currentPlayerIndex)
    private val selectedRootUri: Uri? by lazy {
        getSharedPreferences("settings", MODE_PRIVATE)
            .getString("folder_uri", null)
            ?.toUri()
    }

    private var currentTrack: TrackEntry? = null
    private var insertIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gameplay_without_cards)

        currentPlayerText = findViewById(R.id.currentPlayerText)
        deckCountText = findViewById(R.id.deckCountText)
        playPauseButton = findViewById(R.id.playPauseButton)
        seekBar = findViewById(R.id.seekBar)
        confirmButton = findViewById(R.id.confirmButton)
        moveUpButton = findViewById(R.id.moveUpButton)
        moveDownButton = findViewById(R.id.moveDownButton)
        playerLeaderboardContainer = findViewById(R.id.playerLeaderboardContainer)
        startGameButton = findViewById(R.id.startGameButton)

        trackRecyclerView = findViewById(R.id.trackRecyclerView)
        playBackErrorText = findViewById(R.id.playBackErrorText)
        playbackError = findViewById(R.id.playbackError)
        playbackCard = findViewById(R.id.playbackCard)
        NextButton = findViewById(R.id.NextButton)
        sortContainer = findViewById(R.id.sortContainer)
        oldestYear = findViewById(R.id.oldestYear)
        youngestYear = findViewById(R.id.youngestYear)
        oldestYear.textSize = 18f
        oldestYear.setTextColor(Color.WHITE)
        oldestYear.setPadding(32, 32, 32, 32)
        val background = GradientDrawable().apply {
            setColor("#222222".toColorInt()) // dunkler Hintergrund
            cornerRadius = 24f
            setStroke(3, Color.WHITE)             // weißer Rand
        }

        oldestYear.background = background
        youngestYear.textSize = 18f
        youngestYear.setTextColor(Color.WHITE)
        youngestYear.setPadding(32, 32, 32, 32)

        youngestYear.background = background
        youngestYear.visibility = View.GONE
        oldestYear.visibility = View.GONE

        NextButton.setOnClickListener { drawNextCard() }

        startGameButton.setOnClickListener { startGame() }

        playPauseButton.setOnClickListener {
            if (isPlaying) pauseMusic() else startMusic()
        }

        confirmButton.setOnClickListener { onConfirmTrackPosition() }

        moveUpButton.setOnClickListener {
            if (insertIndex > 0) {
                insertIndex--
                refreshSortList()
            }
        }

        moveDownButton.setOnClickListener {
            val maxIndex = currentPlayer?.tracks?.size ?: 0
            if (insertIndex < maxIndex) {
                insertIndex++
                refreshSortList()
            }
        }
        updateDeckCount()
        updatePlayerUI()
    }
    private fun showPopup(message: String, InsertedTrack: TrackEntry) {
        val inflater = layoutInflater
        val root = findViewById<ViewGroup>(android.R.id.content)
        val popupView = inflater.inflate(R.layout.popup_result, root, false)

        val messageView = popupView.findViewById<TextView>(R.id.popupMessage)
        val Year = popupView.findViewById<TextView>(R.id.Year)
        val Artist = popupView.findViewById<TextView>(R.id.Artist)
        val Song = popupView.findViewById<TextView>(R.id.Song)
        messageView.text = message
        Year.text = InsertedTrack.releaseDate.takeLast(4)
        Artist.text = InsertedTrack.artist
        Song.text = InsertedTrack.song

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
            true
        )

        // Optional: Hintergrund abdunkeln
        val bg = AppCompatResources.getDrawable(this, android.R.drawable.alert_light_frame)
        popupWindow.setBackgroundDrawable(bg)
        popupWindow.elevation = 10f
        popupView.setOnClickListener {
            popupWindow.dismiss()
            drawNextCard()
            updatePlayerUI()
        }
        // Zeige das Popup zentriert über dem Root-Layout
        popupWindow.showAtLocation(findViewById(R.id.rootLayout), Gravity.CENTER, 0, 0)


    }
    private fun startGame() {
        players.forEach { player ->
            val track = CardDeckRepository.drawNextCard()
            if (track != null) {
                player.tracks.add(track)
            }
        }


        gameStarted = true
        startGameButton.visibility = View.GONE
        sortContainer.visibility = View.VISIBLE
        youngestYear.visibility = View.VISIBLE
        oldestYear.visibility = View.VISIBLE
        currentPlayerIndex = 0
        drawNextCard()
        updatePlayerUI()
    }

    private fun drawNextCard() {
        playbackError.visibility = View.GONE
        confirmButton.visibility = View.VISIBLE
        moveUpButton.visibility = View.VISIBLE
        moveDownButton.visibility = View.VISIBLE

        currentTrack = CardDeckRepository.drawNextCard()
        updateDeckCount()
        resetMediaPlayer()

        if (currentTrack == null) {
            Toast.makeText(this,
                getString(R.string.activity_gameplay_without_cards_noMoreCards), Toast.LENGTH_LONG).show()
            confirmButton.isEnabled = false
            return
        }

        val folder = GameRepository.getFolderForTrack(currentTrack!!)
        val fileName = "${currentTrack!!.artist} - ${currentTrack!!.song}"
        val mp3Uri = selectedRootUri?.let {
            GameRepository.findFileInSubfolder(this, it, folder, fileName)
        }
        Log.d("GamePlayWithoutCardsActivity","Current Song: ${currentTrack!!.releaseDate} - ${currentTrack!!.artist} - ${currentTrack!!.song}")

        if (mp3Uri != null) {
            playbackCard.visibility = View.VISIBLE
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@GamePlayWithoutCardsActivity, mp3Uri)
                prepare()
            }
            seekBar.max = mediaPlayer?.duration ?: 0
            seekBar.isEnabled = true
            playPauseButton.isEnabled = true

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) mediaPlayer?.seekTo(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        } else {
            playbackCard.visibility = View.GONE
            val combined = "$folder/$fileName"
            playBackErrorText.text = getString(R.string.gameplay_file_not_found, combined)
             playbackError.visibility = View.VISIBLE
             confirmButton.visibility = View.GONE
             moveUpButton.visibility = View.GONE
             moveDownButton.visibility = View.GONE

             //Toast.makeText(this, "$fileName\ud83c\udfb5 MP3 nicht gefunden!", Toast.LENGTH_SHORT).show()
         }

         setupTrackSortList()
     }

     private fun setupTrackSortList() {
         val existing = currentPlayer?.tracks?.sortedBy { it.releaseYear } ?: emptyList()
         var scrollIndex: Int
         insertIndex = existing.size / 2
         if (insertIndex < existing.count()){
             scrollIndex = insertIndex + 1
         } else {
             scrollIndex = insertIndex
         }


         trackRecyclerView.layoutManager = LinearLayoutManager(this)
         trackRecyclerView.adapter = TrackSortAdapter(existing, insertIndex)
         trackRecyclerView.smoothScrollToPosition(scrollIndex)
         Log.d("GamePlayWithoutCardsActivity", "ScrollPosition: ${scrollIndex}")
         updatePlayerUI()
     }

     private fun refreshSortList() {
         val sorted = currentPlayer?.tracks?.sortedBy { it.releaseYear } ?: emptyList()
         var scrollIndex : Int
         trackRecyclerView.adapter = TrackSortAdapter(sorted, insertIndex)

         if (insertIndex < sorted.count()){
             scrollIndex = insertIndex + 1
         } else {
             scrollIndex = insertIndex
         }
         Log.d("GamePlayWihtoutCardsActivity", "ScrollPosition: ${scrollIndex}")
         trackRecyclerView.smoothScrollToPosition(scrollIndex)
     }

     private fun onConfirmTrackPosition() {
        val newTrack = currentTrack ?: return
        val sorted = currentPlayer?.tracks?.sortedBy { it.releaseYear }?.toMutableList() ?: return

        val isCorrect = when {
            insertIndex == 0 -> newTrack.releaseYear!! <= sorted[0].releaseYear!!
            insertIndex == sorted.size -> newTrack.releaseYear!! >= sorted.last().releaseYear!!
            else -> {
                val before = sorted[insertIndex - 1].releaseYear!!
                val after = sorted[insertIndex].releaseYear!!
                newTrack.releaseYear!! >= before && newTrack.releaseYear <= after
            }
        }

        if (isCorrect) {
            sorted.add(insertIndex, newTrack)
            currentPlayer?.tracks?.clear()
            currentPlayer?.tracks?.addAll(sorted)
            showPopup(getString(R.string.gameplay_correct), newTrack)
            //Toast.makeText(this, "\u2705 Richtig!", Toast.LENGTH_SHORT).show()
        } else {
            showPopup(getString(R.string.gameplay_incorrectly_sorted), newTrack)
            //Toast.makeText(this, "\u274c Falsch einsortiert.", Toast.LENGTH_SHORT).show()
        }

        currentPlayerIndex = (currentPlayerIndex + 1) % players.size

        updatePlayerUI()
    }

    private fun updateDeckCount() {
        val remaining = CardDeckRepository.cards.size
        deckCountText.text = getString(R.string.activity_gameplay_without_cards_cards_left_in_game, remaining)
    }

    private fun updatePlayerUI() {
        currentPlayerText.text = getString(R.string.gameplay_player_turn_with_name, currentPlayer?.name ?: getString(R.string.gameplay_no_player))

        playerLeaderboardContainer.removeAllViews()
        val sortedPlayers = players.sortedByDescending { it.tracks.size }
        val textSizes = listOf(22.4f, 19.6f, 16.8f, 14f, 11.2f) // Adjust as needed

        for ((index, player) in sortedPlayers.withIndex()) {
            val playerView = TextView(this).apply {
                text = getString(R.string.gameplay_player_with_count, player.name, player.tracks.size)
                textSize = if (index < textSizes.size) textSizes[index] else textSizes.last()
                setTextColor(Color.WHITE)
                setPadding(8, 8, 8, 8)
                // Highlight current player
                if (player == currentPlayer) {
                    setTypeface(null, Typeface.BOLD)
                }
            }
            playerLeaderboardContainer.addView(playerView)
        }
    }

    private fun startMusic() {
        mediaPlayer?.start()
        playPauseButton.text = getString(R.string.gameplay_play_pause_pause)
        isPlaying = true
        updateSeekBar()
    }

    private fun pauseMusic() {
        mediaPlayer?.pause()
        playPauseButton.text = getString(R.string.gameplay_play_pause_play)
        isPlaying = false
    }

    private fun resetMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        playPauseButton.text = getString(R.string.gameplay_play_pause_play)
        seekBar.progress = 0
        seekBar.isEnabled = false
        playPauseButton.isEnabled = false
    }

    private fun updateSeekBar() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (mediaPlayer != null && isPlaying) {
                    seekBar.progress = mediaPlayer!!.currentPosition
                    handler.postDelayed(this, 500)
                }
            }
        }, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacksAndMessages(null)
        players.forEach { player ->
            player.tracks.clear()
        }
        CardDeckRepository.cards.clear()
    }
}
