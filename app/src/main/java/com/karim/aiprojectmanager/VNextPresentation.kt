package com.karim.aiprojectmanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun vnextBackdrop(): Brush = Brush.verticalGradient(listOf(
    MaterialTheme.colorScheme.background,
    MaterialTheme.colorScheme.background,
    if (MaterialTheme.colorScheme.background == appearanceColors(true).background) Color(0xFF060E1A)
    else MaterialTheme.colorScheme.background))

@Composable
internal fun VNextIcon(resource: Int, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(painterResource(resource), contentDescription = null, modifier = modifier.size(22.dp), tint = tint)
}

@Composable
internal fun VNextProjectSymbol() {
    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) { VNextIcon(R.drawable.vnext_folder) }
}

@Composable
internal fun VNextPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun VNextCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface), content = content)
}

@Composable
internal fun VNextPreviewSurface(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
}

@Composable
internal fun VNextDialog(
    onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton,
        dismissButton = dismissButton, title = title, text = text, properties = properties,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp)
}

@Composable
internal fun VNextSectionTitle(text: String, icon: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VNextIcon(icon)
        ParagraphText(text, Modifier.weight(1f).semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface, style = ProjectTextStyles.project)
    }
}

@Composable
internal fun VNextProgress(project: ProjectItem) {
    val verified = project.tasks.count { it.status == "VERIFIED" }
    ParagraphText(androidx.compose.ui.res.stringResource(R.string.dashboard_manual_count,
        taskCountExpression(verified, project.tasks.size)), style = ProjectTextStyles.metadata,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LinearProgressIndicator(progress = { if (project.tasks.isEmpty()) 0f else verified.toFloat() / project.tasks.size },
        modifier = Modifier.fillMaxWidth().height(4.dp).clearAndSetSemantics {}, gapSize = 0.dp, drawStopIndicator = {},
        color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun VNextProjectHero(project: ProjectItem) {
    VNextPanel(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VNextProjectSymbol()
            Column(Modifier.weight(1f)) {
                ParagraphText(project.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                if (project.description.isNotBlank()) ParagraphText(project.description, Modifier.clearAndSetSemantics {},
                    style = ProjectTextStyles.supporting, maxLines = 2)
            }
        }
        Column(Modifier.clearAndSetSemantics {}) { VNextProgress(project) }
    }
}

@Composable
internal fun VNextStatusPill(text: String, warning: Boolean = false) {
    Surface(shape = RoundedCornerShape(24.dp), color = if (warning) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)) {
        ParagraphText(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = ProjectTextStyles.metadata, color = if (warning) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun localizedTaskStatus(status: String): String = when (status) {
    "NOT_STARTED" -> androidx.compose.ui.res.stringResource(R.string.task_status_not_started)
    "IN_PROGRESS" -> androidx.compose.ui.res.stringResource(R.string.task_status_in_progress)
    "IMPLEMENTED_UNVERIFIED" -> androidx.compose.ui.res.stringResource(R.string.task_status_implemented_unverified)
    "VERIFIED" -> androidx.compose.ui.res.stringResource(R.string.task_status_verified)
    else -> androidx.compose.ui.res.stringResource(R.string.task_status_unknown, status)
}
