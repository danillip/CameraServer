Добавь соотвествующую кнопку настроек, куда можно вписать ip камеры 192.168.0.44 и там же расположить настроки детальные

# Цель
Собрать Android‑приложение «CameraServer» на Kotlin (minSdk 24, targetSdk 34), которое:
1. Проигрывает RTSP‑поток IP‑камеры во встроенном плеере.
2. Поднимает/гасит встроенный HTTP‑сервер NanoHTTPD по нажатию кнопки.
3. Отдаёт страницу assets/index.html с HLS‑плеером и базовой авторизацией.

# Структура проекта
- app/build.gradle (модуль)
- AndroidManifest.xml
- res/layout/activity_main.xml
- kotlin/com/danil/cameraserver/MainActivity.kt
- kotlin/com/danil/cameraserver/ServerService.kt
- assets/index.html
- (при необходимости) proguard‑rules.pro

# app/build.gradle
plugins {
    id "com.android.application"
    id "org.jetbrains.kotlin.android"
}
android {
    compileSdk 34
    defaultConfig {
        applicationId "com.danil.cameraserver"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"),
                          "proguard-rules.pro"
        }
    }
}
dependencies {
    implementation "androidx.media3:media3-exoplayer-rtsp:1.3.1"
    implementation "androidx.media3:media3-ui:1.3.1"
    implementation "org.nanohttpd:nanohttpd:2.3.1"
}

# AndroidManifest.xml (добавь внутрь <application>)
<uses-permission android:name="android.permission.INTERNET"/>
<service
    android:name=".ServerService"
    android:foregroundServiceType="dataSync"
    android:exported="false"/>

# res/layout/activity_main.xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.media3.ui.PlayerView
        android:id="@+id/player_view"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@+id/startBtn"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"/>

    <Button
        android:id="@+id/startBtn"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Start / Stop Server"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"/>
</androidx.constraintlayout.widget.ConstraintLayout>

# MainActivity.kt
package com.danil.cameraserver

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.danil.cameraserver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer
    private var serverRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        player = ExoPlayer.Builder(this).build().also {
            binding.playerView.player = it
            it.setMediaItem(MediaItem.fromUri("rtsp://user:pass@<camera-ip>/stream"))
            it.prepare()
            it.playWhenReady = true
        }

        binding.startBtn.setOnClickListener {
            if (serverRunning) {
                stopService(Intent(this, ServerService::class.java))
            } else {
                startForegroundService(Intent(this, ServerService::class.java))
            }
            serverRunning = !serverRunning
            binding.startBtn.text =
                if (serverRunning) "Stop Server" else "Start Server"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

# ServerService.kt
package com.danil.cameraserver

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD

class ServerService : Service() {
    private lateinit var server: NanoHTTPD
    private val port = 8080
    private val channelId = "server_channel"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Camera Web Server",
                NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Camera Server")
            .setContentText("Running on port $port")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(1, notification)

        server = object : NanoHTTPD(port) {
            override fun serve(session: IHTTPSession): Response {
                val auth = session.headers["authorization"]
                val expected = "Basic " + android.util.Base64.encodeToString(
                    "admin:password".toByteArray(), android.util.Base64.NO_WRAP)
                if (auth != expected) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain",
                        "Unauthorized").apply {
                        addHeader("WWW-Authenticate", "Basic realm=\"Camera\"")
                    }
                }
                return when (session.uri) {
                    "/" -> {
                        val html = assets.open("index.html").bufferedReader().readText()
                        newFixedLengthResponse(Response.Status.OK, "text/html", html)
                    }
                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND,
                        "text/plain", "Not found")
                }
            }
        }
        server.start()
    }

    override fun onDestroy() {
        server.stop()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

# assets/index.html
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>IP‑Camera Stream</title></head>
<body style="margin:0;background:#000;">
  <video id="player" controls autoplay style="width:100%;height:100%;"></video>
  <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.0/dist/hls.min.js"></script>
  <script>
    const video = document.getElementById('player');
    const hls = new Hls();
    hls.loadSource('/camera.m3u8'); // заменить, если нужен другой манифест
    hls.attachMedia(video);
  </script>
</body>
</html>

# Задание для Codex
1. Создать все перечисленные файлы с точным содержимым.
2. Подставить корректный импорт для Base64 (android.util).
3. Сгенерировать binding‑класс (viewBinding true по умолчанию).
4. Проставить правильные package‑директории.
5. Не добавлять лишних комментариев, только рабочий код.