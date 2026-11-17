package it.labirintodigitale.bcc

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.labirintodigitale.bcc.ui.CardDetailScreen
import it.labirintodigitale.bcc.ui.CardListScreen
import it.labirintodigitale.bcc.ui.EditCardScreen
import it.labirintodigitale.bcc.ui.MainViewModel
import it.labirintodigitale.bcc.ui.Mode
import it.labirintodigitale.bcc.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BccTheme { App(vm) } }
    }

    override fun onStart() {
        super.onStart()
        // Back in the app (e.g. at the checkout): try to refresh, the cached copy is shown meanwhile.
        if (vm.state.value.mode == Mode.OFFLINE) vm.refresh()
    }
}

/** Screens; card ids instead of cards so they survive recomposition and rotation. */
private sealed interface Screen {
    data object List : Screen
    data class Detail(val id: String) : Screen
    data class Edit(val id: String?) : Screen
    data object Settings : Screen
}

private fun Screen.key(): String = when (this) {
    Screen.List -> "list"
    is Screen.Detail -> "detail:$id"
    is Screen.Edit -> "edit:${id ?: ""}"
    Screen.Settings -> "settings"
}

private fun screenOf(key: String): Screen = when {
    key.startsWith("detail:") -> Screen.Detail(key.removePrefix("detail:"))
    key.startsWith("edit:") -> Screen.Edit(key.removePrefix("edit:").ifEmpty { null })
    key == "settings" -> Screen.Settings
    else -> Screen.List
}

@Composable
private fun App(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Back stack as saveable strings.
    var stack by rememberSaveable { mutableStateOf(listOf("list")) }
    val screen = screenOf(stack.last())
    fun push(s: Screen) { stack = stack + s.key() }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1) { pop() }

    if (state.mode == Mode.SETUP && screen != Screen.Settings) {
        SettingsScreen(vm, firstRun = true, onDone = {}, onBack = null)
        return
    }

    when (screen) {
        Screen.List -> CardListScreen(
            state = state,
            vm = vm,
            onOpen = { push(Screen.Detail(it.id)) },
            onAdd = { push(Screen.Edit(null)) },
            onSettings = { push(Screen.Settings) },
        )
        is Screen.Detail -> {
            val card = state.cards.find { it.id == screen.id }
            if (card == null) LaunchedEffect(screen) { pop() } else CardDetailScreen(
                card = card,
                state = state,
                vm = vm,
                onBack = ::pop,
                onEdit = { push(Screen.Edit(card.id)) },
            )
        }
        is Screen.Edit -> {
            val existing = screen.id?.let { id -> state.cards.find { it.id == id } }
            // The card was deleted meanwhile (e.g. from Home Assistant): nothing to edit.
            if (screen.id != null && existing == null) LaunchedEffect(screen) { pop() } else EditCardScreen(
            existing = existing,
            state = state,
            vm = vm,
            onBack = ::pop,
            onSaved = { card ->
                // A new card opens its detail view; an edited one goes back to it.
                stack = if (screen.id == null) stack.dropLast(1) + Screen.Detail(card.id).key() else stack.dropLast(1)
            },
            )
        }
        Screen.Settings -> SettingsScreen(vm, firstRun = false, onDone = ::pop, onBack = ::pop)
    }
}

@Composable
private fun BccTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF4FC3F7))
        else -> lightColorScheme(primary = Color(0xFF0288D1))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
