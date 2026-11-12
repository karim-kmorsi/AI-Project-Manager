package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Presentation of existing saved information only; no inferred dates, versions or evidence. */
@Composable
internal fun ProjectOverviewScreen(project: ProjectItem, connected: Boolean, onEdit: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VNextPanel {
            VNextSectionTitle("وصف المشروع", R.drawable.vnext_info)
            ParagraphText(project.description, Modifier.testTag("overview_description"))
            TextButton(onClick = onEdit, modifier = Modifier.testTag("edit_project")) {
                VNextIcon(R.drawable.vnext_edit)
                Spacer(Modifier.width(8.dp))
                Text("تعديل المشروع")
            }
        }
        VNextPanel {
            VNextSectionTitle("التقدم", R.drawable.vnext_check)
            VNextProgress(project)
            ParagraphText(taskStatusSummary(project.tasks), style = ProjectTextStyles.supporting)
            ParagraphText("التقدم لا يُعتبر مكتملًا دون دليل مستقل.", style = ProjectTextStyles.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val pending = pendingTaskDetails(project.tasks)
        if (pending.isNotEmpty()) VNextPanel {
            VNextSectionTitle("مهام تحتاج متابعة:", R.drawable.vnext_route)
            pending.forEach { ParagraphText(it, style = ProjectTextStyles.supporting) }
        }
        VNextPanel {
            VNextSectionTitle("GitHub", R.drawable.vnext_link)
            ParagraphText(if (connected)
                "حساب GitHub متصل. ربط هذا المشروع بمستودع والفحص الآلي غير مفعّلين."
                else "حساب GitHub غير متصل. ربط هذا المشروع بمستودع والفحص الآلي غير مفعّلين.")
        }
        if (project.blueprint.isBlank() && project.roadmap.isBlank() && project.tasks.isEmpty()) {
            ProjectMessage("خطة المشروع", "ابدأ بكتابة Blueprint للمشروع.")
        }
    }
}
