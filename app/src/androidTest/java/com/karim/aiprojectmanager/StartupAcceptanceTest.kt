package com.karim.aiprojectmanager

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Isolated composables/files only; compiling these tests does not execute Android acceptance. */
@RunWith(AndroidJUnit4::class)
class StartupPresentationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun automaticModeShowsOnlyTheTwoExplicitLanguageOptions() {
        var requests = 0
        compose.setContent {
            AppTheme(true) {
                AppearanceSettings(ThemeSettingsState(loading = false), {}, {}, language = AppLanguage.SYSTEM,
                    onLanguageSelect = { requests++ })
            }
        }
        val effective = AppLanguage.SYSTEM.resolve(android.content.res.Resources.getSystem()
            .configuration.locales[0].language)
        compose.onNodeWithText(if (effective == AppLanguage.ARABIC) "العربية" else "English").assertIsSelected()
        compose.onNodeWithText(if (effective == AppLanguage.ARABIC) "English" else "العربية").assertIsNotSelected()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertCountEquals(4) // Two appearance modes and exactly two language choices.
        compose.onNode(hasText("Follow device language") or hasText("اتباع لغة الجهاز")).assertDoesNotExist()
    }

    @Test fun loadingShowsCopyrightAndLocalizedProgress() {
        compose.setContent { AppTheme(true) { StartupSplash(loading = true, failed = false, onRetry = {}) } }
        compose.onNodeWithText("© Karim S.K. Morsi 2026").assertExists()
        compose.onNode(hasText("Loading your projects…") or hasText("جارٍ تحميل مشاريعك…")).assertExists()
    }

    @Test fun failureHasAnExplicitRetryAction() {
        var retries = 0
        compose.setContent {
            AppTheme(true) { StartupSplash(loading = false, failed = true, onRetry = { retries++ }) }
        }
        compose.onNode(hasText("Retry") or hasText("إعادة المحاولة")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("© Karim S.K. Morsi 2026").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class StartupStorageTest {
    private fun fixture(block: (File) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "startup-storage-${System.nanoTime()}")
        assertTrue(root.mkdir())
        try { block(File(root, "projects.json")) } finally { root.deleteRecursively() }
    }

    @Test fun startupReadPreservesOriginalBytesAndUnknownFields() = fixture { file ->
        val original = """[{"id":"p","name":"Keep","unknown":7,"tasks":[{"id":"t","title":"Keep","status":"CUSTOM","extension":true}]}]""".toByteArray()
        file.writeBytes(original)
        val loaded = ProjectStorage(file).load(readOnly = true)
        assertEquals("p", loaded.single().id)
        assertEquals("CUSTOM", loaded.single().tasks.single().status)
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun pendingAtomicFilesAreNotDeletedOrPromotedByStartup() = fixture { file ->
        val original = """[{"id":"p","name":"Keep"}]""".toByteArray()
        file.writeBytes(original)
        listOf(".new", ".bak").forEach { suffix ->
            val sidecar = File(file.path + suffix).apply { writeText("preserve pending bytes") }
            assertThrows(IOException::class.java) { ProjectStorage(file).load(readOnly = true) }
            assertEquals("preserve pending bytes", sidecar.readText())
            assertArrayEquals(original, file.readBytes())
        }
    }

    @Test fun malformedStorageRemainsUntouchedWhenStartupFails() = fixture { file ->
        val original = "malformed recoverable project bytes".toByteArray()
        file.writeBytes(original)
        assertThrows(IOException::class.java) { ProjectStorage(file).load(readOnly = true) }
        assertArrayEquals(original, file.readBytes())
    }
}
