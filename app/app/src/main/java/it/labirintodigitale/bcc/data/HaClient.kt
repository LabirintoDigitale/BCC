package it.labirintodigitale.bcc.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Error reported by Home Assistant (`code` is HA's error code, e.g. `auth_invalid`). */
class HaException(val code: String, message: String) : Exception(message)

/** Talks to Home Assistant: the websocket API for the cards, HTTP for the images. */
class HaClient {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    /** Open an authenticated websocket, run [block], then close it. */
    suspend fun <T> session(baseUrl: String, token: String, block: suspend (Session) -> T): T {
        val session = Session(token)
        val request = Request.Builder().url(websocketUrl(baseUrl)).build()
        val socket = http.newWebSocket(request, session.listener)
        session.socket = socket
        try {
            withTimeout(15_000) { session.authenticated.await() }
            return block(session)
        } catch (e: TimeoutCancellationException) {
            // Not a cancellation of the caller: report it as a network error.
            throw IOException("Timeout", e)
        } finally {
            socket.close(1000, null)
        }
    }

    /** Download a card image to [target]. Returns false if the server no longer has it. */
    suspend fun downloadImage(baseUrl: String, token: String, file: String, target: File): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${normalizeUrl(baseUrl)}/api/bcc/image/$file")
                .header("Authorization", "Bearer $token")
                .build()
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> false
                    !response.isSuccessful -> throw IOException("HTTP ${response.code}")
                    else -> {
                        val tmp = File(target.parentFile, target.name + ".part")
                        tmp.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                        if (!tmp.renameTo(target)) throw IOException("Cannot save $file")
                        true
                    }
                }
            }
        }

    class Session internal constructor(private val token: String) {
        internal lateinit var socket: WebSocket
        internal val authenticated = CompletableDeferred<Unit>()
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<Any?>>()
        private val nextId = AtomicInteger(1)

        /** Send a command and return its `result` (JSONObject, JSONArray or null). */
        suspend fun call(type: String, params: JSONObject = JSONObject(), timeoutMs: Long = 30_000): Any? {
            val id = nextId.getAndIncrement()
            val deferred = CompletableDeferred<Any?>()
            pending[id] = deferred
            val msg = JSONObject(params.toString()).put("id", id).put("type", type)
            if (!socket.send(msg.toString())) {
                pending.remove(id)
                throw IOException("Connection closed")
            }
            try {
                return withTimeout(timeoutMs) { deferred.await() }
            } finally {
                pending.remove(id)
            }
        }

        internal val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = runCatching { JSONObject(text) }.getOrNull()
                if (parsed != null) {
                    handle(parsed)
                } else {
                    // Coalesced messages arrive as an array.
                    runCatching { JSONArray(text) }.getOrNull()?.let { arr ->
                        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::handle)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failAll(if (t is IOException) t else IOException(t.message, t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failAll(IOException("Connection closed"))
            }
        }

        private fun handle(msg: JSONObject) {
            when (msg.optString("type")) {
                "auth_required" -> socket.send(
                    JSONObject().put("type", "auth").put("access_token", token).toString()
                )
                "auth_ok" -> authenticated.complete(Unit)
                "auth_invalid" -> authenticated.completeExceptionally(
                    HaException("auth_invalid", msg.optString("message", "Invalid token"))
                )
                "result" -> {
                    val deferred = pending[msg.optInt("id")] ?: return
                    if (msg.optBoolean("success")) {
                        deferred.complete(if (msg.isNull("result")) null else msg.get("result"))
                    } else {
                        val err = msg.optJSONObject("error")
                        deferred.completeExceptionally(
                            HaException(
                                err?.optString("code") ?: "unknown_error",
                                err?.optString("message") ?: "Unknown error",
                            )
                        )
                    }
                }
            }
        }

        private fun failAll(error: IOException) {
            authenticated.completeExceptionally(error)
            pending.values.forEach { it.completeExceptionally(error) }
        }
    }

    companion object {
        /** `ha.local:8123/` -> `http://ha.local:8123` */
        fun normalizeUrl(url: String): String {
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "http://$u"
            return u
        }

        fun websocketUrl(baseUrl: String): String {
            val u = normalizeUrl(baseUrl)
            val ws = if (u.startsWith("https://", true)) "wss://" + u.substring(8) else "ws://" + u.substring(7)
            return "$ws/api/websocket"
        }
    }
}
