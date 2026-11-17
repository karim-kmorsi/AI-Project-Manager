package com.karim.aiprojectmanager

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope

data class TaskItem(val id: String, val title: String, val status: String = "NOT_STARTED")
data class ProjectItem(
    val id: String, val name: String, val description: String,
    val blueprint: String = "", val roadmap: String = "",
    val tasks: List<TaskItem> = emptyList()
)

open class MainActivity : ComponentActivity() {
    private val languagePreferences by lazy { AppLanguagePreferences(this) }
    private val themePreferences by lazy { ThemePreferences({ AndroidAppPreferences.open(applicationContext) }) }
    private var startupCompleted = false
    private lateinit var storage: ProjectStorage
    private var pendingExportToken: String? = null
    private var exportPreparing = false
    private val exportStore by lazy { PendingExport(File(cacheDir, "pending-exports")) }
    private var restorePreviewState by mutableStateOf<RestorePreview.Summary?>(null)
    private var restorePreviewError by mutableStateOf<String?>(null)
    private var restoreCandidateState by mutableStateOf<RestoreCandidate.Staged?>(null)
    private var restorePreviewJob: Job? = null
    private val restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restorePreviewJob?.cancel()
        restoreCandidateState?.file?.delete()
        restoreCandidateState = null
        restorePreviewState = null
        restorePreviewError = null
        restorePreviewJob = if (uri != null) lifecycleScope.launch {
            val candidateFile = File(cacheDir, "restore-candidate-${UUID.randomUUID()}.json")
            try {
                val staged = withContext(Dispatchers.IO) {
                    val summary = RestoreCandidate.stageSelection(contentResolver, uri, candidateFile)
                    RestoreCandidate.Staged(
                        candidateFile,
                        summary,
                        RestoreTransaction.fingerprint(storage.file)
                    )
                }
                restoreCandidateState = staged
                restorePreviewState = staged.summary
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                candidateFile.delete()
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
        outState.putBoolean("startupCompleted", startupCompleted)
        outState.putString("pendingExportToken", pendingExportToken)
        super.onSaveInstanceState(outState)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startupCompleted = savedInstanceState?.getBoolean("startupCompleted") ?: false
        pendingExportToken = savedInstanceState?.getString("pendingExportToken")
        storage = ProjectStorage(File(filesDir, "projects.json"))
        setContent {
            val appearance by themePreferences.state.collectAsState()
            var showStartup by rememberSaveable { mutableStateOf(!startupCompleted) }
            var visibleNanos by rememberSaveable { mutableLongStateOf(0L) }
            var startupAttempt by remember { mutableIntStateOf(0) }
            val startupLoader = remember {
                StartupLoader {
                    if (themePreferences.state.value.error != null) themePreferences.retry()
                    else themePreferences.load()
                    if (themePreferences.state.value.error != null) throw IOException("Appearance unavailable")
                    storage.load(readOnly = true)
                }
            }
            val startup by startupLoader.state.collectAsState()
            LaunchedEffect(startupAttempt) {
                startupLoader.load()
            }
            val ready by rememberUpdatedState(startup.projects != null && !startup.failed)
            if (showStartup) {
                val clock = remember { StartupClock(visibleNanos) }
                DisposableEffect(clock) {
                    clock.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> clock.setResumed(true)
                            Lifecycle.Event.ON_PAUSE -> clock.setResumed(false)
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(clock) {
                    while (showStartup) {
                        withFrameNanos { frame ->
                            clock.onFrame(frame)
                            visibleNanos = clock.visibleNanos
                            if (clock.canDismiss(ready)) {
                                startupCompleted = true
                                showStartup = false
                            }
                        }
                    }
                }
            }
            val startupVisible = showStartup || startup.projects == null
            BackHandler(enabled = startupVisible) { /* Keep startup gated; Home can background the app. */ }
            val dark = if (startupVisible) true else appearance.mode.isDark()
            AppTheme(dark) {
                SideEffect { updateWindowAppearance(this@MainActivity, dark) }
                if (startupVisible) {
                    StartupSplash(loading = startup.loading, failed = startup.failed,
                        onRetry = { startupAttempt++ })
                } else ProjectManagerApp(storage, initialProjects = startup.projects, appearanceSettings = {
                    AppearanceSettings(appearance,
                        onSelect = { mode -> lifecycleScope.launch { themePreferences.select(mode) } },
                        onRetry = { lifecycleScope.launch { themePreferences.retry() } },
                        language = languagePreferences.current(),
                        onLanguageSelect = { language ->
                            val preferences = languagePreferences
                            if (preferences.current() != language) {
                                if (preferences.select(language)) recreate()
                                else notifyUser(getString(R.string.language_save_error))
                            }
                        })
                }, onRestorePreview = {
            // An older provider read must not publish into a newer picker/preview request.
            restorePreviewJob?.cancel()
            restoreCandidateState?.file?.delete()
            restoreCandidateState = null
            restorePreviewState = null
            restorePreviewError = null
            try { restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            catch (_: Exception) { restorePreviewError = getString(R.string.backup_picker_error) }
        }, restorePreview = restorePreviewState, restoreError = restorePreviewError,
        restoreCandidate = restoreCandidateState,
        onDismissPreview = {
            restoreCandidateState?.file?.delete()
            restoreCandidateState = null
            restorePreviewState = null
            restorePreviewError = null
        }, onExport = {
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

class ProjectStorage(internal val file: File) {
    fun load(readOnly: Boolean = false): List<ProjectItem> = synchronized(ProjectWriteLock.monitor) {
        if (readOnly && (File(file.path + ".new").exists() || File(file.path + ".bak").exists())) {
            throw IOException("Interrupted project write requires recovery; startup leaves all bytes untouched")
        }
        if (!file.exists() && !File(file.path + ".bak").exists()) {
            if (File(file.path + ".new").exists()) throw IOException("Interrupted initial write; recovery required")
            return emptyList()
        }
        val array = try {
            val input = if (readOnly) file.inputStream() else AtomicFile(file).openRead()
            JSONArray(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) {
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
    restoreCandidate: RestoreCandidate.Staged? = null,
    onDismissPreview: () -> Unit = {},
    appearanceSettings: @Composable () -> Unit = {},
    initialProjects: List<ProjectItem>? = null
) {
    var loadError by remember { mutableStateOf(false) }
    val projects = remember {
        mutableStateListOf<ProjectItem>().apply {
            try { addAll(initialProjects ?: storage.load()) } catch (_: Exception) { loadError = true }
        }
    }
    var writeError by remember { mutableStateOf(false) }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showRestoreConfirmation by rememberSaveable { mutableStateOf(false) }
    var restoreInProgress by remember { mutableStateOf(false) }
    var restoreFailure by remember { mutableStateOf(false) }
    var showRepositories by remember { mutableStateOf(false) }
    val applicationContext = LocalContext.current.applicationContext
    val githubSnapshotStore = remember(applicationContext) {
        GitHubProjectSnapshotStore(GitHubSnapshotPreferences.open(applicationContext))
    }
    val githubSignIn = remember {
        GitHubSignIn(persistence = EncryptedGitHubSessionPersistence(applicationContext))
    }
    val githubScope = rememberCoroutineScope()
    LaunchedEffect(githubSignIn) { githubSignIn.restore(githubScope) }
    LaunchedEffect(restoreCandidate?.file?.path) {
        restoreFailure = false
        showRestoreConfirmation = false
    }
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
    fun importGitHubProject(
        repository: GitHubRepository,
        planningDocuments: Map<GitHubPlanningFile, GitHubPreviewSection<GitHubPlanningDocument>>
    ): GitHubImportOutcome {
        if (loadError) return GitHubImportOutcome.FAILED
        val current = projects.toList()
        val proposal = previewGitHubImport(repository, current, planningDocuments)
            ?: return GitHubImportOutcome.FAILED
        if (proposal.candidates.isNotEmpty()) return GitHubImportOutcome.DUPLICATE
        return try {
            persistProjectChange(storage, current, current + proposal.project)
            projects.add(proposal.project)
            activeId = proposal.project.id
            showSettings = false
            showRepositories = false
            GitHubImportOutcome.IMPORTED
        } catch (_: Exception) {
            GitHubImportOutcome.FAILED
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
    val dialogBodyMaxHeight = computeDialogBodyMaxHeight()
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
        if (active == null && !loadError && !settingsVisible) DashboardHomeActionBar(
            onImport = {
                if (githubSignIn.session != null) showRepositories = true else showSettings = true
            },
            onCreate = {
                writeError = false
                showNew = true
            }
        )
    }) { padding ->
        if (settingsVisible) {
            SettingsScreen(
                Modifier.padding(padding), onExport, onRestorePreview, githubSignIn,
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
                val tightTabs = density.fontScale > 1.3f && WindowInsets.ime.getBottom(density) > 0
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (tightTabs) 0.dp else 4.dp)) {
                    val columns = if (maxWidth < 400.dp || density.fontScale > 1.15f) 2 else 4
                    val tabs = listOf(
                        R.string.project_tab_overview to R.drawable.vnext_info,
                        R.string.project_tab_blueprint to R.drawable.vnext_layers,
                        R.string.project_tab_roadmap to R.drawable.vnext_route,
                        R.string.project_tab_tasks to R.drawable.vnext_check
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tabs.chunked(columns).forEachIndexed { rowIndex, rowTabs ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowTabs.forEachIndexed { columnIndex, (label, icon) ->
                                    val index = rowIndex * columns + columnIndex
                                    val selected = index == tab
                                    Surface(onClick = {
                                        if (!selected && editorDirty) warnOnExit = true else tab = index
                                    }, modifier = Modifier.weight(1f).testTag("project_tab_$index").heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(24.dp),
                                        color = if (selected) FilledActionBlue else MaterialTheme.colorScheme.surfaceContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            VNextIcon(icon, modifier = Modifier.size(18.dp),
                                                tint = if (selected) androidx.compose.ui.graphics.Color.White
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) androidx.compose.ui.graphics.Color.White
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                when (tab) {
                    0 -> key(active.id) {
                        ProjectOverviewScreen(
                            active,
                            githubSignIn.session != null,
                            onEdit = { editingProjectId = active.id },
                            repositoryApi = githubSignIn.session?.repositoryApi,
                            snapshotStore = githubSnapshotStore
                        )
                    }
                    1 -> key(active.id) { EditableDocument(active.id, "blueprint", active.blueprint, stringResource(R.string.blueprint_editor_hint), { editorDirty = it }) {
                        update(active.copy(blueprint = it))
                    } }
                    2 -> key(active.id) { EditableDocument(active.id, "roadmap", active.roadmap, stringResource(R.string.roadmap_editor_hint), { editorDirty = it }) {
                        update(active.copy(roadmap = it))
                    } }
                    3 -> taskScreenStates.SaveableStateProvider(active.id) {
                        TaskScreen(active, repositoryApi = githubSignIn.session?.repositoryApi, onReloadTitle = { taskId ->
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
    val repositorySession = githubSignIn.session
    LaunchedEffect(showRepositories, repositorySession) {
        if (showRepositories && repositorySession == null) showRepositories = false
    }
    if (showRepositories && repositorySession != null) {
        GitHubRepositoriesDialog(
            source = repositorySession.repositoryApi,
            onExpired = {
                showRepositories = false
                githubSignIn.expire(repositorySession, githubScope)
            },
            onClose = { showRepositories = false },
            previewSource = repositorySession.repositoryApi,
            localProjects = projects.takeUnless { loadError },
            onImport = ::importGitHubProject
        )
    }
    if (restorePreview != null || restoreError != null) {
        VNextDialog(onDismissRequest = onDismissPreview,
            title = { ParagraphText(stringResource(if (restoreError != null) R.string.backup_preview_error_title else R.string.backup_preview_title)) },
            text = {
                Column(Modifier.heightIn(max = dialogBodyMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (restoreError != null) ParagraphText(restoreError)
                    else if (restorePreview != null) {
                        ParagraphText(stringResource(R.string.backup_preview_counts, restorePreview.projects.size, restorePreview.totalTasks))
                        restorePreview.projects.take(30).forEach { project ->
                            ParagraphText(stringResource(R.string.backup_preview_project_tasks, project.name, project.taskCount))
                        }
                        if (restorePreview.projects.size > 30) ParagraphText(stringResource(R.string.backup_preview_limit))
                        ParagraphText(stringResource(R.string.backup_preview_confirmation_warning), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }, confirmButton = {
                if (restoreCandidate != null) TextButton(onClick = { showRestoreConfirmation = true }) {
                    Text(stringResource(R.string.backup_restore_review))
                } else TextButton(onClick = onDismissPreview) { Text(stringResource(R.string.generic_close)) }
            }, dismissButton = if (restoreCandidate != null) ({
                TextButton(onClick = onDismissPreview) { Text(stringResource(R.string.generic_close)) }
            }) else null)
    }
    if (showRestoreConfirmation && restoreCandidate != null) {
        VNextDialog(onDismissRequest = {
            if (!restoreInProgress) showRestoreConfirmation = false
        },
            title = { ParagraphText(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Column(Modifier.heightIn(max = dialogBodyMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParagraphText(stringResource(R.string.backup_restore_confirm_message))
                    ParagraphText(pluralStringResource(
                        R.plurals.backup_restore_project_count,
                        restoreCandidate.summary.projects.size,
                        restoreCandidate.summary.projects.size
                    ))
                    ParagraphText(pluralStringResource(
                        R.plurals.backup_restore_task_count,
                        restoreCandidate.summary.totalTasks,
                        restoreCandidate.summary.totalTasks
                    ))
                    ParagraphText(stringResource(R.string.backup_restore_safety_copy))
                    if (restoreFailure) ParagraphText(
                        stringResource(R.string.backup_restore_failed),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !restoreInProgress, onClick = {
                    restoreInProgress = true
                    restoreFailure = false
                    val candidate = restoreCandidate
                    githubScope.launch {
                        try {
                            val restored = withContext(Dispatchers.IO) {
                                val snapshot = File(
                                    File(applicationContext.filesDir, "restore-recovery"),
                                    "projects-${System.currentTimeMillis()}-${UUID.randomUUID()}.json"
                                )
                                RestoreTransaction.replace(
                                    target = storage.file,
                                    candidate = candidate.file,
                                    snapshot = snapshot,
                                    authorized = true,
                                    expectedTargetSha256 = candidate.targetFingerprint,
                                    expectedCandidateSha256 = candidate.summary.sha256
                                )
                                storage.load()
                            }
                            projects.clear()
                            projects.addAll(restored)
                            loadError = false
                            activeId = null
                            showSettings = false
                            showRestoreConfirmation = false
                            onDismissPreview()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            restoreFailure = true
                            try {
                                val current = withContext(Dispatchers.IO) { storage.load() }
                                projects.clear()
                                projects.addAll(current)
                                loadError = false
                            } catch (_: Exception) {
                                loadError = true
                            }
                        } finally {
                            restoreInProgress = false
                        }
                    }
                }) {
                    Text(stringResource(if (restoreInProgress) R.string.backup_restore_in_progress else R.string.backup_restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(enabled = !restoreInProgress, onClick = { showRestoreConfirmation = false }) {
                    Text(stringResource(R.string.backup_restore_cancel))
                }
            })
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

@Composable
private fun computeDialogBodyMaxHeight() =
    (with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } - 200.dp)
        .coerceAtLeast(160.dp)
        .coerceAtMost(440.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskScreen(
    project: ProjectItem,
    repositoryApi: GitHubRepositoryApi? = null,
    onReloadTitle: (String) -> ProjectItem,
    onEditTitle: (String, String, EditorSnapshot) -> Boolean,
    update: (ProjectItem) -> Boolean
) {
    var editingTaskId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deletingTaskId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable(project.id) { mutableStateOf("") }
    var selectedStatus by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var statusMenuExpanded by remember(project.id) { mutableStateOf(false) }
    var showRoadmapProposals by rememberSaveable(project.id) { mutableStateOf(false) }
    val visibleTasks = filterTasksByQuery(project.tasks, searchQuery, selectedStatus)
    val taskListState = rememberLazyListState()
    val taskScreenScope = rememberCoroutineScope()
    val dialogBodyMaxHeight = computeDialogBodyMaxHeight()
    var newTask by rememberSaveable(project.id) { mutableStateOf("") }
    var roadmapAnalysis by remember(project.id) { mutableStateOf<RoadmapTaskAnalysis?>(null) }
    LaunchedEffect(showRoadmapProposals, project.id, project.roadmap, project.tasks) {
        roadmapAnalysis = if (showRoadmapProposals) {
            withContext(Dispatchers.Default) { analyzeRoadmapTasks(project.roadmap, project.tasks) }
        } else null
    }
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
    val deletingTask = project.tasks.find { it.id == deletingTaskId }
    if (deletingTask != null) {
        VNextDialog(onDismissRequest = { deletingTaskId = null },
            title = { ParagraphText(stringResource(R.string.task_delete_title)) },
            text = { ParagraphText(stringResource(R.string.task_delete_message, deletingTask.title)) },
            confirmButton = { TextButton(onClick = {
                update(deletedTask(project, deletingTask.id))
                deletingTaskId = null
            }) { Text(stringResource(R.string.task_delete_confirm)) } },
            dismissButton = { TextButton(onClick = { deletingTaskId = null }) {
                Text(stringResource(R.string.task_edit_cancel))
            } })
    }
    val currentRoadmapAnalysis = roadmapAnalysis
    if (showRoadmapProposals && currentRoadmapAnalysis != null) {
        val proposalStateKey = roadmapReviewSnapshot(project)
        var titles by rememberSaveable(proposalStateKey) {
            mutableStateOf(currentRoadmapAnalysis.proposals.map { it.title })
        }
        var selected by rememberSaveable(proposalStateKey) {
            mutableStateOf(currentRoadmapAnalysis.proposals.map { !it.alreadyExists })
        }
        var saveError by rememberSaveable(proposalStateKey) { mutableStateOf(false) }
        val selectedIndexes = selected.indices.filter { selected[it] }
        val conflicts = duplicateRoadmapTaskProposalIndexes(
            selectedIndexes.map { titles[it] },
            project.tasks.map { it.title }
        ).mapTo(HashSet()) { selectedIndexes[it] }
        val canConfirm = selectedIndexes.isNotEmpty() && conflicts.isEmpty()
        VNextDialog(onDismissRequest = { showRoadmapProposals = false },
            title = { ParagraphText(stringResource(R.string.roadmap_proposals_title)) },
            text = {
                LazyColumn(Modifier.heightIn(max = dialogBodyMaxHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ParagraphText(stringResource(R.string.roadmap_proposals_review))
                        if (currentRoadmapAnalysis.ignoredAmbiguousEntries > 0) ParagraphText(
                            pluralStringResource(R.plurals.roadmap_proposals_ambiguous,
                                currentRoadmapAnalysis.ignoredAmbiguousEntries, currentRoadmapAnalysis.ignoredAmbiguousEntries),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (currentRoadmapAnalysis.ignoredCompletedEntries > 0) ParagraphText(
                            pluralStringResource(R.plurals.roadmap_proposals_completed,
                                currentRoadmapAnalysis.ignoredCompletedEntries, currentRoadmapAnalysis.ignoredCompletedEntries),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (currentRoadmapAnalysis.duplicateEntries > 0) ParagraphText(
                            pluralStringResource(R.plurals.roadmap_proposals_duplicates,
                                currentRoadmapAnalysis.duplicateEntries, currentRoadmapAnalysis.duplicateEntries),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (currentRoadmapAnalysis.notShownEntries > 0) ParagraphText(
                            pluralStringResource(R.plurals.roadmap_proposals_limit,
                                currentRoadmapAnalysis.notShownEntries, currentRoadmapAnalysis.notShownEntries),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (currentRoadmapAnalysis.proposals.isEmpty()) ParagraphText(
                            stringResource(R.string.roadmap_proposals_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (saveError) ParagraphText(
                            stringResource(R.string.roadmap_proposals_save_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    itemsIndexed(currentRoadmapAnalysis.proposals, key = { _, proposal -> proposal.sourceLine }) { index, proposal ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selected[index],
                                    onCheckedChange = { checked ->
                                        selected = selected.toMutableList().apply { set(index, checked) }
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    ParagraphText(stringResource(R.string.roadmap_proposals_source_line, proposal.sourceLine),
                                        style = MaterialTheme.typography.bodySmall)
                                    proposal.phase?.let {
                                        ParagraphText(stringResource(R.string.roadmap_proposals_phase, it),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (proposal.alreadyExists && titles[index] == proposal.title) ParagraphText(
                                        stringResource(R.string.roadmap_proposals_existing),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = titles[index],
                                onValueChange = { value ->
                                    titles = titles.toMutableList().apply { set(index, value) }
                                    saveError = false
                                },
                                label = { Text(stringResource(R.string.roadmap_proposals_task_title)) },
                                textStyle = contentTextStyle(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false
                            )
                            if (index in conflicts) ParagraphText(
                                stringResource(R.string.roadmap_proposals_duplicate_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val additions = selectedIndexes.map { index ->
                        TaskItem(UUID.randomUUID().toString(), titles[index].trim())
                    }
                    if (additions.isNotEmpty() && update(project.copy(tasks = project.tasks + additions))) {
                        searchQuery = ""
                        selectedStatus = null
                        showRoadmapProposals = false
                        saveError = false
                        taskScreenScope.launch {
                            taskListState.animateScrollToItem(project.tasks.size + additions.size + 2)
                        }
                    } else saveError = true
                }, enabled = canConfirm) { Text(stringResource(R.string.roadmap_proposals_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showRoadmapProposals = false }) {
                Text(stringResource(R.string.task_edit_cancel))
            } })
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
        if (project.roadmap.isNotBlank()) SecondaryActionButton(
            onClick = { showRoadmapProposals = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            VNextIcon(R.drawable.vnext_route)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.roadmap_proposals_open))
        }
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
            if (project.id.startsWith("github:")) {
                item {
                    GitHubChecklistPanel(project, repositoryApi, update)
                }
            }
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
                            DropdownMenuItem(
                                modifier = Modifier.testTag("task_status_option_${status ?: "ALL"}"),
                                text = { Text(label) }, onClick = {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editingTaskId = task.id }) {
                                Text(stringResource(R.string.task_edit_button))
                            }
                            TextButton(onClick = { deletingTaskId = task.id }) {
                                Text(stringResource(R.string.task_delete_button))
                            }
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                update(project.copy(tasks = project.tasks.map {
                                    if (it.id == task.id) it.copy(status = "NOT_STARTED") else it
                                }))
                            }) { Text(stringResource(R.string.task_status_not_started)) }
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
