package com.sortasong.sortasong

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

//@OptIn(ExperimentalGetImage::class)
class QrScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var resultTextView: TextView
    private lateinit var cameraExecutor: ExecutorService
    private var scanning = true
    private var mediaPlayer: MediaPlayer? = null

    private val playWithCards by lazy {
        intent.getBooleanExtra("WITH_CARDS", false)
    }
    override fun onResume() {
        super.onResume()
        scanning = true
    }
    override fun onPause() {
        super.onPause()
        scanning = false
    }

    private val selectedRootUri: Uri? by lazy {
        getSharedPreferences("settings", MODE_PRIVATE)
            .getString("folder_uri", null)
            ?.toUri()
    }

    companion object {
        private const val CAMERA_PERMISSION_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)

        previewView = findViewById(R.id.previewView)
        resultTextView = findViewById(R.id.resultTextView)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (GameRepository.games.isEmpty()) {
            resultTextView.text = getString(R.string.qrscan_game_not_loaded)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }

        resultTextView.append("\n\n" + getString(R.string.qrscan_mode_line, if (playWithCards) getString(R.string.activity_start_with_cards) else getString(R.string.activity_start_without_cards)))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val scannerOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            val scanner = BarcodeScanning.getClient(scannerOptions)

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(scanner, imageProxy)
            }

            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Fehler beim Binden der Kamera", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    //@OptIn(ExperimentalGetImage::class)
    @Suppress("OPT_IN_USAGE")
    private fun processImageProxy(
        scanner: BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && scanning) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { scannedLink ->
                            scanning = false

                            val (match, trackNr) = findBestMatch(scannedLink, GameRepository.games)

                            runOnUiThread {
                                if (match != null && trackNr != null) {
                                    val folder = match.folderName
                                    val entry = GameRepository.tracksByFolder[folder]?.find {
                                        it.trackNr == trackNr
                                    }

                                    if (entry != null) {
                                        val fileName = "${entry.artist} - ${entry.song}"
                                            .replace(Regex("[\\\\/:*?\"<>|]"), "").trim()

                                        resultTextView.text = getString(
                                            R.string.qrscan_game_detected_template,
                                            match.game,
                                            folder,
                                            trackNr,
                                            fileName
                                        )

                                        selectedRootUri?.let { rootUri ->
                                            val mp3Uri = GameRepository.findFileInSubfolder(
                                                this,
                                                rootUri,
                                                folder,
                                                fileName
                                            )
                                            if (mp3Uri != null) {
                                                if (playWithCards) {
                                                    // → Weiter zu PlaybackActivity
                                                    val intent = Intent(this, PlaybackActivity::class.java).apply {
                                                        putExtra("MP3_URI", mp3Uri.toString())
                                                        putExtra("TITLE", "${entry.artist} - ${entry.song}")
                                                    }
                                                    startActivity(intent)
                                                    //finish()
                                                } else {
                                                    playMp3(mp3Uri)
                                                }
                                            } else {
                                                resultTextView.append("\n" + getString(R.string.qrscan_mp3_not_found))
                                            }
                                        }
                                    } else {
                                        resultTextView.text = getString(R.string.qrscan_track_not_found_in_csv)
                                    }
                                } else {
                                    resultTextView.text = getString(R.string.qrscan_no_link_identifier)
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("MLKit", "Scanfehler", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun playMp3(mp3Uri: Uri) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@QrScanActivity, mp3Uri)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { it.release() }
            prepareAsync()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                resultTextView.text = getString(R.string.qrscan_camera_denied)
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    // 🔍 Lokale Hilfsfunktion für bestes Link-Matching
    private fun findBestMatch(scannedUrl: String, entries: List<GameEntry>): Pair<GameEntry?, String?> {
        var bestMatch: GameEntry? = null
        var maxMatchLength = -1

        for (entry in entries) {
            if (scannedUrl.startsWith(entry.linkIdentifier) && entry.linkIdentifier.length > maxMatchLength) {
                bestMatch = entry
                maxMatchLength = entry.linkIdentifier.length
            }
        }

        val trackNumber = if (bestMatch != null) {
            scannedUrl.removePrefix(bestMatch.linkIdentifier)
                .take(5)
        } else null

        return Pair(bestMatch, trackNumber)
    }
}
