package com.sortasong.sortasong

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import android.media.MediaPlayer
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.animation.AnimatorListenerAdapter
class PlaybackActivity : AppCompatActivity() {


    private val equalizerAnimators = mutableListOf<ObjectAnimator>()

    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    private lateinit var nextButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var vinylView: ImageView
    private lateinit var armView: ImageView
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler()
    private lateinit var updateSeekbar: Runnable
    private var animator: ValueAnimator? = null
    private lateinit var vinylAnimator: ObjectAnimator

    private fun startVinylRotation() {
        vinylAnimator = ObjectAnimator.ofFloat(vinylView, View.ROTATION, 0f, 360f).apply {
            duration = 5000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }
    private fun moveArmToStartPosition(onFinished: () -> Unit) {
        val animator = ObjectAnimator.ofFloat(armView, View.ROTATION, -20f, -5f).apply {
            duration = 1500
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onFinished() // ⬅️ Starte Musik erst danach
                }
            })
        }
        animator.start()
    }


    private fun moveArmToPlayingPosition(progressFraction: Float) {
        val startAngle = -5f
        val endAngle = 20f
        val targetAngle = startAngle + (endAngle - startAngle) * progressFraction

        armView.rotation = targetAngle
    }

    private fun moveArmToRestPosition() {
        ObjectAnimator.ofFloat(armView, View.ROTATION, armView.rotation, -20f).apply {
            duration = 500
            interpolator = AccelerateInterpolator()
            start()
        }
    }

    private fun stopVinylRotation() {
        vinylAnimator.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)


        playButton = findViewById(R.id.playButton)
        pauseButton = findViewById(R.id.pauseButton)
        nextButton = findViewById(R.id.nextButton)
        seekBar = findViewById(R.id.seekBar)
        armView = findViewById(R.id.armView)
        vinylView = findViewById(R.id.vinylView)

        armView.post {
            // z. B. Drehpunkt links unten
            armView.pivotX = armView.width.toFloat()
            //armView.pivotY = armView.height.toFloat()
            armView.rotation = -20f
            armView.translationY = -350f
        }

        val uriString = intent.getStringExtra("MP3_URI")
        val mp3Uri = uriString?.toUri()

        if (mp3Uri != null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@PlaybackActivity, mp3Uri)
                prepare()
                //start()

                setOnCompletionListener {
                    stopPlayback()
                    moveArmToRestPosition()
                    stopVinylRotation()
                }
                moveArmToStartPosition {
                    mediaPlayer?.start()
                    startVinylRotation()
                }

            }

            seekBar.max = mediaPlayer!!.duration

            updateSeekbar = object : Runnable {
                override fun run() {
                    seekBar.progress = mediaPlayer?.currentPosition ?: 0
                    handler.postDelayed(this, 500)
                    val progress = mediaPlayer?.currentPosition?.toFloat() ?: 0f
                    val total = mediaPlayer?.duration?.toFloat() ?: 1f
                    if (mediaPlayer?.isPlaying == true) {
                        moveArmToPlayingPosition(progress / total)
                    }
                }
            }
            handler.post(updateSeekbar)
        }

        playButton.setOnClickListener {

            moveArmToStartPosition{
                startVinylRotation()
                mediaPlayer?.start()
            }
        }

        pauseButton.setOnClickListener {
            mediaPlayer?.pause()
            stopVinylRotation()
        }

        nextButton.setOnClickListener {
            stopPlayback()
            stopVinylRotation()
            finish() // Zurück zur Scan-Maske (QrScanActivity bleibt im Stack)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }






    private fun stopPlayback() {
        handler.removeCallbacks(updateSeekbar)
        mediaPlayer?.release()
        mediaPlayer = null
        animator?.cancel()
        animator = null
    }

    override fun onDestroy() {
        stopPlayback()
        stopVinylRotation()
        super.onDestroy()
    }
}
