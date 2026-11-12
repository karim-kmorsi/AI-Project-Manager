package com.karim.aiprojectmanager

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement.Absolute
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
internal fun DashboardHeader(onSettings: () -> Unit, onExport: () -> Unit, onPreview: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val exportLabel = stringResource(R.string.dashboard_export)
    val previewLabel = stringResource(R.string.dashboard_backup_preview)
    Surface(color = MaterialTheme.colorScheme.background) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = ProjectSpacing.screen, vertical = ProjectSpacing.xSmall)
            .semantics { isTraversalGroup = true }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ProjectSpacing.small)) {
            // Reuse the untouched transparent foreground. The separate launcher background
            // belongs to adaptive icons, not the Dashboard; no bitmap derivative is created.
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                modifier = Modifier.size(72.dp).testTag("dashboard_logo"), contentScale = ContentScale.Fit)
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF00C7E8))) { append("AI ") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("Project Manager") }
            }, Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title),
                    modifier = Modifier.size(24.dp))
            }
            Box {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.dashboard_more),
                        modifier = Modifier.size(24.dp))
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(exportLabel) },
                        onClick = { expanded = false; onExport() })
                    DropdownMenuItem(text = { Text(previewLabel) },
                        onClick = { expanded = false; onPreview() })
                }
            }
        }
        }
    }
}

@Composable
internal fun DashboardCreateBar(onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            .padding(horizontal = ProjectSpacing.screen, vertical = ProjectSpacing.small)
            .testTag("dashboard_create_bar"), horizontalArrangement = Absolute.Right) {
            DashboardCreateButton(onClick)
        }
    }
}

@Composable
internal fun DashboardCreateButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick,
        modifier = Modifier.testTag("dashboard_create").border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .65f), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp), elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        containerColor = FilledActionBlue, contentColor = androidx.compose.ui.graphics.Color.White) {
        Text("+", modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(ProjectSpacing.small))
        Text(stringResource(R.string.dashboard_create))
    }
}

@Composable
internal fun DashboardProjectList(projects: List<ProjectItem>, query: String, onQueryChange: (String) -> Unit,
    listState: LazyListState, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val visible = filterProjectsByQuery(projects, query)
    val focus = LocalFocusManager.current
    // Scaffold reserves the measured bottom bar: rows never render behind Create.
    LazyColumn(modifier.fillMaxSize().background(vnextBackdrop()).testTag("dashboard_projects"), state = listState,
        contentPadding = PaddingValues(start = ProjectSpacing.screen, end = ProjectSpacing.screen,
            top = ProjectSpacing.small, bottom = ProjectSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ProjectSpacing.small)) {
        item(key = "heading") {
            ProjectSectionHeading(stringResource(R.string.dashboard_my_projects))
            ParagraphText(stringResource(R.string.dashboard_project_count, projects.size),
                style = ProjectTextStyles.supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (projects.isEmpty()) item(key = "empty") {
            ProjectMessage(stringResource(R.string.dashboard_empty_title), stringResource(R.string.dashboard_empty_detail))
        }
        if (projects.isNotEmpty()) {
            item(key = "search") {
                DashboardSearchField(query, onQueryChange) { focus.clearFocus() }
            }
            if (visible.isEmpty()) item(key = "no_matches") {
                ProjectMessage(stringResource(R.string.dashboard_no_matches_title), stringResource(R.string.dashboard_no_matches_detail))
            }
        }
        items(visible, key = { "project:${it.id}" }) { project -> DashboardProjectCard(project) { onSelect(project.id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.dashboard_search)
    val colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = if (MaterialTheme.colorScheme.background == appearanceColors(true).background)
            androidx.compose.ui.graphics.Color(0xFF111D2D) else MaterialTheme.colorScheme.surfaceContainer,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer)
    BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dashboard_search")
            .semantics { contentDescription = label },
        textStyle = contentTextStyle(ProjectTextStyles.supporting).copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(value = query, innerTextField = inner,
                enabled = true, singleLine = true, visualTransformation = VisualTransformation.None,
                interactionSource = interaction, placeholder = { Text(label, style = ProjectTextStyles.supporting) },
                colors = colors, contentPadding = PaddingValues(horizontal = ProjectSpacing.medium, vertical = ProjectSpacing.medium),
                container = { OutlinedTextFieldDefaults.Container(enabled = true, isError = false, interactionSource = interaction, colors = colors, shape = ProjectCardShape) })
        })
}

@Composable
internal fun DashboardProjectCard(project: ProjectItem, onClick: () -> Unit) {
    val verified = project.tasks.count { it.status == "VERIFIED" }
    val total = project.tasks.size
    val counts = stringResource(R.string.dashboard_manual_count, taskCountExpression(verified, total))
    ProjectCard(onClick, Modifier.testTag("dashboard_project_${project.id}"),
        contentPadding = PaddingValues(horizontal = ProjectSpacing.medium, vertical = ProjectSpacing.small),
        spacing = ProjectSpacing.xSmall) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VNextProjectSymbol()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ParagraphText(project.name, style = ProjectTextStyles.project, color = MaterialTheme.colorScheme.onSurface)
                if (project.description.isNotBlank()) Text(project.description, modifier = Modifier.fillMaxWidth(),
                    style = contentTextStyle(ProjectTextStyles.supporting), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        ParagraphText(counts, style = ProjectTextStyles.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { if (total == 0) 0f else verified.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(ProjectSpacing.xSmall).clearAndSetSemantics {},
            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant,
            gapSize = 0.dp, drawStopIndicator = {})
    }
}

@Composable
internal fun DashboardReadError(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ProjectSpacing.screen)) {
        ProjectMessage(stringResource(R.string.dashboard_read_error_title), stringResource(R.string.dashboard_read_error), error = true,
            action = { SecondaryActionButton(onRetry) { Text(stringResource(R.string.dashboard_retry)) } })
    }
}
