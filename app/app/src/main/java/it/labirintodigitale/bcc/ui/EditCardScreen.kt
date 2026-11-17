package it.labirintodigitale.bcc.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.labirintodigitale.bcc.R
import it.labirintodigitale.bcc.data.BARCODE_FORMATS
import it.labirintodigitale.bcc.data.Card
import it.labirintodigitale.bcc.data.CardFields
import it.labirintodigitale.bcc.data.FORMAT_LABELS

private val PALETTE = listOf(
    "#03a9f4", "#2196f3", "#3f51b5", "#673ab7", "#9c27b0", "#e91e63", "#f44336",
    "#ff5722", "#ff9800", "#ffc107", "#8bc34a", "#4caf50", "#009688", "#795548", "#607d8b", "#212121",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditCardScreen(
    existing: Card?,
    state: UiState,
    vm: MainViewModel,
    onBack: () -> Unit,
    onSaved: (Card) -> Unit,
) {
    val start = existing?.let(CardFields::from)
        ?: CardFields("", "", "code128", PALETTE.first(), "", false, "front")
    var name by rememberSaveable { mutableStateOf(start.name) }
    var number by rememberSaveable { mutableStateOf(start.number) }
    var format by rememberSaveable { mutableStateOf(start.format) }
    var color by rememberSaveable { mutableStateOf(start.color) }
    var notes by rememberSaveable { mutableStateOf(start.notes) }
    var favorite by rememberSaveable { mutableStateOf(start.favorite) }
    var preview by rememberSaveable { mutableStateOf(start.preview) }
    val images = remember { mutableStateMapOf<String, ImageChange>() }
    var saving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state, vm, snackbar)

    val canSave = name.isNotBlank() && state.canEdit && !saving
    fun save() = vm.save(
        existing,
        CardFields(name, number, format, color, notes, favorite, preview),
        images.toMap(),
        onSaving = { saving = it },
        onDone = onSaved,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.new_card else R.string.edit_card)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = ::save, enabled = canSave) {
                            Icon(Icons.Filled.Check, stringResource(R.string.save))
                        }
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    name, { name = it.take(100) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    number, { number = it.take(512) },
                    label = { Text(stringResource(R.string.number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                FormatPicker(format) { format = it }
                if (number.isNotBlank()) BarcodeView(format, number, Modifier.fillMaxWidth())

                Text(stringResource(R.string.color), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(color) + PALETTE).distinctBy { it.lowercase() }.forEach { hex ->
                        val selected = hex.equals(color, ignoreCase = true)
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parseColor(hex))
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = hex },
                        )
                    }
                }

                Text(stringResource(R.string.images), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (side in listOf("front", "back")) {
                        ImageSlot(
                            label = stringResource(if (side == "front") R.string.front else R.string.back_side),
                            current = existing?.image(side),
                            change = images[side],
                            color = parseColor(color),
                            vm = vm,
                            onChange = { images[side] = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(stringResource(R.string.preview), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((value, label) in listOf(
                        "front" to R.string.front, "back" to R.string.back_side, "none" to R.string.preview_none,
                    )) {
                        FilterChip(selected = preview == value, onClick = { preview = value }, label = { Text(stringResource(label)) })
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.favorite), Modifier.weight(1f))
                    Switch(favorite, { favorite = it })
                }

                OutlinedTextField(
                    notes, { notes = it.take(2000) },
                    label = { Text(stringResource(R.string.notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatPicker(format: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val noneLabel = stringResource(R.string.format_none)
    fun label(f: String) = if (f == "none") noneLabel else FORMAT_LABELS[f] ?: f
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = label(format),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.format)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BARCODE_FORMATS.forEach { f ->
                DropdownMenuItem(text = { Text(label(f)) }, onClick = { onChange(f); open = false })
            }
        }
    }
}

@Composable
private fun ImageSlot(
    label: String,
    current: String?,
    change: ImageChange?,
    color: Color,
    vm: MainViewModel,
    onChange: (ImageChange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) onChange(ImageChange.Set(uri))
    }
    val model: Any? = when (change) {
        is ImageChange.Set -> change.uri
        ImageChange.Remove -> null
        null -> current?.takeIf { vm.repo.hasImage(it) }?.let { vm.repo.imageFile(it) }
    }
    val hasImage = change is ImageChange.Set || (change == null && current != null)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (model != null) {
                AsyncImage(model, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(stringResource(R.string.choose_photo), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (hasImage) {
                TextButton(onClick = { onChange(ImageChange.Remove) }) { Text(stringResource(R.string.remove)) }
            }
        }
    }
}
