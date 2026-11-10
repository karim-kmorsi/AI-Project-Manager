package com.karim.aiprojectmanager

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
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
    appearanceSettings: @Composable () -> Unit = {}
) {
    var showSnapshot by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionTitle(R.string.settings_connections)
        GitHubConnectionSettings(githubSignIn, localProjects)
        appearanceSettings()
        SettingsSectionTitle(R.string.settings_data_backup)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ParagraphText(stringResource(R.string.settings_backup_loss))
                ParagraphText(stringResource(R.string.settings_backup_preview_warning))
                ParagraphText(stringResource(R.string.settings_backup_transfer_warning))
                TextButton(onClick = onExport) { Text("تصدير JSON") }
                TextButton(onClick = onPreview) { Text("معاينة نسخة") }
            }
        }
        SettingsSectionTitle(R.string.settings_advanced)
        GitHubPublicConnectivityProbe()
        TextButton(onClick = { showSnapshot = true }) { Text("معاينة لقطة Backlog") }
    }
    if (showSnapshot) BacklogSnapshotPreview(onClose = { showSnapshot = false })
}


@Composable
internal fun SettingsSectionTitle(@StringRes title: Int) {
    ParagraphText(stringResource(title), modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall)
}
