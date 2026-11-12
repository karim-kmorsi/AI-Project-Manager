package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Overview of saved project information. It does not infer dates, versions, or verification. */
@Composable
internal fun ProjectOverviewScreen(project: ProjectItem, connected: Boolean, onEdit: () -> Unit) {
    val verified = project.tasks.count { it.status == "VERIFIED" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = ProjectSpacing.screen, vertical = ProjectSpacing.small),
        verticalArrangement = Arrangement.spacedBy(ProjectSpacing.small)
    ) {
        VNextPanel(Modifier.testTag("overview_description_panel")) {
            VNextSectionTitle("وصف المشروع", R.drawable.vnext_info)
            ParagraphText(
                project.description.ifBlank { "لم يُضف وصف لهذا المشروع بعد." },
                Modifier.testTag("overview_description"), style = ProjectTextStyles.supporting
            )
            SecondaryActionButton(onEdit) {
                VNextIcon(R.drawable.vnext_edit)
                Spacer(Modifier.width(8.dp))
                Text("تعديل المشروع")
            }
        }

        VNextPanel(Modifier.testTag("overview_progress_panel")) {
            VNextSectionTitle("التقدم", R.drawable.vnext_check)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ParagraphText("المهام المكتملة يدويًا", style = ProjectTextStyles.supporting)
                ParagraphText(taskCountExpression(verified, project.tasks.size),
                    style = ProjectTextStyles.project, fontWeight = FontWeight.Bold)
            }
            VNextProgress(project)
            ParagraphText("تُسجّل الحالة يدويًا ولا تعني تحققًا مستقلًا.",
                style = ProjectTextStyles.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val pending = pendingTaskDetails(project.tasks)
        if (pending.isNotEmpty()) VNextPanel(Modifier.testTag("overview_pending_panel")) {
            VNextSectionTitle("مهام تحتاج متابعة", R.drawable.vnext_route)
            pending.forEach { ParagraphText(it, style = ProjectTextStyles.supporting) }
        }

        VNextPanel(Modifier.testTag("overview_github_panel")) {
            VNextSectionTitle("اتصال GitHub", R.drawable.vnext_link)
            VNextStatusPill(if (connected) "الحساب متصل" else "الحساب غير متصل", warning = !connected)
            ParagraphText("حالة اتصال الحساب فقط؛ لا يوجد ربط لهذا المشروع أو مزامنة تلقائية.",
                style = ProjectTextStyles.supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        VNextPanel(Modifier.testTag("overview_quick_info")) {
            VNextSectionTitle("معلومات سريعة", R.drawable.vnext_info)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewFact("إجمالي المهام", project.tasks.size.toString(), Modifier.weight(1f))
                OverviewFact("المخطط", if (project.blueprint.isBlank()) "فارغ" else "موجود", Modifier.weight(1f))
                OverviewFact("خارطة الطريق", if (project.roadmap.isBlank()) "فارغة" else "موجودة", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OverviewFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ParagraphText(label, style = ProjectTextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ParagraphText(value, style = ProjectTextStyles.supporting, fontWeight = FontWeight.SemiBold)
    }
}
