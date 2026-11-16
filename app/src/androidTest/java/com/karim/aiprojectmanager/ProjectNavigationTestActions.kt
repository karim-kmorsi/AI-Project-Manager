package com.karim.aiprojectmanager

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag

internal fun SemanticsNodeInteractionsProvider.selectProjectTab(index: Int) {
    require(index in 0..3) { "Project tab index must be between 0 and 3" }
    onNodeWithTag("project_tab_$index").performClick()
}

internal fun SemanticsNodeInteractionsProvider.selectDocumentTab(name: String) {
    val index = when (name.lowercase()) {
        "overview" -> 0
        "blueprint" -> 1
        "roadmap" -> 2
        "tasks" -> 3
        else -> error("Unknown project tab: $name")
    }
    selectProjectTab(index)
}

internal fun SemanticsNodeInteractionsProvider.pressProjectBack() {
    onNodeWithTag("vnext_back").performClick()
}

internal fun SemanticsNodeInteractionsProvider.onLocalizedText(
    english: String,
    arabic: String
): SemanticsNodeInteraction = onNode(hasText(english) or hasText(arabic))

internal fun SemanticsNodeInteractionsProvider.onDocumentContent() =
    onLocalizedText("Document content", "محتوى المستند")

internal fun SemanticsNodeInteractionsProvider.onUnsavedChanges() =
    onNode(hasText("Unsaved changes") or hasText("تغييرات غير محفوظة") or hasText("تعديلات غير محفوظة"))

internal fun SemanticsNodeInteractionsProvider.onDiscardChanges() =
    onNode(hasText("Discard changes") or hasText("تجاهل التغييرات") or hasText("تجاهل التعديلات"))

internal fun SemanticsNodeInteractionsProvider.onEditProject() =
    onLocalizedText("Edit project", "تعديل المشروع")

internal fun SemanticsNodeInteractionsProvider.onBackupPreview() =
    onNode(
        hasText("Backup preview") or hasText("Preview backup") or
            hasText("معاينة النسخة الاحتياطية") or hasText("معاينة نسخة احتياطية") or
            hasText("معاينة نسخة")
    )

internal fun SemanticsNodeInteractionsProvider.onAppearanceMode(mode: String) =
    onLocalizedText(mode, if (mode == "Dark") "داكن" else "فاتح")
