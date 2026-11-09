package com.karim.aiprojectmanager

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
import androidx.compose.ui.unit.dp

/** Settings and read-only tools. Opening this destination never saves projects. */
@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier, onExport: () -> Unit, onPreview: () -> Unit,
    githubSignIn: GitHubSignIn,
    appearanceSettings: @Composable () -> Unit = {}
) {
    var showSnapshot by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        appearanceSettings()
        ParagraphText("التخزين والنسخ الاحتياطي", style = MaterialTheme.typography.headlineSmall)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ParagraphText("تُحفظ المشاريع والمخطط وخريطة الطريق والمهام في مساحة التطبيق المحلية الخاصة على هذا الجهاز.")
                ParagraphText("تصدير JSON يدويًا ينشئ نسخة خارجية في المكان الذي تختاره. لا تُنشأ نسخة خارجية تلقائيًا.")
                ParagraphText("معاينة النسخة الاحتياطية تعرض محتواها فقط؛ لا تستورد البيانات ولا تدمجها أو تستبدل المشاريع الحالية.")
                ParagraphText("إعدادات Android مهيأة لاستبعاد بيانات التطبيق من النسخ الاحتياطي السحابي التلقائي والنقل بين الأجهزة.")
                ParagraphText("لم يُتحقق بالكامل من سلوك جميع الشركات المصنّعة وعمليات النقل بين الأجهزة؛ لا تعتمد على النقل التلقائي لحماية بياناتك.")
                ParagraphText("قد يؤدي إلغاء تثبيت التطبيق أو فقدان بياناته الخاصة إلى فقدان مشاريعك إذا لم تحتفظ بنسخة JSON خارجية. لا توجد ضمانة لاستعادة كل البيانات.")
            }
        }
        TextButton(onClick = onExport) { Text("تصدير JSON") }
        TextButton(onClick = onPreview) { Text("معاينة نسخة") }
        TextButton(onClick = { showSnapshot = true }) { Text("معاينة لقطة Backlog") }
        GitHubConnectionSettings(githubSignIn)
    }
    if (showSnapshot) BacklogSnapshotPreview(onClose = { showSnapshot = false })
}
