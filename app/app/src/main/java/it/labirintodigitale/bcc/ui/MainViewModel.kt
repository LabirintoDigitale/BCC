package it.labirintodigitale.bcc.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.labirintodigitale.bcc.R
import it.labirintodigitale.bcc.data.Card
import it.labirintodigitale.bcc.data.CardFields
import it.labirintodigitale.bcc.data.CardRepository
import it.labirintodigitale.bcc.data.HaException
import it.labirintodigitale.bcc.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class Mode {
    /** Opening: showing the local copy while trying to reach Home Assistant. */
    CONNECTING,

    /** Home Assistant answered: cards can be edited. */
    ONLINE,

    /** No connection: read-only, showing the cards downloaded last time. */
    OFFLINE,

    /** No Home Assistant configured yet. */
    SETUP,
}

data class UiState(
    val cards: List<Card> = emptyList(),
    val mode: Mode = Mode.CONNECTING,
    val lastSync: Long? = null,
    val syncing: Boolean = false,
    /** Why the last connection failed (shown in the offline banner). */
    val offlineReason: String? = null,
    val message: String? = null,
) {
    val canEdit: Boolean get() = mode == Mode.ONLINE
}

/** Change to one image side when saving a card. */
sealed interface ImageChange {
    data class Set(val uri: Uri) : ImageChange
    data object Remove : ImageChange
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = CardRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var syncJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Signal is back: try again on our own.
            if (_state.value.mode == Mode.OFFLINE) refresh()
        }
    }

    init {
        viewModelScope.launch {
            val cached = repo.cachedCards()
            val last = repo.lastSync()
            if (!repo.settings.configured) {
                _state.update { it.copy(cards = cached, lastSync = last, mode = Mode.SETUP) }
            } else {
                _state.update { it.copy(cards = cached, lastSync = last) }
                refresh()
            }
        }
        runCatching {
            app.getSystemService(ConnectivityManager::class.java)
                .registerDefaultNetworkCallback(networkCallback)
        }
    }

    override fun onCleared() {
        runCatching {
            getApplication<Application>().getSystemService(ConnectivityManager::class.java)
                .unregisterNetworkCallback(networkCallback)
        }
    }

    fun refresh() {
        if (!repo.settings.configured || syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            try {
                val cards = repo.sync()
                _state.update {
                    it.copy(cards = cards, mode = Mode.ONLINE, lastSync = repo.lastSync(), offlineReason = null)
                }
            } catch (e: Exception) {
                goOffline(e)
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    /** Test the new settings, save them and download the cards. Returns an error message or null. */
    suspend fun saveSettings(new: Settings): String? {
        try {
            repo.test(new)
        } catch (e: Exception) {
            return describe(e)
        }
        val old = repo.settings
        if (old.url != new.url.trim() || old.token != new.token.trim()) {
            // Another server or account: drop the old copy.
            repo.clearCache()
            _state.update { it.copy(cards = emptyList(), lastSync = null) }
        }
        repo.settings = new
        _state.update { it.copy(mode = Mode.CONNECTING) }
        syncJob?.cancel()
        refresh()
        return null
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun toggleFavorite(card: Card) = online {
        replace(repo.setFavorite(card, !card.favorite))
    }

    /** Count a use (for "most used" sorting). Silently skipped offline. */
    fun markUsed(card: Card) {
        if (!_state.value.canEdit) return
        viewModelScope.launch {
            runCatching { repo.markUsed(card.id) }.onSuccess(::replace)
        }
    }

    fun delete(card: Card, onDone: () -> Unit) = online {
        repo.delete(card.id)
        _state.update { s -> s.copy(cards = s.cards.filterNot { it.id == card.id }) }
        onDone()
    }

    /** Create or update a card, then upload or remove the changed images. */
    fun save(
        existing: Card?,
        fields: CardFields,
        images: Map<String, ImageChange>,
        onSaving: (Boolean) -> Unit,
        onDone: (Card) -> Unit,
    ) = online(onSaving) {
        var card = if (existing == null) repo.create(fields) else repo.update(existing.id, fields)
        replace(card)
        for ((side, change) in images) {
            val dataUrl = when (change) {
                is ImageChange.Set -> try {
                    prepareImage(change.uri)
                } catch (e: Exception) {
                    throw HaException("invalid", str(R.string.err_image))
                }
                ImageChange.Remove -> null
            }
            card = repo.setImage(card.id, side, dataUrl)
            replace(card)
        }
        onDone(card)
    }

    private fun online(onBusy: (Boolean) -> Unit = {}, block: suspend () -> Unit) {
        if (!_state.value.canEdit) {
            _state.update { it.copy(message = str(R.string.read_only_offline)) }
            return
        }
        viewModelScope.launch {
            onBusy(true)
            try {
                block()
            } catch (e: HaException) {
                _state.update { it.copy(message = describe(e)) }
            } catch (e: Exception) {
                goOffline(e)
                _state.update { it.copy(message = str(R.string.save_failed_offline)) }
            } finally {
                onBusy(false)
            }
        }
    }

    private fun replace(card: Card) = _state.update { s ->
        val others = s.cards.filterNot { it.id == card.id }
        s.copy(cards = others + card)
    }

    private fun goOffline(e: Exception) {
        _state.update { it.copy(mode = Mode.OFFLINE, offlineReason = describe(e)) }
    }

    private fun describe(e: Exception): String = when {
        e is HaException && e.code == "auth_invalid" -> str(R.string.err_token)
        e is HaException && (e.code == "unknown_command" || e.code == "not_loaded") -> str(R.string.err_bcc_missing)
        e is HaException -> e.message ?: e.code
        e is java.net.UnknownHostException -> str(R.string.err_no_host)
        e is java.io.IOException -> str(R.string.err_unreachable)
        else -> e.message ?: e.toString()
    }

    private fun str(id: Int) = getApplication<Application>().getString(id)

    /** Resize like the web panel (max 2560 px JPEG) and keep the websocket message under ~3 MB. */
    private suspend fun prepareImage(uri: Uri): String = withContext(Dispatchers.Default) {
        val resolver = getApplication<Application>().contentResolver
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt(),
                        (info.size.height * scale).roundToInt(),
                    )
                }
            }
        } else {
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: throw HaException("invalid", str(R.string.err_image))
        }
        for ((side, quality) in listOf(MAX_SIDE to 92, MAX_SIDE to 85, 1920 to 85, 1280 to 85)) {
            val scale = minOf(1f, side.toFloat() / max(bitmap.width, bitmap.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true,
                )
            } else bitmap
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val url = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (url.length <= MAX_DATA_URL || side == 1280) return@withContext url
        }
        error("unreachable")
    }

    private companion object {
        const val MAX_SIDE = 2560
        const val MAX_DATA_URL = 3 * 1024 * 1024
    }
}
