package com.karim.aiprojectmanager

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Settings and read-only tools. Opening this destination never saves projects. */
@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier, onExport: () -> Unit, onPreview: () -> Unit,
    githubSignIn: GitHubSignIn,
    localProjects: List<ProjectItem>? = null,
    onGitHubImport: ((GitHubRepository) -> GitHubImportOutcome)? = null,
    appearanceSettings: @Composable () -> Unit = {}
) {
    var showSnapshot by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionTitle(R.string.settings_connections)
        GitHubConnectionSettings(githubSignIn, localProjects, onGitHubImport)
        appearanceSettings()
        SettingsSectionTitle(R.string.settings_data_backup)
        VNextCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VNextSectionTitle(stringResource(R.string.settings_backup_heading), R.drawable.vnext_save)
                ParagraphText(stringResource(R.string.settings_backup_loss))
                ParagraphText(stringResource(R.string.settings_backup_preview_warning))
                ParagraphText(stringResource(R.string.settings_backup_transfer_warning))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledActionButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                        VNextIcon(R.drawable.vnext_save, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_export_json))
                    }
                    SecondaryActionButton(onClick = onPreview, modifier = Modifier.weight(1f)) {
                        VNextIcon(R.drawable.vnext_info)
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_preview_backup))
                    }
                }
            }
        }
        SettingsSectionTitle(R.string.settings_advanced)
        GitHubPublicConnectivityProbe()
        VNextCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VNextSectionTitle(stringResource(R.string.settings_backlog_title), R.drawable.vnext_layers)
                ParagraphText(stringResource(R.string.settings_backlog_description), style = ProjectTextStyles.supporting)
                SecondaryActionButton(onClick = { showSnapshot = true }) {
                    VNextIcon(R.drawable.vnext_layers)
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_backlog_preview))
                }
            }
        }
    }
    if (showSnapshot) BacklogSnapshotPreview(onClose = { showSnapshot = false })
}


@Composable
internal fun SettingsSectionTitle(@StringRes title: Int) {
    VNextSectionTitle(stringResource(title), when (title) {
        R.string.settings_connections -> R.drawable.vnext_link
        R.string.settings_appearance -> R.drawable.vnext_layers
        R.string.settings_data_backup -> R.drawable.vnext_save
        else -> R.drawable.vnext_info
    })
}
