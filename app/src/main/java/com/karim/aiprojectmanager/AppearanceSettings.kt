package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource

@Composable
internal fun AppearanceSettings(state: ThemeSettingsState, onSelect: (ThemeMode) -> Unit, onRetry: () -> Unit) {
    SettingsSectionTitle(R.string.settings_appearance)
    VNextCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().selectable(
                    selected = state.mode == mode, enabled = !state.loading && !state.saving,
                    role = Role.RadioButton, onClick = { onSelect(mode) }
                ).heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = state.mode == mode, onClick = null, enabled = !state.loading && !state.saving)
                    Text(stringResource(when (mode) {
                        ThemeMode.SYSTEM -> R.string.appearance_follow_system
                        ThemeMode.LIGHT -> R.string.appearance_light
                        ThemeMode.DARK -> R.string.appearance_dark
                    }))
                }
            }
            if (state.loading || state.saving) ParagraphText(
                if (state.loading) stringResource(R.string.appearance_loading)
                else stringResource(R.string.appearance_saving, stringResource(when (state.pendingMode) {
                    ThemeMode.LIGHT -> R.string.appearance_light
                    ThemeMode.DARK -> R.string.appearance_dark
                    else -> R.string.appearance_follow_system
                })),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.error != null) {
                ParagraphText(if (state.error == ThemePreferenceError.LOAD)
                    stringResource(R.string.appearance_load_error)
                else stringResource(R.string.appearance_save_error, stringResource(when (state.pendingMode) {
                    ThemeMode.LIGHT -> R.string.appearance_light
                    ThemeMode.DARK -> R.string.appearance_dark
                    else -> R.string.appearance_follow_system
                })),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                TextButton(onClick = onRetry, enabled = !state.loading && !state.saving) { Text(stringResource(R.string.appearance_retry)) }
            }
        }
    }
}
