package it.labirintodigitale.bcc.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import it.labirintodigitale.bcc.R
import it.labirintodigitale.bcc.data.Card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    card: Card,
    state: UiState,
    vm: MainViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state, vm, snackbar)
    KeepBrightScreen()

    // Count one use per opening (only when online; offline the app is read-only).
    var counted by rememberSaveable(card.id) { mutableStateOf(false) }
    LaunchedEffect(card.id, state.canEdit) {
        if (!counted && state.canEdit) {
            counted = true
            vm.markUsed(card)
        }
    }

    var confirmDelete by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.canEdit) {
                        IconButton(onClick = { vm.toggleFavorite(card) }) {
                            Icon(
                                if (card.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                stringResource(R.string.favorite),
                            )
                        }
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                        }
                    } else if (card.favorite) {
                        Icon(Icons.Filled.Favorite, stringResource(R.string.favorite), Modifier.padding(12.dp))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            StatusBanner(state, onRetry = vm::refresh)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (card.number.isNotBlank()) {
                    BarcodeView(card.format, card.number, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(card.number)) }) {
                            Text(stringResource(R.string.copy_number))
                        }
                    }
                }
                CardImages(card, vm, onFullscreen = { fullscreen = it })
                if (card.notes.isNotBlank()) {
                    Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleSmall)
                    Text(card.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text, card.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(card, onDone = onBack)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    fullscreen?.let { file ->
        Dialog(
            onDismissRequest = { fullscreen = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreen = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = vm.repo.imageFile(file),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Front/back photo: tap to flip, button for full screen. */
@Composable
private fun CardImages(card: Card, vm: MainViewModel, onFullscreen: (String) -> Unit) {
    val sides = listOf("front", "back").filter { card.image(it) != null }
    if (sides.isEmpty()) return
    var index by rememberSaveable(card.id) { mutableStateOf(0) }
    val side = sides[index % sides.size]
    val file = card.image(side)!!
    val available = vm.repo.hasImage(file)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .clip(RoundedCornerShape(12.dp))
                .background(parseColor(card.color))
                .clickable(enabled = sides.size > 1) { index++ },
            contentAlignment = Alignment.Center,
        ) {
            if (available) {
                AsyncImage(
                    model = vm.repo.imageFile(file),
                    contentDescription = stringResource(if (side == "front") R.string.front else R.string.back_side),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(stringResource(R.string.image_not_cached), color = Color.White)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (side == "front") R.string.front else R.string.back_side) +
                    if (sides.size > 1) " · " + stringResource(R.string.tap_to_flip) else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (available) {
                TextButton(onClick = { onFullscreen(file) }) { Text(stringResource(R.string.full_screen)) }
            }
        }
    }
}

/** Keep the screen on at full brightness while a card is shown, so the scanner reads it. */
@Composable
private fun KeepBrightScreen() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val before = window.attributes.screenBrightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = before }
        }
    }
}
