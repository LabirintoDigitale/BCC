package it.labirintodigitale.bcc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import it.labirintodigitale.bcc.R
import it.labirintodigitale.bcc.data.Settings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, firstRun: Boolean, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val current = remember { vm.repo.settings }
    var url by rememberSaveable { mutableStateOf(current.url) }
    var altUrl by rememberSaveable { mutableStateOf(current.altUrl) }
    var token by rememberSaveable { mutableStateOf(current.token) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (firstRun) R.string.welcome else R.string.settings)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                url, { url = it },
                label = { Text(stringResource(R.string.ha_url)) },
                placeholder = { Text("http://homeassistant.local:8123") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                altUrl, { altUrl = it },
                label = { Text(stringResource(R.string.ha_alt_url)) },
                placeholder = { Text("https://xxxx.ui.nabu.casa") },
                supportingText = { Text(stringResource(R.string.ha_alt_url_help)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                token, { token = it },
                label = { Text(stringResource(R.string.token)) },
                supportingText = { Text(stringResource(R.string.token_help)) },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showToken = !showToken }) {
                        Text(stringResource(if (showToken) R.string.hide else R.string.show))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val err = vm.saveSettings(Settings(url, altUrl, token))
                            busy = false
                            if (err == null) onDone() else error = err
                        }
                    },
                ) { Text(stringResource(R.string.connect_save)) }
                if (busy) {
                    Spacer(Modifier.size(16.dp))
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            if (!firstRun) {
                Text(stringResource(R.string.local_copy), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.local_copy_info, state.cards.size) + "\n" + lastSyncText(state.lastSync),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
