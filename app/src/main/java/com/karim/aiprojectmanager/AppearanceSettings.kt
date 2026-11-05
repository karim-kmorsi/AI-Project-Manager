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

@Composable
internal fun AppearanceSettings(state: ThemeSettingsState, onSelect: (ThemeMode) -> Unit, onRetry: () -> Unit) {
    ParagraphText("المظهر", style = MaterialTheme.typography.headlineSmall)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().selectable(
                    selected = state.mode == mode, enabled = !state.loading && !state.saving,
                    role = Role.RadioButton, onClick = { onSelect(mode) }
                ).heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = state.mode == mode, onClick = null, enabled = !state.loading && !state.saving)
                    Text(mode.label)
                }
            }
            if (state.loading || state.saving) ParagraphText(
                if (state.loading) "جارٍ تحميل المظهر..." else "جارٍ حفظ المظهر: ${state.pendingMode?.label}...",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.error != null) {
                ParagraphText(if (state.error == ThemePreferenceError.LOAD)
                    "تعذّر تحميل تفضيل المظهر. يُستخدم Follow System مؤقتًا؛ أعد المحاولة."
                else "تعذّر حفظ المظهر: ${state.pendingMode?.label}. بقي المظهر السابق؛ أعد المحاولة.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                TextButton(onClick = onRetry, enabled = !state.loading && !state.saving) { Text("إعادة محاولة المظهر") }
            }
        }
    }
}
