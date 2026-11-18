package it.labirintodigitale.bcc.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Connection settings. Either [refreshToken] (login with username and password; the
 * password itself is never stored) or [token] (long-lived access token) is set.
 */
data class Settings(
    val url: String,
    val altUrl: String,
    val token: String = "",
    val refreshToken: String = "",
    val username: String = "",
) {
    val configured: Boolean get() = url.isNotBlank() && (token.isNotBlank() || refreshToken.isNotBlank())

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
            prefs.getString("refresh_token", "") ?: "",
            prefs.getString("username", "") ?: "",
        )
        set(value) {
            prefs.edit()
                .putString("url", value.url.trim())
                .putString("alt_url", value.altUrl.trim())
                .putString("token", value.token.trim())
                .putString("refresh_token", value.refreshToken)
                .putString("username", value.username.trim())
                .remove("access_token")
                .remove("access_expires")
                .apply()
            workingUrl = null
        }

    // ---------- login with username and password ----------

    sealed interface LoginResult {
        /** Logged in; save [settings] to use it. */
        data class Ok(val settings: Settings, val tokens: HaClient.Tokens) : LoginResult

        /** Two-factor code needed: call [loginCode] with this. */
        data class NeedsCode(val pending: PendingLogin, val module: String?) : LoginResult

        /** Rejected by Home Assistant: `invalid_auth`, `invalid_code`, `banned`... */
        data class Failed(val error: String, val pending: PendingLogin?) : LoginResult
    }

    data class PendingLogin(val settings: Settings, val baseUrl: String, val flowId: String)

    /** Log in on the first address of [s] that answers. Network errors are thrown. */
    suspend fun login(s: Settings, username: String, password: String): LoginResult {
        var last: Exception? = null
        for (url in s.urls) {
            val step = try {
                client.login(url, username.trim(), password)
            } catch (e: HaException) {
                return LoginResult.Failed(e.code, null)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                last = e
                continue
            }
            return finishStep(s.copy(username = username.trim()), url, step)
        }
        throw last ?: IllegalStateException("No URL")
    }

    suspend fun loginCode(pending: PendingLogin, code: String): LoginResult {
        val step = client.submitLogin(pending.baseUrl, pending.flowId, JSONObject().put("code", code.trim()))
        return finishStep(pending.settings, pending.baseUrl, step)
    }

    private suspend fun finishStep(s: Settings, url: String, step: HaClient.LoginStep): LoginResult =
        when (step) {
            is HaClient.LoginStep.NeedsCode ->
                LoginResult.NeedsCode(PendingLogin(s, url, step.flowId), step.module)
            is HaClient.LoginStep.Failed ->
                LoginResult.Failed(step.error, step.flowId?.let { PendingLogin(s, url, it) })
            is HaClient.LoginStep.Done -> {
                val tokens = client.exchangeCode(url, step.code)
                // Check that BCC is installed before keeping the login.
                client.session(url, tokens.access) { it.call("bcc/cards/list") }
                LoginResult.Ok(s.copy(token = "", refreshToken = tokens.refresh.orEmpty()), tokens)
            }
        }

    /** Save a successful login, keeping its first access token. */
    fun saveLogin(result: LoginResult.Ok) {
        settings = result.settings
        storeAccess(result.tokens)
    }

    /** Token to authenticate on [url]: the long-lived one, or an access token renewed when needed. */
    private suspend fun accessToken(url: String): String {
        val s = settings
        if (s.token.isNotBlank()) return s.token
        val cached = prefs.getString("access_token", null)
        if (cached != null && prefs.getLong("access_expires", 0) > System.currentTimeMillis() + 60_000) {
            return cached
        }
        val tokens = try {
            client.refresh(url, s.refreshToken)
        } catch (e: HaException) {
            // Refresh token revoked or expired: the user has to log in again.
            throw HaException("auth_invalid", e.message ?: e.code)
        }
        storeAccess(tokens)
        return tokens.access
    }

    private fun storeAccess(tokens: HaClient.Tokens) {
        prefs.edit()
            .putString("access_token", tokens.access)
            .putLong("access_expires", tokens.expiresAt)
            .apply()
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
        val (url, token, result) = withServer { session -> session.call("bcc/cards/list") }
        val arr = (result as? JSONObject)?.optJSONArray("cards") ?: JSONArray()
        val cards = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { runCatching { Card.fromJson(it) }.getOrNull() }
        }

        // Images have unique, immutable file names: download only the new ones.
        val wanted = cards.flatMap { listOfNotNull(it.front, it.back) }.toSet()
        for (name in wanted) {
            if (!hasImage(name) && SAFE_NAME.matches(name)) {
                runCatching { client.downloadImage(url, token, name, imageFile(name)) }
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
            val (url, token, result) = withServer { it.call(type, params, timeoutMs) }
            val card = Card.fromJson(result as JSONObject)
            for (name in listOfNotNull(card.front, card.back)) {
                if (!hasImage(name) && SAFE_NAME.matches(name)) {
                    runCatching { client.downloadImage(url, token, name, imageFile(name)) }
                }
            }
            db.upsert(card)
            card
        }

    /** Run [block] on the first base URL that answers; returns that URL and the token used too. */
    private suspend fun <T> withServer(block: suspend (HaClient.Session) -> T): Triple<String, String, T> {
        val s = settings
        if (!s.configured) throw IllegalStateException("Not configured")
        val urls = (listOfNotNull(workingUrl) + s.urls).distinct()
        var last: Exception? = null
        for (url in urls) {
            try {
                val token = accessToken(url)
                val result = client.session(url, token, block)
                workingUrl = url
                return Triple(url, token, result)
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
