package app.noreels.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.noreels.R
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onSurfaceToggle: (String, Boolean) -> Unit,
    onAllowanceChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    var editingAllowance by rememberSaveable { mutableStateOf(false) }
    if (editingAllowance) {
        AllowanceDialog(
            currentMinutes = state.allowanceMinutes,
            onDismiss = { editingAllowance = false },
            onSave = {
                onAllowanceChange(it)
                editingAllowance = false
            },
        )
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ServiceCard(state.serviceEnabled) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            item {
                AllowanceCard(state) { editingAllowance = true }
            }
            item {
                TodayCard(state) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            items(state.platforms, key = { it.id }) { platform ->
                PlatformCard(platform, onSurfaceToggle)
            }
        }
    }
}

@Composable
private fun ServiceCard(enabled: Boolean, onOpenSettings: () -> Unit) {
    val colors = if (enabled) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(if (enabled) R.string.service_on_title else R.string.service_off_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(if (enabled) R.string.service_on_body else R.string.service_off_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!enabled) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.service_enable_button)) }
            }
        }
    }
}

@Composable
private fun AllowanceCard(state: HomeUiState, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.allowance_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onEdit) { Text(stringResource(R.string.allowance_edit_button)) }
            }
            val allowanceMillis = TimeUnit.MINUTES.toMillis(state.allowanceMinutes.toLong())
            Text(
                if (state.allowanceMinutes == 0) stringResource(R.string.allowance_none) else formatDuration(allowanceMillis),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.allowanceMinutes > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.allowance_used, formatDuration(state.watchedTodayMs), formatDuration(allowanceMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.allowance_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AllowanceDialog(currentMinutes: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var hours by rememberSaveable { mutableStateOf((currentMinutes / 60).toString()) }
    var minutes by rememberSaveable { mutableStateOf((currentMinutes % 60).toString()) }
    val parsed = parseAllowanceMinutes(hours, minutes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.allowance_dialog_title)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it },
                        label = { Text(stringResource(R.string.allowance_hours_label)) },
                        isError = parseAllowanceMinutes(hours, "0") == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = { Text(stringResource(R.string.allowance_minutes_label)) },
                        isError = parseAllowanceMinutes("0", minutes) == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.allowance_dialog_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) {
                Text(stringResource(R.string.allowance_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.allowance_cancel)) }
        },
    )
}

@Composable
private fun TodayCard(state: HomeUiState, onGrantUsage: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val totalBlocks = state.platforms.sumOf { it.blocksToday }
            Text(pluralStringResource(R.plurals.blocks_today, totalBlocks, totalBlocks), style = MaterialTheme.typography.bodyLarge)
            if (state.usageAccess) {
                state.platforms.filter { it.installed }.forEach { platform ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(platform.label, style = MaterialTheme.typography.bodyMedium)
                        Text(formatDuration(platform.foregroundMillisToday ?: 0L), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.usage_missing_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onGrantUsage) { Text(stringResource(R.string.usage_grant_button)) }
            }
        }
    }
}

@Composable
private fun PlatformCard(platform: PlatformUi, onSurfaceToggle: (String, Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(platform.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (platform.installed) pluralStringResource(R.plurals.blocks_short, platform.blocksToday, platform.blocksToday)
                    else stringResource(R.string.not_installed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            platform.surfaces.forEach { surface ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(surface.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = surface.enabled, onCheckedChange = { onSurfaceToggle(surface.key, it) })
                }
            }
        }
    }
}

@Composable
private fun formatDuration(millis: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) stringResource(R.string.duration_hours_minutes, hours, minutes)
    else stringResource(R.string.duration_minutes, minutes)
}
