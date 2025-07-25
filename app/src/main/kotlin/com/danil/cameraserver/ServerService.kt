package com.danil.cameraserver

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import fi.iki.elonen.NanoHTTPD
import android.util.Base64
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ServerService : Service() {

    private lateinit var server: NanoHTTPD
    private val port = 8080
    private val channelId = "server_channel"
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate() {
        super.onCreate()

        // foreground‑уведомление
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
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@ServerService)
                val login  = prefs.getString("login", "admin")
                val pass   = prefs.getString("password", "12345")
                val expected = "Basic " + Base64.encodeToString(
                    "$login:$pass".toByteArray(), Base64.NO_WRAP
                )
                if (session.headers["authorization"] != expected) {
                    return newFixedLengthResponse(
                        Response.Status.UNAUTHORIZED, "text/plain", "Unauthorized"
                    ).apply {
                        addHeader("WWW-Authenticate", "Basic realm=\"Camera\"")
                    }
                }

                return when (session.uri) {
                    "/" -> {
                        val html = assets.open("index.html").bufferedReader().readText()
                        newFixedLengthResponse(Response.Status.OK, "text/html", html)
                    }

                    "/stream" -> createMjpegStream(session)

                    else -> newFixedLengthResponse(
                        Response.Status.NOT_FOUND, "text/plain", "Not found"
                    )
                }
            }

            /** формируем multipart‑MJPEG из последовательности снимков */
            private fun createMjpegStream(session: IHTTPSession): Response {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@ServerService)
                val ip     = prefs.getString("ip_address", "192.168.0.44")
                val login  = prefs.getString("login", "admin")
                val pass   = prefs.getString("password", "12345")

                val camSnapshotUrl =
                    "http://$ip/Streaming/channels/101/picture"   // один кадр JPEG

                // PipedInputStream соединён с PipedOutputStream: запись → чтение
                val pipeOut = java.io.PipedOutputStream()
                val pipeIn: InputStream = java.io.PipedInputStream(pipeOut, 64 * 1024)

                // каждые 800 мс тянем новый кадр и пишем как multipart
                scheduler.scheduleAtFixedRate({
                    try {
                        val conn = URL(camSnapshotUrl).openConnection() as HttpURLConnection
                        conn.setRequestProperty(
                            "Authorization",
                            "Basic " + Base64.encodeToString(
                                "$login:$pass".toByteArray(), Base64.NO_WRAP
                            )
                        )
                        val bytes = conn.inputStream.readBytes()
                        conn.disconnect()

                        val boundary = "--frame\r\n"
                        val header =
                            "Content-Type: image/jpeg\r\nContent-Length: ${bytes.size}\r\n\r\n"
                        pipeOut.write(boundary.toByteArray())
                        pipeOut.write(header.toByteArray())
                        pipeOut.write(bytes)
                        pipeOut.write("\r\n".toByteArray())
                        pipeOut.flush()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, 0, 800, TimeUnit.MILLISECONDS)

                return newChunkedResponse(
                    Response.Status.OK,
                    "multipart/x-mixed-replace; boundary=frame",
                    pipeIn
                )
            }
        }
        server.start()
    }

    override fun onDestroy() {
        server.stop()
        scheduler.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
