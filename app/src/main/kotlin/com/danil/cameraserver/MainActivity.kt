package com.danil.cameraserver

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.preference.PreferenceManager
import com.danil.cameraserver.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.net.InetAddress
import java.nio.ByteBuffer

@UnstableApi                // для классов Media3
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var player: ExoPlayer
    private var serverRunning = false

    // ────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        // применяем сохранённую тему (Day/Night)
        val dark = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("dark_theme", false)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (dark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else      androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)

        b.fabServer.setOnClickListener { toggleServer() }

        connectAndPlay()           // сразу отображаем поток
    }
    // ────────────────────────────────────────────────────────────
    /** создаём пункт меню «Settings» */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /** обработка нажатия пунктов меню */
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
    // ────────────────────────────────────────────────────────────
    /** старт / стоп встроенного веб‑сервера */
    private fun toggleServer() {
        val intent = Intent(this, ServerService::class.java)

        if (serverRunning) {
            stopService(intent)
            b.fabServer.setImageResource(android.R.drawable.ic_media_play)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            // показываем, куда транслируется
            val url = "http://${getLocalIp()}:8080/"
            Snackbar.make(b.playerView, "Сервер запущен: $url", Snackbar.LENGTH_LONG).show()

            b.fabServer.setImageResource(android.R.drawable.ic_media_pause)
        }
        serverRunning = !serverRunning
    }
    // ────────────────────────────────────────────────────────────
    /** подключаемся к камере и выводим видео */
    private fun connectAndPlay() {
        val prefs  = PreferenceManager.getDefaultSharedPreferences(this)
        val ip     = prefs.getString("ip_address", "192.168.0.44")
        val login  = prefs.getString("login", "admin")
        val pass   = prefs.getString("password", "12345")

        val url = "rtsp://$login:$pass@$ip:554/Streaming/Channels/101?transportmode=unicast"

        if (::player.isInitialized) player.release()

        player = ExoPlayer.Builder(this).build().also { exo ->
            b.playerView.player = exo
            val mediaItem = MediaItem.fromUri(url)
            val src = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(mediaItem)
            exo.setMediaSource(src)
            exo.prepare()
            exo.playWhenReady = true
        }

        Snackbar.make(b.playerView, "Streaming from $ip", Snackbar.LENGTH_SHORT).show()
    }
    // ────────────────────────────────────────────────────────────
    /** получаем локальный IP планшета (Wi‑Fi) */
    private fun getLocalIp(): String {
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ipInt = wifi.connectionInfo.ipAddress           // little‑endian
        val ipBytes = ByteBuffer.allocate(4).putInt(ipInt).array().reversed().toByteArray()
        return InetAddress.getByAddress(ipBytes).hostAddress ?: "0.0.0.0"
    }
    // ────────────────────────────────────────────────────────────
    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
