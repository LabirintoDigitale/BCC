package it.labirintodigitale.bcc.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Settings(val url: String, val altUrl: String, val token: String) {
    val configured: Boolean get() = url.isNotBlank() && token.isNotBlank()

    /** Addresses to try, in order (e.g. LAN first, then Nabu Casa / external). */
    val urls: List<String> get() = listOf(url, altUrl).filter { it.isNotBlank() }.distinct()
}

/**
 * Cards from Home Assistant with a local copy (SQLite + image files).
 *
 * Every change made online is written to the local copy too, so the app always
 * opens with the cards it saw last, even without signal.
 */
class CardRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val db = CardDb(context)
    private val client = HaClient()
    private val imageDir = File(context.filesDir, "images").apply { mkdirs() }

    /** Base URL that answered last time; tried first. */
    @Volatile private var workingUrl: String? = null

    var settings: Settings
        get() = Settings(
            prefs.getString("url", "") ?: "",
            prefs.getString("alt_url", "") ?: "",
            prefs.getString("token", "") ?: "",
        )
        set(value) {
            prefs.edit()
                .putString("url", value.url.trim())
                .putString("alt_url", value.altUrl.trim())
                .putString("token", value.token.trim())
                .apply()
            workingUrl = null
        }

    fun imageFile(name: String): File = File(imageDir, name)

    fun hasImage(name: String?): Boolean = name != null && imageFile(name).isFile

    suspend fun cachedCards(): List<Card> = withContext(Dispatchers.IO) { db.loadCards() }

    suspend fun lastSync(): Long? = withContext(Dispatchers.IO) { db.lastSync() }

    /** Forget the local copy (used when switching to another Home Assistant). */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        db.clear()
        imageDir.listFiles()?.forEach { it.delete() }
    }

    /** Check that [s] reaches Home Assistant and that BCC is installed. */
    suspend fun test(s: Settings) {
        var last: Exception? = null
        for (url in s.urls) {
            try {
                client.session(url, s.token) { it.call("bcc/cards/list") }
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("No URL")
    }

    /**
     * Download every card and the images not cached yet, then replace the local copy.
     * Throws if Home Assistant cannot be reached: the old copy is left untouched.
     */
    suspend fun sync(): List<Card> = withContext(Dispatchers.IO) {
        val s = settings
        val (url, result) = withServer { session -> session.call("bcc/cards/list") }
        val arr = (result as? JSONObject)?.optJSONArray("cards") ?: JSONArray()
        val cards = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { runCatching { Card.fromJson(it) }.getOrNull() }
        }

        // Images have unique, immutable file names: download only the new ones.
        val wanted = cards.flatMap { listOfNotNull(it.front, it.back) }.toSet()
        for (name in wanted) {
            if (!hasImage(name) && SAFE_NAME.matches(name)) {
                runCatching { client.downloadImage(url, s.token, name, imageFile(name)) }
            }
        }
        db.replaceAll(cards, System.currentTimeMillis())
        imageDir.listFiles()?.forEach { if (it.name !in wanted) it.delete() }
        cards
    }

    suspend fun create(fields: CardFields): Card = mutate("bcc/cards/create", fields.toJson())

    suspend fun update(cardId: String, fields: CardFields): Card =
        mutate("bcc/cards/update", fields.toJson().put("card_id", cardId))

    suspend fun setFavorite(card: Card, favorite: Boolean): Card =
        mutate("bcc/cards/update", JSONObject().put("card_id", card.id).put("favorite", favorite))

    suspend fun markUsed(cardId: String): Card =
        mutate("bcc/cards/used", JSONObject().put("card_id", cardId))

    /** Upload (data URL) or remove (null) the image of one side. */
    suspend fun setImage(cardId: String, side: String, dataUrl: String?): Card =
        mutate(
            "bcc/cards/set_image",
            JSONObject().put("card_id", cardId).put("side", side).put("image", dataUrl ?: JSONObject.NULL),
            timeoutMs = 120_000,
        )

    suspend fun delete(cardId: String) = withContext(Dispatchers.IO) {
        withServer { it.call("bcc/cards/delete", JSONObject().put("card_id", cardId)) }
        db.delete(cardId)
    }

    private suspend fun mutate(type: String, params: JSONObject, timeoutMs: Long = 30_000): Card =
        withContext(Dispatchers.IO) {
            val (url, result) = withServer { it.call(type, params, timeoutMs) }
            val card = Card.fromJson(result as JSONObject)
            val s = settings
            for (name in listOfNotNull(card.front, card.back)) {
                if (!hasImage(name) && SAFE_NAME.matches(name)) {
                    runCatching { client.downloadImage(url, s.token, name, imageFile(name)) }
                }
            }
            db.upsert(card)
            card
        }

    /** Run [block] on the first base URL that answers; returns that URL too. */
    private suspend fun <T> withServer(block: suspend (HaClient.Session) -> T): Pair<String, T> {
        val s = settings
        if (!s.configured) throw IllegalStateException("Not configured")
        val urls = (listOfNotNull(workingUrl) + s.urls).distinct()
        var last: Exception? = null
        for (url in urls) {
            try {
                val result = client.session(url, s.token, block)
                workingUrl = url
                return url to result
            } catch (e: HaException) {
                // HA answered: a wrong token or a rejected command is not fixed by another URL.
                workingUrl = url
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                last = e
            }
        }
        throw last ?: IllegalStateException("No URL")
    }

    private companion object {
        val SAFE_NAME = Regex("^[A-Za-z0-9_.-]+$")
    }
}
