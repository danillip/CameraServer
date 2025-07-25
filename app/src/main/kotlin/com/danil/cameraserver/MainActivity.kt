package com.danil.cameraserver

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.danil.cameraserver.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var cfProcess: Process? = null
    private var serverRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.liveBtn.setOnClickListener { toggleLive() }
    }

    private fun toggleLive() {
        if (cfProcess == null) startPublicTunnel() else stopPublicTunnel()
    }

    private fun startPublicTunnel() {
        if (!serverRunning) toggleServer()

        val bin = AssetCopier.copyIfMissing(this, "cloudflared")
        val creds = AssetCopier.copyIfMissing(this, "warp.json")
        val cmd = listOf(
            bin.absolutePath, "tunnel",
            "--no-autoupdate",
            "--url", "http://127.0.0.1:8080",
            "--credentials-file", creds.absolutePath
        )
        cfProcess = ProcessBuilder(cmd).redirectErrorStream(true).start()

        GlobalScope.launch(Dispatchers.IO) {
            repeat(20) {
                delay(500)
                runCatching {
                    val api = URL("http://127.0.0.1:3333/ready").readText()
                    val url = JSONObject(api).getJSONArray("config")
                        .getJSONObject(0).getString("url")
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.liveBtn, "LIVE: $url", Snackbar.LENGTH_LONG).show()
                        binding.liveBtn.text = "Stop"
                    }
                    return@launch
                }
            }
        }
    }

    private fun stopPublicTunnel() {
        cfProcess?.destroy()
        cfProcess = null
        if (serverRunning) toggleServer()
        binding.liveBtn.text = "Go Live"
    }

    private fun toggleServer() {
        val intent = Intent(this, ServerService::class.java)

        if (serverRunning) {
            stopService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        }
        serverRunning = !serverRunning
    }
}
