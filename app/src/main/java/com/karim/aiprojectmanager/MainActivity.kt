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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                restorePreviewError = "ملف النسخة الاحتياطية غير صالح أو تعذرت قراءته. لم تتغير بياناتك."
            }
        } else null
    }
    protected open fun notifyUser(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
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
                    notifyUser("تم تصدير النسخة الاحتياطية")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { notifyUser("فشل التصدير؛ تحقق من الملف في مكان الحفظ") }
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
                        onRetry = { lifecycleScope.launch { themePreferences.retry() } })
                }, onRestorePreview = {
            // An older provider read must not publish into a newer picker/preview request.
            restorePreviewJob?.cancel()
            restorePreviewState = null
            restorePreviewError = null
            try { restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            catch (_: Exception) { restorePreviewError = "تعذّر فتح منتقي الملفات. لم تتغير بياناتك." }
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
                        notifyUser("تعذّر تحضير النسخة الاحتياطية")
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
    var projectSearchQuery by rememberSaveable { mutableStateOf("") }
    val projectListState = rememberLazyListState()
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

    Scaffold(topBar = {
        AccessibleAppBar(title = if (settingsVisible) "الإعدادات" else if (active == null) "إدارة المشاريع" else active.name,
            actions = { if (active == null && !settingsVisible) {
                TextButton(onClick = onExport) { Text("تصدير JSON") }
                TextButton(onClick = onRestorePreview) { Text("معاينة نسخة") }
            } },
            navigation = {
                if (settingsVisible) TextButton(onClick = { showSettings = false }) { Text("رجوع") }
                else if (active != null) TextButton(onClick = { if (editorDirty) warnOnExit = true else activeId = null }) { Text("رجوع") }
            })
    }, floatingActionButton = {
        if (active == null && !loadError && !settingsVisible) FloatingActionButton(
            onClick = { writeError = false; showNew = true },
            modifier = Modifier.semantics { contentDescription = "إنشاء مشروع" }
        ) { Text("+") }
    }) { padding ->
        if (settingsVisible) {
            SettingsScreen(Modifier.padding(padding), onExport, onRestorePreview, appearanceSettings)
        } else if (loadError) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                ParagraphText("تعذّر قراءة المشاريع. لم تتغير بياناتك؛ أعد المحاولة بعد معالجة مشكلة التخزين.")
                TextButton(onClick = {
                    try { val loaded = storage.load(); projects.clear(); projects.addAll(loaded); loadError = false }
                    catch (_: Exception) { loadError = true }
                }) { Text("إعادة المحاولة") }
            }
        } else if (active == null) {
            val visibleProjects = filterProjectsByQuery(projects, projectSearchQuery)
            LazyColumn(Modifier.padding(padding).padding(16.dp), state = projectListState,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ParagraphText("مشاريعي", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                        TextButton(onClick = { showSettings = true }) { Text("الإعدادات") }
                    }
                }
                item { ParagraphText("كل مشروع مستقل ببلوبرنت وخريطة طريق ومهام خاصة به.") }
                if (projects.isEmpty()) item { ParagraphText("لا توجد مشاريع بعد. اضغط + لإضافة مشروع.") }
                if (projects.isNotEmpty()) {
                    item { OutlinedTextField(projectSearchQuery, { projectSearchQuery = it }, textStyle = contentTextStyle(), label = { Text("بحث في المشاريع") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    if (visibleProjects.isEmpty()) item { ParagraphText("لا توجد مشاريع تطابق البحث.") }
                }
                items(visibleProjects, key = { it.id }) { item ->
                    ElevatedCard(onClick = { activeId = item.id }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            ParagraphText(item.name, fontWeight = FontWeight.Bold)
                            ParagraphText(item.description)
                            ParagraphText("المهام المتحققة: " + taskCountExpression(item.tasks.count { it.status == "VERIFIED" }, item.tasks.size))
                        }
                    }
                }
            }
        } else {
            var tab by rememberSaveable(active.id) { mutableIntStateOf(0) }
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                ScrollableTabRow(selectedTabIndex = tab) {
                    listOf("نظرة عامة", "Blueprint", "Roadmap", "المهام").forEachIndexed { index, label ->
                        Tab(selected = index == tab, onClick = { if (index != tab && editorDirty) warnOnExit = true else tab = index }, text = { Text(label) })
                    }
                }
                when (tab) {
                    0 -> key(active.id) {
                        val overviewScrollState = rememberScrollState()
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(overviewScrollState).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(onClick = { editingProjectId = active.id }) { Text("تعديل المشروع") }
                            ParagraphText(active.description)
                            val verified = active.tasks.count { it.status == "VERIFIED" }
                            ParagraphText("تقدم مثبت: $verified من ${active.tasks.size} مهمة")
                            ParagraphText(taskStatusSummary(active.tasks), style = MaterialTheme.typography.bodySmall)
                            val pendingTasks = pendingTaskDetails(active.tasks)
                            if (pendingTasks.isNotEmpty()) {
                                ParagraphText("مهام تحتاج متابعة:", style = MaterialTheme.typography.bodySmall)
                                pendingTasks.forEach { detail ->
                                    ParagraphText(detail, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            ParagraphText("التقدم لا يُعتبر مكتملًا دون دليل مستقل.", style = MaterialTheme.typography.bodySmall)
                            ParagraphText("الخطوة التالية: " + nextStepGuidance(active), style = MaterialTheme.typography.bodySmall)
                            ParagraphText("GitHub والفحص الآلي: غير متصلين في هذه النسخة.")
                        }
                    }
                    1 -> key(active.id) { EditableDocument(active.id, "blueprint", active.blueprint, "المخطط الهندسي للمشروع", { editorDirty = it }) {
                        update(active.copy(blueprint = it))
                    } }
                    2 -> key(active.id) { EditableDocument(active.id, "roadmap", active.roadmap, "المراحل والمعالم وشروط النجاح", { editorDirty = it }) {
                        update(active.copy(roadmap = it))
                    } }
                    3 -> TaskScreen(active, onReloadTitle = { taskId ->
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
    if (restorePreview != null || restoreError != null) {
        AlertDialog(onDismissRequest = onDismissPreview,
            title = { ParagraphText(if (restoreError != null) "تعذرت المعاينة" else "معاينة نسخة احتياطية") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (restoreError != null) ParagraphText(restoreError)
                    else if (restorePreview != null) {
                        ParagraphText("عدد المشاريع: ${restorePreview.projects.size} | عدد المهام: ${restorePreview.totalTasks}")
                        restorePreview.projects.take(30).forEach { project ->
                            ParagraphText("${project.name} — ${project.taskCount} مهمة")
                        }
                        if (restorePreview.projects.size > 30) ParagraphText("تُعرض أول 30 مشروعًا فقط")
                        ParagraphText("معاينة فقط: لن يتم استبدال أو دمج أي بيانات.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }, confirmButton = { TextButton(onClick = onDismissPreview) { Text("إغلاق") } })
    }
    if (writeError && !showNew) {
        AlertDialog(onDismissRequest = { writeError = false }, title = { ParagraphText("تعذّر الحفظ") },
            text = { ParagraphText("تعذّر حفظ المشروع. لم تُعتمد التغييرات؛ حاول مجددًا.") },
            confirmButton = { TextButton(onClick = { writeError = false }) { Text("إغلاق") } })
    }
    if (warnOnExit) {
        AlertDialog(onDismissRequest = { warnOnExit = false }, title = { ParagraphText("تعديلات غير محفوظة") },
            text = { ParagraphText("لم يُؤكد حفظ التعديلات. ابق في المحرر واضغط حفظ ثم حاول الانتقال مجددًا.") },
            confirmButton = { TextButton(onClick = { warnOnExit = false }) { Text("العودة إلى المحرر") } })
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
            AlertDialog(onDismissRequest = { dismissEditor() },
                title = { ParagraphText("تعديل المشروع") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (saveError) ParagraphText("تعذّر حفظ المشروع. لم تُعتمد التغييرات؛ حاول مجددًا.", color = MaterialTheme.colorScheme.error)
                        if (saveError) ParagraphText("قد تكون البيانات المحفوظة أحدث أو تعذرت قراءتها. أعد تحميل القيم الحالية ثم راجع تعديلاتك.")
                        TextButton(onClick = { discardAction = "reload" }) { Text("إعادة تحميل القيم الحالية") }
                        OutlinedTextField(name, { name = it }, textStyle = contentTextStyle(), label = { Text("اسم المشروع") },
                            singleLine = true, isError = name.isBlank())
                        OutlinedTextField(description, { description = it }, textStyle = contentTextStyle(), label = { Text("وصف مختصر") })
                    }
                },
                confirmButton = { TextButton(onClick = {
                    try {
                        if (editorSnapshot(editingProject) != editorBase) throw IOException("Editor base changed")
                        val edited = editedProjectMetadata(editingProject, name, description)
                        if (edited != editingProject) update(edited)
                        editingProjectId = null
                    } catch (_: Exception) { saveError = true }
                }, enabled = name.isNotBlank()) { Text("حفظ") } },
                dismissButton = { TextButton(onClick = { dismissEditor() }) { Text("إلغاء") } })
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
        modifier = Modifier.fillMaxSize().imePadding().padding(16.dp),
        action = {
            FilledActionButton(onClick = {
                try {
                    draftStore.save(projectId, document, value, draft)
                    latestSave(draft)
                    saveError = false
                    recoveryError = false
                    runCatching { draftStore.discard(projectId, document) }
                } catch (_: Exception) { saveError = true }
            }, enabled = !recoveryError && conflictingDraft == null && (draft != value || saveError)) { Text("حفظ") }
        },
        chrome = {
            ParagraphText(hint, fontWeight = FontWeight.Medium)
            ParagraphText(contextualTip(document), style = MaterialTheme.typography.bodySmall)
            ParagraphText(
                when {
                    recoveryError -> "تعذّر حفظ أو قراءة المسودة. لم تتغير بياناتك؛ التحرير متوقف حتى الاحتفاظ بالمسودة."
                    conflictingDraft != null -> "تعارض: توجد مسودة سابقة مختلفة عن النص الحالي؛ راجعها قبل اعتمادها"
                    saveError -> "فشل حفظ المشروع — المسودة الاحتياطية محفوظة إن أمكن"
                    draft != value -> "تعديلات قيد الحفظ..."
                    else -> "تم الحفظ"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (saveError || recoveryError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (recoveryError) {
                if (preservationFailed) ParagraphText("تعذّر الاحتفاظ بالمسودة؛ لم تُحذف أي بيانات. حاول مجددًا؛ ستبقى التعديلات غير المحفوظة محمية من المغادرة.")
                TextButton(onClick = {
                    try {
                        draftStore.preserveUnreadable(projectId, document)
                        recoveryError = false
                        preservationFailed = false
                        preservationCount++
                    } catch (_: Exception) { preservationFailed = true }
                }) { Text("الاحتفاظ بالمسودة ومتابعة التحرير") }
            }
            if (preservationCount > 0) ParagraphText("تم الاحتفاظ بالمسودة السابقة دون تغيير، ولم تتم استعادتها.")
            if (conflictingDraft != null) {
                ParagraphText("نص المسودة السابقة (للمراجعة فقط):", style = MaterialTheme.typography.bodySmall)
                ParagraphText(conflictingDraft!!, style = MaterialTheme.typography.bodySmall, maxLines = 5)
                TextButton(onClick = {
                    // Explicit choice: recover the older text; a new draft records the current base.
                    draft = conflictingDraft!!
                    conflictingDraft = null
                }) { Text("استخدام المسودة السابقة") }
                TextButton(onClick = {
                    // Explicit user choice; remove only this conflicting recovery draft.
                    try {
                        draftStore.discard(projectId, document)
                        conflictingDraft = null
                        recoveryError = false
                    } catch (_: IOException) { recoveryError = true }
                }) { Text("الاحتفاظ بنص المشروع الحالي") }
            }
        }, editor = {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                enabled = !recoveryError,
                modifier = Modifier.fillMaxWidth(),
                textStyle = contentTextStyle(), label = { Text("محتوى المستند") },
                placeholder = { Text("اكتب أو الصق الخطة هنا...") }
            )
        }
    )
}

@Composable
private fun ConfirmDiscardEdits(reload: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel,
        title = { ParagraphText("تعديلات غير محفوظة") },
        text = { ParagraphText(if (reload) "إعادة التحميل ستستبدل النص الذي أدخلته بالقيم المحفوظة حاليًا. هل تريد المتابعة؟"
            else "هل تريد تجاهل التعديلات غير المحفوظة؟") },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (reload) "تأكيد إعادة التحميل" else "تجاهل التعديلات") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("متابعة التحرير") } })
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
    var searchQuery by remember(project.id) { mutableStateOf("") }
    var selectedStatus by remember(project.id) { mutableStateOf<String?>(null) }
    var statusMenuExpanded by remember(project.id) { mutableStateOf(false) }
    val visibleTasks = filterTasksByQuery(project.tasks, searchQuery, selectedStatus)
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
            AlertDialog(onDismissRequest = { dismissEditor() },
                title = { ParagraphText("تعديل عنوان المهمة") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (saveError) ParagraphText("تعذّر حفظ المهمة. لم تُعتمد التغييرات؛ حاول مجددًا.", color = MaterialTheme.colorScheme.error)
                        if (saveError) ParagraphText("قد تكون البيانات المحفوظة أحدث أو تعذرت قراءتها. أعد تحميل القيم الحالية ثم راجع تعديلاتك.")
                        TextButton(onClick = { discardAction = "reload" }) { Text("إعادة تحميل القيم الحالية") }
                        OutlinedTextField(title, { title = it }, textStyle = contentTextStyle(), label = { Text("عنوان المهمة") },
                            isError = title.isBlank(), modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { TextButton(onClick = {
                    if (onEditTitle(editingTask.id, title, editorBase)) editingTaskId = null else saveError = true
                }, enabled = title.isNotBlank()) { Text("حفظ") } },
                dismissButton = { TextButton(onClick = { dismissEditor() }) { Text("إلغاء") } })
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
    Column(Modifier.padding(16.dp)) {
        ParagraphText(contextualTip("tasks"), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(newTask, { newTask = it },
            textStyle = contentTextStyle(), label = { Text("مهمة جديدة") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        FilledActionButton(onClick = {
            if (update(project.copy(tasks = project.tasks + TaskItem(UUID.randomUUID().toString(), newTask.trim())))) {
                newTask = ""
            }
        }, enabled = newTask.isNotBlank()) { Text("إضافة") }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(searchQuery, { searchQuery = it }, textStyle = contentTextStyle(), label = { Text("بحث في المهام") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) TextButton(onClick = { searchQuery = "" }) { Text("مسح البحث") }
                    })
            }
            item {
                val statusOptions = listOf(
                    null to "كل الحالات",
                    "NOT_STARTED" to "لم تبدأ",
                    "IN_PROGRESS" to "قيد التنفيذ",
                    "IMPLEMENTED_UNVERIFIED" to "كود غير متحقق",
                    "VERIFIED" to "تم التحقق"
                )
                Box {
                    TextButton(onClick = { statusMenuExpanded = true }) {
                        Text("حالة المهام: " + statusOptions.first { it.first == selectedStatus }.second)
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
                item { ParagraphText("لا توجد مهام تطابق خيارات البحث والتصفية.") }
            }
            items(visibleTasks, key = { it.id }) { task ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        ParagraphText(task.title, fontWeight = FontWeight.Bold)
                        ParagraphText(task.status, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { editingTaskId = task.id }) { Text("تعديل العنوان") }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                update(project.copy(tasks = project.tasks.map {
                                    if (it.id == task.id) it.copy(status = "IN_PROGRESS") else it
                                }))
                            }) { Text("قيد التنفيذ") }
                            TextButton(onClick = {
                                update(project.copy(tasks = project.tasks.map {
                                    if (it.id == task.id) it.copy(status = "IMPLEMENTED_UNVERIFIED") else it
                                }))
                            }) { Text("كود غير متحقق") }
                            TextButton(onClick = {
                                update(project.copy(tasks = project.tasks.map {
                                    if (it.id == task.id) it.copy(status = "VERIFIED") else it
                                }))
                            }) { Text("تم التحقق") }
                        }
                    }
                }
            }
        }
    }
}
