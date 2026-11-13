package com.karim.aiprojectmanager

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import android.util.AtomicFile
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

data class TaskItem(val id: String, val title: String, val status: String = "NOT_STARTED")
data class ProjectItem(
    val id: String, val name: String, val description: String,
    val blueprint: String = "", val roadmap: String = "",
    val tasks: List<TaskItem> = emptyList()
)

open class MainActivity : ComponentActivity() {
    private val themePreferences by lazy { ThemePreferences({ AndroidAppPreferences.open(applicationContext) }) }
    private lateinit var storage: ProjectStorage
    private var pendingExportToken: String? = null
    private var exportPreparing = false
    private val exportStore by lazy { PendingExport(File(cacheDir, "pending-exports")) }
    private var restorePreviewState by mutableStateOf<RestorePreview.Summary?>(null)
    private var restorePreviewError by mutableStateOf<String?>(null)
    private var restorePreviewJob: Job? = null
    private val restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restorePreviewJob?.cancel()
        restorePreviewState = null
        restorePreviewError = null
        restorePreviewJob = if (uri != null) lifecycleScope.launch {
            try {
                restorePreviewState = withContext(Dispatchers.IO) {
                    RestoreCandidate.inspectSelection(contentResolver, uri)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                restorePreviewError = getString(R.string.backup_read_error)
            }
        } else null
    }
    protected open fun notifyUser(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase.withAppLanguage(AppLanguagePreferences(newBase).current()))
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val token = pendingExportToken
        pendingExportToken = null
        lifecycleScope.launch {
            try {
                if (uri != null) {
                    if (token == null) throw IOException("Export snapshot unavailable")
                    withContext(Dispatchers.IO) {
                        JsonExport.exportToUri(contentResolver, uri, exportStore.read(token))
                    }
                    notifyUser(getString(R.string.export_complete))
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { notifyUser(getString(R.string.export_failed)) }
            finally { if (token != null) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { exportStore.discard(token) } }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingExportToken", pendingExportToken)
        super.onSaveInstanceState(outState)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExportToken = savedInstanceState?.getString("pendingExportToken")
        storage = ProjectStorage(File(filesDir, "projects.json"))
        setContent {
            val appearance by themePreferences.state.collectAsState()
            LaunchedEffect(Unit) { themePreferences.load() }
            val dark = appearance.mode.isDark(androidx.compose.foundation.isSystemInDarkTheme())
            AppTheme(dark) {
                SideEffect { updateWindowAppearance(this@MainActivity, dark) }
                ProjectManagerApp(storage, appearanceSettings = {
                    AppearanceSettings(appearance,
                        onSelect = { mode -> lifecycleScope.launch { themePreferences.select(mode) } },
                        onRetry = { lifecycleScope.launch { themePreferences.retry() } },
                        language = AppLanguagePreferences(this@MainActivity).current(),
                        onLanguageSelect = { language ->
                            val preferences = AppLanguagePreferences(this@MainActivity)
                            if (preferences.current() != language) {
                                if (preferences.select(language)) recreate()
                                else notifyUser(getString(R.string.language_save_error))
                            }
                        })
                }, onRestorePreview = {
            // An older provider read must not publish into a newer picker/preview request.
            restorePreviewJob?.cancel()
            restorePreviewState = null
            restorePreviewError = null
            try { restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            catch (_: Exception) { restorePreviewError = getString(R.string.backup_picker_error) }
        }, restorePreview = restorePreviewState, restoreError = restorePreviewError,
        onDismissPreview = { restorePreviewState = null; restorePreviewError = null }, onExport = {
            if (pendingExportToken == null && !exportPreparing) {
                exportPreparing = true
                lifecycleScope.launch {
                    try {
                        pendingExportToken = withContext(Dispatchers.IO) {
                            exportStore.stage(JsonExport.readValidatedSource(File(filesDir, "projects.json")))
                        }
                        exportLauncher.launch("ai-project-manager-backup.json")
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        pendingExportToken?.let { token -> withContext(Dispatchers.IO) { exportStore.discard(token) } }
                        pendingExportToken = null
                        notifyUser(getString(R.string.export_prepare_failed))
                    } finally { exportPreparing = false }
                }
            }
        }) } }
    }
}

class ProjectStorage(private val file: File) {
    fun load(): List<ProjectItem> = synchronized(ProjectWriteLock.monitor) {
        if (!file.exists() && !File(file.path + ".bak").exists()) {
            if (File(file.path + ".new").exists()) throw IOException("Interrupted initial write; recovery required")
            return emptyList()
        }
        val array = try { JSONArray(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }) } catch (e: Exception) {
            throw IOException("Cannot read saved projects; writes are blocked until recovery", e)
        }
        LocalBackup.validateArray(array)
        return try {
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val ta = o.optJSONArray("tasks") ?: JSONArray()
            ProjectItem(
                id = o.getString("id"),
                name = o.getString("name"),
                description = o.optString("description"),
                blueprint = o.optString("blueprint"),
                roadmap = o.optString("roadmap"),
                tasks = (0 until ta.length()).map { n ->
                    val t = ta.getJSONObject(n)
                    TaskItem(t.getString("id"), t.getString("title"), t.optString("status", "NOT_STARTED"))
                }
            )
        }
        } catch (e: Exception) {
            throw IOException("Invalid project data; refusing to replace saved file", e)
        }
    }

    fun save(data: List<ProjectItem>) = synchronized(ProjectWriteLock.monitor) {
        // Never overwrite data that cannot be read or validated.
        load()
        val existing = if (file.exists() || File(file.path + ".bak").exists()) {
            JSONArray(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        } else JSONArray()
        val originals = (0 until existing.length()).associate { index ->
            val obj = existing.getJSONObject(index)
            obj.getString("id") to obj
        }
        val arr = JSONArray()
        data.forEach { project ->
            val obj = originals[project.id] ?: JSONObject()
            obj.put("id", project.id)
            obj.put("name", project.name)
            obj.put("description", project.description)
            obj.put("blueprint", project.blueprint)
            obj.put("roadmap", project.roadmap)
            val oldTasks = obj.optJSONArray("tasks") ?: JSONArray()
            val taskOriginals = (0 until oldTasks.length()).associate { index ->
                val task = oldTasks.getJSONObject(index)
                task.getString("id") to task
            }
            val tasks = JSONArray()
            project.tasks.forEach { t ->
                tasks.put((taskOriginals[t.id] ?: JSONObject()).put("id", t.id).put("title", t.title).put("status", t.status))
            }
            obj.put("tasks", tasks)
            arr.put(obj)
        }
        LocalBackup.validateArray(arr)
        writeVerifiedAtomic(file, arr.toString(2).toByteArray(Charsets.UTF_8))
    }
}

internal fun persistProjectChange(storage: ProjectStorage, current: List<ProjectItem>, next: List<ProjectItem>): List<ProjectItem> =
    synchronized(ProjectWriteLock.monitor) {
        if (storage.load() != current) throw IOException("Projects changed since loading; refusing stale write")
        storage.save(next)
        next
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManagerApp(
    storage: ProjectStorage,
    onExport: () -> Unit = {},
    onRestorePreview: () -> Unit = {},
    restorePreview: RestorePreview.Summary? = null,
    restoreError: String? = null,
    onDismissPreview: () -> Unit = {},
    appearanceSettings: @Composable () -> Unit = {}
) {
    var loadError by remember { mutableStateOf(false) }
    val projects = remember {
        mutableStateListOf<ProjectItem>().apply {
            try { addAll(storage.load()) } catch (_: Exception) { loadError = true }
        }
    }
    var writeError by remember { mutableStateOf(false) }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val applicationContext = LocalContext.current.applicationContext
    val githubSignIn = remember {
        GitHubSignIn(persistence = EncryptedGitHubSessionPersistence(applicationContext))
    }
    val githubScope = rememberCoroutineScope()
    LaunchedEffect(githubSignIn) { githubSignIn.restore(githubScope) }
    var projectSearchQuery by rememberSaveable { mutableStateOf("") }
    val projectListState = rememberLazyListState()
    val taskScreenStates = rememberSaveableStateHolder()
    var editingProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorDirty by remember { mutableStateOf(false) }
    var warnOnExit by remember { mutableStateOf(false) }
    fun update(item: ProjectItem) {
        val index = projects.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            val next = projects.toMutableList().apply { set(index, item) }
            persistProjectChange(storage, projects.toList(), next)
            projects[index] = item
        }
    }
    val active = projects.find { it.id == activeId }
    val settingsVisible = active == null && showSettings
    // Respect unsaved document edits when the system Back gesture/button is used.
    BackHandler(enabled = activeId != null) {
        if (editorDirty) warnOnExit = true else activeId = null
    }

    BackHandler(enabled = settingsVisible) { showSettings = false }

    val density = LocalDensity.current
    val compactProject = density.fontScale > 1.3f || LocalConfiguration.current.screenHeightDp < 480 ||
        WindowInsets.ime.getBottom(density) > 0
    Scaffold(modifier = Modifier.background(vnextBackdrop()), containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        if (active == null && !settingsVisible) {
            DashboardHeader(onSettings = { showSettings = true }, onExport = onExport, onPreview = onRestorePreview)
        } else AccessibleAppBar(
            title = if (settingsVisible) stringResource(R.string.settings_title) else if (compactProject) active!!.name else stringResource(R.string.dashboard_app_name),
            brand = true,
            actions = {},
            navigation = {
                IconButton(modifier = Modifier.testTag("vnext_back"), onClick = {
                    if (settingsVisible) showSettings = false
                    else if (editorDirty) warnOnExit = true else activeId = null
                }) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.project_back)) }
            })
    }, bottomBar = {
        if (active == null && !loadError && !settingsVisible) DashboardCreateBar {
            writeError = false; showNew = true
        }
    }) { padding ->
        if (settingsVisible) {
            SettingsScreen(
                Modifier.padding(padding), onExport, onRestorePreview, githubSignIn,
                localProjects = projects.takeUnless { loadError },
                onGitHubImport = { repository ->
                    if (loadError) {
                        GitHubImportOutcome.FAILED
                    } else {
                        val current = projects.toList()
                        val proposal = previewGitHubImport(repository, current)
                        when {
                            proposal == null -> GitHubImportOutcome.FAILED
                            proposal.candidates.isNotEmpty() -> GitHubImportOutcome.DUPLICATE
                            else -> try {
                                persistProjectChange(storage, current, current + proposal.project)
                                projects.add(proposal.project)
                                activeId = proposal.project.id
                                showSettings = false
                                GitHubImportOutcome.IMPORTED
                            } catch (_: Exception) {
                                GitHubImportOutcome.FAILED
                            }
                        }
                    }
                },
                appearanceSettings = appearanceSettings
            )
        } else if (loadError) {
            DashboardReadError(Modifier.padding(padding), onRetry = {
                try { val loaded = storage.load(); projects.clear(); projects.addAll(loaded); loadError = false }
                catch (_: Exception) { loadError = true }
            })
        } else if (active == null) {
            DashboardProjectList(
                projects = projects,
                query = projectSearchQuery,
                onQueryChange = { projectSearchQuery = it },
                listState = projectListState,
                modifier = Modifier.padding(padding),
                onSelect = { activeId = it }
            )
        } else {
            var tab by rememberSaveable(active.id) { mutableIntStateOf(0) }
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                if (!compactProject) VNextProjectHero(active)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    listOf(R.string.project_tab_overview, R.string.project_tab_blueprint,
                        R.string.project_tab_roadmap, R.string.project_tab_tasks).forEachIndexed { index, label ->
                        val selected = index == tab
                        Surface(onClick = {
                            if (!selected && editorDirty) warnOnExit = true else tab = index
                        }, modifier = Modifier.testTag("project_tab_$index").heightIn(min = 48.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = if (selected) FilledActionBlue else MaterialTheme.colorScheme.surfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                VNextIcon(listOf(R.drawable.vnext_info, R.drawable.vnext_layers,
                                    R.drawable.vnext_route, R.drawable.vnext_check)[index],
                                    modifier = Modifier.size(18.dp),
                                    tint = if (selected) androidx.compose.ui.graphics.Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) androidx.compose.ui.graphics.Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
                when (tab) {
                    0 -> key(active.id) {
                        ProjectOverviewScreen(active, githubSignIn.session != null) { editingProjectId = active.id }
                    }
                    1 -> key(active.id) { EditableDocument(active.id, "blueprint", active.blueprint, stringResource(R.string.blueprint_editor_hint), { editorDirty = it }) {
                        update(active.copy(blueprint = it))
                    } }
                    2 -> key(active.id) { EditableDocument(active.id, "roadmap", active.roadmap, stringResource(R.string.roadmap_editor_hint), { editorDirty = it }) {
                        update(active.copy(roadmap = it))
                    } }
                    3 -> taskScreenStates.SaveableStateProvider(active.id) {
                        TaskScreen(active, onReloadTitle = { taskId ->
                            val loaded = storage.load()
                            loaded.find { it.id == active.id }?.tasks?.find { it.id == taskId }
                                ?: throw IOException("Task no longer exists; pending input retained")
                            projects.clear()
                            projects.addAll(loaded)
                            loaded.first { it.id == active.id }
                        }, onEditTitle = { taskId, title, base ->
                            try {
                                if (editorSnapshot(active) != base) throw IOException("Editor base changed")
                                val edited = editedTaskTitle(active, taskId, title)
                                if (edited != active) update(edited)
                                true
                            } catch (_: Exception) { false }
                        }) { item ->
                            try { update(item); writeError = false; true }
                            catch (_: Exception) { writeError = true; false }
                        }
                    }
                }
            }
        }
    }
    if (restorePreview != null || restoreError != null) {
        VNextDialog(onDismissRequest = onDismissPreview,
            title = { ParagraphText(stringResource(if (restoreError != null) R.string.backup_preview_error_title else R.string.backup_preview_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (restoreError != null) ParagraphText(restoreError)
                    else if (restorePreview != null) {
                        ParagraphText(stringResource(R.string.backup_preview_counts, restorePreview.projects.size, restorePreview.totalTasks))
                        restorePreview.projects.take(30).forEach { project ->
                            ParagraphText(stringResource(R.string.backup_preview_project_tasks, project.name, project.taskCount))
                        }
                        if (restorePreview.projects.size > 30) ParagraphText(stringResource(R.string.backup_preview_limit))
                        ParagraphText(stringResource(R.string.backup_preview_read_only), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }, confirmButton = { TextButton(onClick = onDismissPreview) { Text(stringResource(R.string.generic_close)) } })
    }
    if (writeError && !showNew) {
        VNextDialog(onDismissRequest = { writeError = false }, title = { ParagraphText(stringResource(if (active == null) R.string.dashboard_write_error_title else R.string.project_save_failed_title)) },
            text = { ParagraphText(stringResource(if (active == null) R.string.dashboard_write_error else R.string.project_save_failed_message)) },
            confirmButton = { TextButton(onClick = { writeError = false }) { Text(stringResource(if (active == null) R.string.dashboard_close else R.string.project_save_failed_action)) } })
    }
    if (warnOnExit) {
        VNextDialog(onDismissRequest = { warnOnExit = false }, title = { ParagraphText(stringResource(R.string.unsaved_title)) },
            text = { ParagraphText(stringResource(R.string.unsaved_navigation_message)) },
            confirmButton = { TextButton(onClick = { warnOnExit = false }) { Text(stringResource(R.string.unsaved_return_editor)) } })
    }
    val editingProject = active?.takeIf { it.id == editingProjectId }
    if (editingProject != null) {
        key(editingProject.id) {
            var name by rememberSaveable { mutableStateOf(editingProject.name) }
            var description by rememberSaveable { mutableStateOf(editingProject.description) }
            var editorBase by rememberSaveable(stateSaver = EditorSnapshot.saver) { mutableStateOf(editorSnapshot(editingProject)) }
            var saveError by rememberSaveable { mutableStateOf(false) }
            var discardAction by rememberSaveable { mutableStateOf<String?>(null) }
            fun dismissEditor() {
                if (name != editingProject.name || description != editingProject.description) discardAction = "close"
                else editingProjectId = null
            }
            VNextDialog(onDismissRequest = { dismissEditor() },
                title = { ParagraphText(stringResource(R.string.project_edit_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (saveError) ParagraphText(stringResource(R.string.project_edit_save_error), color = MaterialTheme.colorScheme.error)
                        if (saveError) ParagraphText(stringResource(R.string.project_edit_stale_error))
                        TextButton(onClick = { discardAction = "reload" }) { Text(stringResource(R.string.project_edit_reload)) }
                        OutlinedTextField(name, { name = it }, textStyle = contentTextStyle(), label = { Text(stringResource(R.string.project_edit_name)) },
                            singleLine = true, isError = name.isBlank())
                        OutlinedTextField(description, { description = it }, textStyle = contentTextStyle(), label = { Text(stringResource(R.string.project_edit_description)) })
                    }
                },
                confirmButton = { TextButton(onClick = {
                    try {
                        if (editorSnapshot(editingProject) != editorBase) throw IOException("Editor base changed")
                        val edited = editedProjectMetadata(editingProject, name, description)
                        if (edited != editingProject) update(edited)
                        editingProjectId = null
                    } catch (_: Exception) { saveError = true }
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.project_save)) } },
                dismissButton = { TextButton(onClick = { dismissEditor() }) { Text(stringResource(R.string.project_cancel)) } })
            if (discardAction != null) ConfirmDiscardEdits(
                reload = discardAction == "reload",
                onCancel = { discardAction = null },
                onConfirm = {
                    if (discardAction == "reload") {
                        try {
                            val loaded = storage.load()
                            val latest = loaded.find { it.id == editingProject.id }
                                ?: throw IOException("Project no longer exists; pending input retained")
                            projects.clear()
                            projects.addAll(loaded)
                            name = latest.name
                            description = latest.description
                            editorBase = editorSnapshot(latest)
                            saveError = false
                        } catch (_: Exception) { saveError = true }
                    } else editingProjectId = null
                    discardAction = null
                })
        }
    }
    if (showNew) {
        var name by rememberSaveable { mutableStateOf("") }
        var details by rememberSaveable { mutableStateOf("") }
        ProjectCreationDialog(name, details, writeError, { name = it }, { details = it },
            onDismiss = { showNew = false }, onCreate = {
                if (name.isNotBlank()) {
                    val p = ProjectItem(UUID.randomUUID().toString(), name.trim(), details.trim())
                    try {
                        persistProjectChange(storage, projects.toList(), projects.toList() + p)
                        projects.add(p); activeId = p.id
                        showNew = false; writeError = false
                    } catch (_: Exception) { writeError = true }
                }
            })
    }
}

internal fun isLatestDraft(candidate: String, current: String): Boolean = candidate == current

@Composable
private fun EditableDocument(
    projectId: String, document: String, value: String, hint: String,
    onDirtyChange: (Boolean) -> Unit, save: (String) -> Unit
) {
    val context = LocalContext.current
    val draftStore = remember(context) { DraftStorage(File(context.filesDir, "recovery-drafts")) }
    var recoveryError by remember(projectId, document) { mutableStateOf(false) }
    var preservationFailed by remember(projectId, document) { mutableStateOf(false) }
    var preservationCount by rememberSaveable(projectId, document) { mutableIntStateOf(0) }
    val recovered = remember(projectId, document) {
        try { draftStore.load(projectId, document) } catch (_: IOException) {
            recoveryError = true
            null
        }
    }
    // Do not auto-apply a draft if its original base has since changed.
    val canRecover = recovered != null && recovered.baseText == value
    var conflictingDraft by remember(projectId, document) { mutableStateOf(if (canRecover) null else recovered?.text) }
    var draft by rememberSaveable(projectId, document) {
        mutableStateOf(if (canRecover) recovered!!.text else value)
    }
    var saveError by rememberSaveable(projectId, document) { mutableStateOf(false) }
    val latestSave by rememberUpdatedState(save)
    // Keep a live snapshot so an outdated coroutine cannot commit or delete a newer draft.
    val latestDraft by rememberUpdatedState(draft)
    var lastWrittenDraft by remember(projectId, document) { mutableStateOf<DraftStorage.Draft?>(null) }
    // An unreadable on-disk draft alone must not trap the user in an unchanged, read-only editor.
    SideEffect { onDirtyChange(draft != value || saveError || conflictingDraft != null) }

    LaunchedEffect(draft, value, preservationCount) {
        if (conflictingDraft != null) return@LaunchedEffect
        if (draft != value) {
            // A draft is written to a separate atomic file before touching projects.json.
            try {
                draftStore.save(projectId, document, value, draft)
                lastWrittenDraft = DraftStorage.Draft(draft, value)
                recoveryError = false
            } catch (_: Exception) {
                recoveryError = true
                return@LaunchedEffect
            }
            delay(700)
            if (!isLatestDraft(draft, latestDraft)) return@LaunchedEffect
            try {
                latestSave(draft)
                saveError = false
                // Failure to delete a now-redundant recovery file must not reclassify successful persistence.
                if (isLatestDraft(draft, latestDraft)) runCatching { draftStore.discard(projectId, document) }
            } catch (_: Exception) {
                saveError = true
            }
        } else if (!recoveryError) {
            runCatching {
                val stored = draftStore.load(projectId, document)
                // Reverting cancels autosave; remove only our abandoned draft or redundant saved text.
                if (stored != null && stored.baseText == value &&
                    (stored == lastWrittenDraft || stored.text == value)) {
                    draftStore.discard(projectId, document)
                }
            }
        }
    }
    ScrollableDocumentBody(
        modifier = Modifier.fillMaxSize().imePadding(),
        action = {
            FilledActionButton(onClick = {
                try {
                    draftStore.save(projectId, document, value, draft)
                    latestSave(draft)
                    saveError = false
                    recoveryError = false
                    runCatching { draftStore.discard(projectId, document) }
                } catch (_: Exception) { saveError = true }
            }, enabled = !recoveryError && conflictingDraft == null && (draft != value || saveError)) { VNextIcon(R.drawable.vnext_save, tint = androidx.compose.ui.graphics.Color.White); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.editor_save)) }
        },
        chrome = {
            VNextPanel {
                VNextSectionTitle(hint, if (document == "blueprint") R.drawable.vnext_layers else R.drawable.vnext_route)
                ParagraphText(
                    when {
                        recoveryError -> stringResource(R.string.editor_recovery_error)
                        conflictingDraft != null -> stringResource(R.string.editor_conflict)
                        saveError -> stringResource(R.string.editor_save_error)
                        draft != value -> stringResource(R.string.editor_saving)
                        else -> stringResource(R.string.editor_saved)
                    },
                    style = ProjectTextStyles.metadata,
                    color = if (saveError || recoveryError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (recoveryError) {
                    if (preservationFailed) ParagraphText(stringResource(R.string.editor_preservation_failed))
                    SecondaryActionButton(onClick = {
                        try {
                            draftStore.preserveUnreadable(projectId, document)
                            recoveryError = false
                            preservationFailed = false
                            preservationCount++
                        } catch (_: Exception) { preservationFailed = true }
                    }) { Text(stringResource(R.string.editor_preserve_and_continue)) }
                }
                if (preservationCount > 0) ParagraphText(stringResource(R.string.editor_preserved_note))
                if (conflictingDraft != null) {
                    ParagraphText(stringResource(R.string.editor_previous_draft), style = ProjectTextStyles.metadata)
                    ParagraphText(conflictingDraft!!, style = ProjectTextStyles.supporting, maxLines = 5)
                    SecondaryActionButton(onClick = {
                        // Explicit choice: recover the older text; a new draft records the current base.
                        draft = conflictingDraft!!
                        conflictingDraft = null
                    }) { Text(stringResource(R.string.editor_use_previous_draft)) }
                    TextButton(onClick = {
                        // Explicit user choice; remove only this conflicting recovery draft.
                        try {
                            draftStore.discard(projectId, document)
                            conflictingDraft = null
                            recoveryError = false
                        } catch (_: IOException) { recoveryError = true }
                    }) { Text(stringResource(R.string.editor_keep_current_text)) }
                }
            }
        }, editor = {
            VNextPreviewSurface {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it },
                        enabled = !recoveryError,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = contentTextStyle(), shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer), label = { Text(stringResource(R.string.editor_document_content)) },
                        placeholder = { Text(stringResource(R.string.editor_document_placeholder)) }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ParagraphText(stringResource(R.string.editor_markdown), style = ProjectTextStyles.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fillWidth = false)
                        ParagraphText(stringResource(R.string.editor_stats, draft.lineSequence().count(), draft.trim().split(Regex("\\s+")).count { it.isNotBlank() }),
                            style = ProjectTextStyles.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fillWidth = false)
                    }
                }
            }
        }
    )
}

@Composable
private fun ConfirmDiscardEdits(reload: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    VNextDialog(onDismissRequest = onCancel,
        title = { ParagraphText(stringResource(R.string.unsaved_title)) },
        text = { ParagraphText(stringResource(if (reload) R.string.unsaved_reload_message else R.string.unsaved_discard_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(if (reload) R.string.unsaved_reload_confirm else R.string.unsaved_discard_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.unsaved_continue_editing)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskScreen(
    project: ProjectItem,
    onReloadTitle: (String) -> ProjectItem,
    onEditTitle: (String, String, EditorSnapshot) -> Boolean,
    update: (ProjectItem) -> Boolean
) {
    var editingTaskId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable(project.id) { mutableStateOf("") }
    var selectedStatus by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var statusMenuExpanded by remember(project.id) { mutableStateOf(false) }
    val visibleTasks = filterTasksByQuery(project.tasks, searchQuery, selectedStatus)
    val taskListState = rememberLazyListState()
    var newTask by rememberSaveable(project.id) { mutableStateOf("") }
    val editingTask = project.tasks.find { it.id == editingTaskId }
    if (editingTask != null) {
        key(project.id, editingTask.id) {
            var title by rememberSaveable { mutableStateOf(editingTask.title) }
            var editorBase by rememberSaveable(stateSaver = EditorSnapshot.saver) { mutableStateOf(editorSnapshot(project)) }
            var saveError by rememberSaveable { mutableStateOf(false) }
            var discardAction by rememberSaveable { mutableStateOf<String?>(null) }
            fun dismissEditor() {
                if (title != editingTask.title) discardAction = "close" else editingTaskId = null
            }
            VNextDialog(onDismissRequest = { dismissEditor() },
                title = { ParagraphText(stringResource(R.string.task_edit_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (saveError) ParagraphText(stringResource(R.string.task_edit_save_error), color = MaterialTheme.colorScheme.error)
                        if (saveError) ParagraphText(stringResource(R.string.task_edit_stale_error))
                        TextButton(onClick = { discardAction = "reload" }) { Text(stringResource(R.string.task_edit_reload)) }
                        OutlinedTextField(title, { title = it }, textStyle = contentTextStyle(), label = { Text(stringResource(R.string.task_edit_label)) },
                            isError = title.isBlank(), modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { TextButton(onClick = {
                    if (onEditTitle(editingTask.id, title, editorBase)) editingTaskId = null else saveError = true
                }, enabled = title.isNotBlank()) { Text(stringResource(R.string.task_edit_save)) } },
                dismissButton = { TextButton(onClick = { dismissEditor() }) { Text(stringResource(R.string.task_edit_cancel)) } })
            if (discardAction != null) ConfirmDiscardEdits(
                reload = discardAction == "reload",
                onCancel = { discardAction = null },
                onConfirm = {
                    if (discardAction == "reload") {
                        try {
                            val latest = onReloadTitle(editingTask.id)
                            title = latest.tasks.first { it.id == editingTask.id }.title
                            editorBase = editorSnapshot(latest)
                            saveError = false
                        } catch (_: Exception) { saveError = true }
                    } else editingTaskId = null
                    discardAction = null
                })
        }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), state = taskListState,
        contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { VNextPanel {
        VNextSectionTitle(stringResource(R.string.task_screen_title), R.drawable.vnext_check)
        ParagraphText(stringResource(R.string.task_screen_manual_warning), style = MaterialTheme.typography.bodySmall)
        ParagraphText(stringResource(R.string.task_screen_total, project.tasks.size), style = ProjectTextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        VNextProgress(project)
        val statusCounts = project.tasks.groupingBy { it.status }.eachCount()
        val otherStatusCount = project.tasks.size - listOf("NOT_STARTED", "IN_PROGRESS", "IMPLEMENTED_UNVERIFIED", "VERIFIED").sumOf { statusCounts[it] ?: 0 }
        ParagraphText(stringResource(R.string.task_screen_status_summary,
            statusCounts["NOT_STARTED"] ?: 0, statusCounts["IN_PROGRESS"] ?: 0,
            statusCounts["IMPLEMENTED_UNVERIFIED"] ?: 0, statusCounts["VERIFIED"] ?: 0, otherStatusCount),
            style = ProjectTextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val addTask: () -> Unit = {
            if (update(project.copy(tasks = project.tasks + TaskItem(UUID.randomUUID().toString(), newTask.trim())))) {
                newTask = ""
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compactForm = maxWidth >= 340.dp && LocalDensity.current.fontScale < 1.3f
            if (compactForm) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newTask, { newTask = it },
                        textStyle = contentTextStyle(), label = { Text(stringResource(R.string.task_screen_new)) },
                        modifier = Modifier.weight(1f))
                    FilledActionButton(onClick = addTask, enabled = newTask.isNotBlank()) {
                        Text(stringResource(R.string.task_screen_add))
                    }
                }
            } else {
                OutlinedTextField(newTask, { newTask = it },
                    textStyle = contentTextStyle(), label = { Text(stringResource(R.string.task_screen_new)) },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                FilledActionButton(onClick = addTask, enabled = newTask.isNotBlank()) {
                    Text(stringResource(R.string.task_screen_add))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        } }
            item {
                OutlinedTextField(searchQuery, { searchQuery = it }, textStyle = contentTextStyle(), label = { Text(stringResource(R.string.task_screen_search)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) TextButton(onClick = { searchQuery = "" }) { Text(stringResource(R.string.task_screen_clear_search)) }
                    })
            }
            item {
                val statusOptions = listOf(
                    null to stringResource(R.string.task_status_all),
                    "NOT_STARTED" to stringResource(R.string.task_status_not_started),
                    "IN_PROGRESS" to stringResource(R.string.task_status_in_progress),
                    "IMPLEMENTED_UNVERIFIED" to stringResource(R.string.task_status_implemented_unverified),
                    "VERIFIED" to stringResource(R.string.task_status_verified)
                )
                Box {
                    TextButton(onClick = { statusMenuExpanded = true }) {
                        Text(stringResource(R.string.task_screen_status_filter, statusOptions.first { it.first == selectedStatus }.second))
                    }
                    DropdownMenu(expanded = statusMenuExpanded, onDismissRequest = { statusMenuExpanded = false }) {
                        statusOptions.forEach { (status, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                selectedStatus = status
                                statusMenuExpanded = false
                            })
                        }
                    }
                }
            }
            if (visibleTasks.isEmpty()) {
                item {
                    val message = if (project.tasks.isEmpty()) R.string.tasks_empty else R.string.tasks_no_matches
                    ParagraphText(stringResource(message), color = MaterialTheme.colorScheme.onSurface,
                        style = ProjectTextStyles.supporting)
                }
            }
            items(visibleTasks, key = { it.id }) { task ->
                VNextCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VNextIcon(if (task.status == "VERIFIED") R.drawable.vnext_check else R.drawable.vnext_info)
                            ParagraphText(task.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        }
                        VNextStatusPill(localizedTaskStatus(task.status))
                        TextButton(onClick = { editingTaskId = task.id }) { Text(stringResource(R.string.task_edit_button)) }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                update(project.copy(tasks = project.tasks.map {
                                    if (it.id == task.id) it.copy(status = "IN_PROGRESS") else it
                                }))
                            }) { Text(stringResource(R.string.task_action_in_progress)) }
                            TextButton(onClick = {
                                update(project.copy(tasks = project.tasks.map {
                                    if (it.id == task.id) it.copy(status = "IMPLEMENTED_UNVERIFIED") else it
                                }))
                            }) { Text(stringResource(R.string.task_action_implemented_unverified)) }
                            TextButton(onClick = {
                                update(project.copy(tasks = project.tasks.map {
                                    if (it.id == task.id) it.copy(status = "VERIFIED") else it
                                }))
                            }) { Text(stringResource(R.string.task_action_verified)) }
                        }
                    }
                }
            }
    }
}
