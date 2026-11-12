package com.karim.aiprojectmanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

internal object ProjectSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val screen = 16.dp
    val large = 24.dp
    val xLarge = 32.dp
}

// Keep the system font and existing Material metrics: editor sizing uses these same metrics.
internal val ProjectTypography = Typography()
internal val ProjectCardShape = RoundedCornerShape(12.dp)

internal object ProjectTextStyles {
    val screen @Composable get() = MaterialTheme.typography.headlineSmall
    val section @Composable get() = MaterialTheme.typography.titleLarge
    val project @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    val body @Composable get() = MaterialTheme.typography.bodyLarge
    val supporting @Composable get() = MaterialTheme.typography.bodyMedium
    val status @Composable get() = MaterialTheme.typography.labelLarge
    val metadata @Composable get() = MaterialTheme.typography.bodySmall
}

@Composable
internal fun ProjectSectionHeading(text: String, modifier: Modifier = Modifier) {
    ParagraphText(text, modifier.semantics { heading() }, style = ProjectTextStyles.section)
}

@Composable
internal fun ProjectCard(onClick: () -> Unit, modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(ProjectSpacing.screen), spacing: Dp = ProjectSpacing.small,
    content: @Composable ColumnScope.() -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = ProjectCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@Composable
internal fun ProjectMessage(title: String, detail: String, modifier: Modifier = Modifier, error: Boolean = false,
    action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = ProjectSpacing.large)
        .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(ProjectSpacing.small)) {
        ParagraphText(title, Modifier.semantics { heading() }, style = ProjectTextStyles.section,
            color = if (error) MaterialTheme.colorScheme.error else Color.Unspecified)
        ParagraphText(detail, style = ProjectTextStyles.supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
internal fun SecondaryActionButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier, enabled = enabled, shape = ProjectCardShape, content = content)
}
