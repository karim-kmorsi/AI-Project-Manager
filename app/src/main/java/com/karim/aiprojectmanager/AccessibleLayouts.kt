package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Same toolbar content; only move actions when measured text would crowd the title. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AccessibleAppBar(
    title: String,
    navigation: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets
) {
    Surface(modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = -1f },
        color = MaterialTheme.colorScheme.surface) {
        Layout(
            modifier = Modifier.windowInsetsPadding(windowInsets).padding(start = 16.dp, end = 4.dp),
            content = {
                Box { navigation() }
                ParagraphText(title, style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
            }
        ) { children, constraints ->
            val width = constraints.maxWidth
            val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
            val nav = children[0].measure(loose)
            val action = children[2].measure(loose)
            // TextButton already supplies its own horizontal content padding.
            val gap = 0
            val ownRow = toolbarActionsNeedOwnRow(width, nav.width,
                children[1].maxIntrinsicWidth(Constraints.Infinity), action.width, gap)
            val titleWidth = (width - nav.width - (if (nav.width > 0) gap else 0) -
                (if (!ownRow && action.width > 0) action.width + gap else 0)).coerceAtLeast(0)
            val heading = children[1].measure(loose.copy(maxWidth = titleWidth))
            val rowHeight = maxOf(64.dp.roundToPx(), nav.height, heading.height, if (ownRow) 0 else action.height)
            val height = rowHeight + if (ownRow) gap + action.height else 0
            layout(width, height) {
                nav.placeRelative(0, (rowHeight - nav.height) / 2)
                heading.placeRelative(nav.width + if (nav.width > 0) gap else 0, (rowHeight - heading.height) / 2)
                action.placeRelative(width - action.width, if (ownRow) rowHeight + gap else (rowHeight - action.height) / 2)
            }
        }
    }
}

/** Dialog's own edge-to-edge window consumes safe drawing/IME insets once. */
@Composable
internal fun ProjectCreationDialog(
    name: String, details: String, writeError: Boolean,
    onNameChange: (String) -> Unit, onDetailsChange: (String) -> Unit,
    onDismiss: () -> Unit, onCreate: () -> Unit,
    insets: WindowInsets? = null
) {
    AlertDialog(
        // Resolve native insets where the modifier is applied: inside the dialog's window.
        modifier = if (insets == null) Modifier.safeDrawingPadding() else Modifier.windowInsetsPadding(insets),
        properties = DialogProperties(decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { ParagraphText("إنشاء مشروع") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (writeError) ParagraphText("تعذّر حفظ المشروع. لم تُعتمد التغييرات؛ حاول مجددًا.")
                OutlinedTextField(name, onNameChange, textStyle = contentTextStyle(), label = { Text("اسم المشروع") })
                OutlinedTextField(details, onDetailsChange, textStyle = contentTextStyle(), label = { Text("وصف مختصر") })
            }
        },
        confirmButton = { TextButton(onClick = onCreate, enabled = name.isNotBlank()) { Text("إنشاء") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** Reserve readable input space without moving Save into focus-driven scrolling. */
@Composable
internal fun ScrollableDocumentBody(
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
    chrome: @Composable ColumnScope.() -> Unit,
    editor: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val lineHeight = with(density) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() }
    val labelHeight = with(density) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
    // Material's floating label/padding plus actual themed line metrics, including font scaling.
    val minimumLine = with(density) { (lineHeight + labelHeight + 32.dp).roundToPx() }
    val readableMinimum = with(density) { (lineHeight * 3 + labelHeight + 32.dp).roundToPx() }
    Layout(
        modifier = modifier.padding(horizontal = 16.dp),
        content = {
            Box { action() }
            BoxWithConstraints(Modifier.clipToBounds()) {
                val viewport = constraints.maxHeight
                Layout(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    content = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = chrome)
                        editor()
                    }
                ) { children, constraints ->
                    val width = constraints.maxWidth
                    val header = children[0].measure(Constraints(maxWidth = width))
                    val gap = 12.dp.roundToPx()
                    // The field itself must know its real viewport, so its internal scrolling
                    // can keep the cursor in view instead of relying on an externally clipped field.
                    val field = children[1].measure(Constraints.fixed(width,
                        documentFieldHeight(viewport, header.height, gap, readableMinimum)))
                    layout(width, header.height + gap + field.height) {
                        header.placeRelative(0, 0)
                        field.placeRelative(0, header.height + gap)
                    }
                }
            }
        }
    ) { children, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val save = children[0].measure(Constraints(maxWidth = width, maxHeight = height))
        val allocation = documentBodyAllocation(width, height, save.width, save.height,
            12.dp.roundToPx(), 16.dp.roundToPx(), minimumLine)
        val body = children[1].measure(Constraints.fixed(allocation.bodyWidth, allocation.bodyHeight))
        layout(width, height) {
            save.placeRelative(width - save.width, allocation.verticalPadding)
            body.placeRelative(0, if (allocation.sideAction) 0 else allocation.verticalPadding + save.height)
        }
    }
}
