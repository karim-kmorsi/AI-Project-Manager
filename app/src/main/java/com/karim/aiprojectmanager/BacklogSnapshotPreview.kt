package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun BacklogSnapshotPreview(onClose: () -> Unit) {
    val assets = LocalContext.current.assets
    val result by produceState<Result<BacklogSnapshot>?>(initialValue = null, key1 = assets) {
        value = withContext(Dispatchers.IO) {
            try {
                Result.success(BacklogSnapshot.read(assets.open(BacklogSnapshot.ASSET).bufferedReader()))
            } catch (error: IOException) {
                Result.failure(error)
            } catch (error: IllegalArgumentException) {
                Result.failure(error)
            } catch (error: IllegalStateException) {
                Result.failure(error)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("معاينة لقطة Backlog") },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val snapshot = result?.getOrNull()
                when {
                    result == null -> ParagraphText("جارٍ قراءة اللقطة…")
                    snapshot == null -> ParagraphText("تعذّرت قراءة اللقطة أو أن تنسيقها غير مدعوم.")
                    else -> {
                        ParagraphText("لقطة تجريبية مضمّنة للقراءة فقط. تُعرض حالة التحقق كما سجّلها المصدر.")
                        ParagraphText(snapshot.name, style = MaterialTheme.typography.titleMedium)
                        ParagraphText(snapshot.id)
                        for ((id, title) in snapshot.milestones) ParagraphText("$id · $title")
                        val action = snapshot.nextAction
                        ParagraphText("الإجراء التالي: " + (action?.let { "${it.taskId} · ${it.reason}" } ?: "لا يوجد"))
                        for (task in snapshot.tasks) {
                            ParagraphText("${task.id} · ${task.title}", style = MaterialTheme.typography.titleSmall)
                            ParagraphText("${task.status} · ${task.verification} · ${task.milestoneId}")
                            if (task.dependencies.isNotEmpty()) {
                                ParagraphText("التبعيات: " + task.dependencies.joinToString())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}
