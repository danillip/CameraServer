package com.danil.cameraserver

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.danil.cameraserver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer
    private lateinit var prefs: SharedPreferences
    private var serverRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        initPlayer()

        binding.startBtn.setOnClickListener {
            if (serverRunning) {
                stopService(Intent(this, ServerService::class.java))
            } else {
                startForegroundService(Intent(this, ServerService::class.java))
            }
            serverRunning = !serverRunning
            binding.startBtn.text = if (serverRunning) "Stop Server" else "Start Server"
        }

        binding.settingsBtn.setOnClickListener { showSettingsDialog() }
    }

    private fun initPlayer() {
        val ip = prefs.getString("camera_ip", "192.168.0.44")
        player = ExoPlayer.Builder(this).build().also {
            binding.playerView.player = it
            it.setMediaItem(MediaItem.fromUri("rtsp://user:pass@$ip/stream"))
            it.prepare()
            it.playWhenReady = true
        }
    }

    private fun showSettingsDialog() {
        val edit = EditText(this)
        edit.setText(prefs.getString("camera_ip", "192.168.0.44"))
        MaterialAlertDialogBuilder(this)
            .setTitle("Camera IP")
            .setView(edit)
            .setPositiveButton("OK") { _, _ ->
                prefs.edit().putString("camera_ip", edit.text.toString()).apply()
                player.setMediaItem(MediaItem.fromUri("rtsp://user:pass@${'$'}{edit.text}/stream"))
                player.prepare()
                player.playWhenReady = true
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
