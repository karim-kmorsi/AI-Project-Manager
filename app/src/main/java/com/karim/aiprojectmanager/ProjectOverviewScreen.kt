package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
            VNextSectionTitle(stringResource(R.string.overview_description_title), R.drawable.vnext_info)
            ParagraphText(
                project.description.ifBlank { stringResource(R.string.overview_description_empty) },
                Modifier.testTag("overview_description"), style = ProjectTextStyles.supporting
            )
            SecondaryActionButton(onEdit) {
                VNextIcon(R.drawable.vnext_edit)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.overview_edit_project))
            }
        }

        VNextPanel(Modifier.testTag("overview_progress_panel")) {
            VNextSectionTitle(stringResource(R.string.overview_progress), R.drawable.vnext_check)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ParagraphText(stringResource(R.string.overview_manually_completed),
                    style = ProjectTextStyles.supporting, fillWidth = false)
                ParagraphText(taskCountExpression(verified, project.tasks.size),
                    style = ProjectTextStyles.project, fontWeight = FontWeight.Bold, fillWidth = false)
            }
            VNextProgress(project)
            ParagraphText(stringResource(R.string.overview_manual_note),
                style = ProjectTextStyles.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val pending = project.tasks.filter { it.status != "VERIFIED" }
        if (pending.isNotEmpty()) VNextPanel(Modifier.testTag("overview_pending_panel")) {
            VNextSectionTitle(stringResource(R.string.overview_pending), R.drawable.vnext_route)
            pending.forEach { ParagraphText("${it.title} — ${localizedTaskStatus(it.status)}", style = ProjectTextStyles.supporting) }
        }

        VNextPanel(Modifier.testTag("overview_github_panel")) {
            VNextSectionTitle(stringResource(R.string.overview_github), R.drawable.vnext_link)
            VNextStatusPill(stringResource(if (connected) R.string.overview_connected else R.string.overview_disconnected),
                warning = !connected)
            ParagraphText(stringResource(R.string.overview_github_note),
                style = ProjectTextStyles.supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        VNextPanel(Modifier.testTag("overview_quick_info")) {
            VNextSectionTitle(stringResource(R.string.overview_quick_info), R.drawable.vnext_info)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewFact(stringResource(R.string.overview_total_tasks), project.tasks.size.toString(), Modifier.weight(1f))
                OverviewFact(stringResource(R.string.overview_blueprint),
                    stringResource(if (project.blueprint.isBlank()) R.string.overview_empty else R.string.overview_present), Modifier.weight(1f))
                OverviewFact(stringResource(R.string.overview_roadmap),
                    stringResource(if (project.roadmap.isBlank()) R.string.overview_empty else R.string.overview_present), Modifier.weight(1f))
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
