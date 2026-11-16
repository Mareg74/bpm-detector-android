package com.Mareg74.bpmdetector.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.Mareg74.bpmdetector.audio.AudioSourceKind
import com.Mareg74.bpmdetector.data.ThemeMode
import com.Mareg74.bpmdetector.data.UserSettings
import com.Mareg74.bpmdetector.domain.GenreCatalog
import com.Mareg74.bpmdetector.domain.GenreProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: UserSettings,
    sourceAvailability: Map<AudioSourceKind, Boolean>,
    onSourceChange: (AudioSourceKind) -> Unit,
    onGenreChange: (String) -> Unit,
    onShowDecimalChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val genres = remember { GenreCatalog.all() }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .testTag("settings_sheet"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
        )
        SourceSection(
            selected = settings.source,
            availability = sourceAvailability,
            onSelected = onSourceChange,
        )
        GenreSection(
            selectedId = settings.genreId,
            genres = genres,
            onSelected = onGenreChange,
        )
        ToggleRow(
            label = "Show decimal BPM",
            checked = settings.showDecimal,
            onCheckedChange = onShowDecimalChange,
            testTag = "decimal_switch",
        )
        ToggleRow(
            label = "Night theme",
            checked = settings.theme == ThemeMode.Night,
            onCheckedChange = { night ->
                onThemeChange(if (night) ThemeMode.Night else ThemeMode.Day)
            },
            testTag = "theme_switch",
        )
    }
}

@Composable
private fun SourceSection(
    selected: AudioSourceKind,
    availability: Map<AudioSourceKind, Boolean>,
    onSelected: (AudioSourceKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Audio source",
            style = MaterialTheme.typography.titleMedium,
        )
        AudioSourceKind.entries.forEach { kind ->
            val available = availability[kind] != false
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("source_${kind.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == kind,
                    onClick = { if (available) onSelected(kind) },
                    enabled = available,
                )
                Text(
                    text = kind.label(),
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (available) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreSection(
    selectedId: String,
    genres: List<GenreProfile>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = genres.firstOrNull { it.id == selectedId }?.label ?: selectedId
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Genre profile",
            style = MaterialTheme.typography.titleMedium,
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.testTag("genre_dropdown"),
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Genre") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                genres.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.label) },
                        onClick = {
                            onSelected(profile.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun AudioSourceKind.label(): String =
    when (this) {
        AudioSourceKind.Mic -> "Microphone"
        AudioSourceKind.Usb -> "USB audio"
        AudioSourceKind.Internal -> "Internal playback"
    }