package com.danil.cameraserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
