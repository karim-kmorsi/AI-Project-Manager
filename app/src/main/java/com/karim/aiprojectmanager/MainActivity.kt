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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
        setContent { MaterialTheme { ProjectManagerApp(storage, onRestorePreview = {
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
    onDismissPreview: () -> Unit = {}
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
    // Respect unsaved document edits when the system Back gesture/button is used.
    BackHandler(enabled = activeId != null) {
        if (editorDirty) warnOnExit = true else activeId = null
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (active == null) "إدارة المشاريع" else active.name) },
            actions = { if (active == null) {
                TextButton(onClick = onExport) { Text("تصدير JSON") }
                TextButton(onClick = onRestorePreview) { Text("معاينة نسخة") }
            } },
            navigationIcon = {
                if (active != null) TextButton(onClick = { if (editorDirty) warnOnExit = true else activeId = null }) { Text("رجوع") }
            })
    }, floatingActionButton = {
        if (active == null && !loadError) FloatingActionButton(onClick = { writeError = false; showNew = true }) { Text("+") }
    }) { padding ->
        if (loadError) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                Text("تعذّر قراءة المشاريع. لم تتغير بياناتك؛ أعد المحاولة بعد معالجة مشكلة التخزين.")
                TextButton(onClick = {
                    try { val loaded = storage.load(); projects.clear(); projects.addAll(loaded); loadError = false }
                    catch (_: Exception) { loadError = true }
                }) { Text("إعادة المحاولة") }
            }
        } else if (active == null) {
            var searchQuery by rememberSaveable { mutableStateOf("") }
            val visibleProjects = filterProjectsByQuery(projects, searchQuery)
            LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("مشاريعي", style = MaterialTheme.typography.headlineMedium) }
                item { Text("كل مشروع مستقل ببلوبرنت وخريطة طريق ومهام خاصة به.") }
                if (projects.isEmpty()) item { Text("لا توجد مشاريع بعد. اضغط + لإضافة مشروع.") }
                if (projects.isNotEmpty()) {
                    item { OutlinedTextField(searchQuery, { searchQuery = it }, label = { Text("بحث في المشاريع") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    if (visibleProjects.isEmpty()) item { Text("لا توجد مشاريع تطابق البحث.") }
                }
                items(visibleProjects, key = { it.id }) { item ->
                    ElevatedCard(onClick = { activeId = item.id }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.description)
                            Text("المهام المتحققة: ${item.tasks.count { it.status == "VERIFIED" }} / ${item.tasks.size}")
                        }
                    }
                }
            }
        } else {
            var tab by rememberSaveable(active.id) { mutableIntStateOf(0) }
            Column(Modifier.padding(padding)) {
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
                            Text(active.description)
                            val verified = active.tasks.count { it.status == "VERIFIED" }
                            Text("تقدم مثبت: $verified من ${active.tasks.size} مهمة")
                            Text(taskStatusSummary(active.tasks), style = MaterialTheme.typography.bodySmall)
                            val pendingTasks = pendingTaskDetails(active.tasks)
                            if (pendingTasks.isNotEmpty()) {
                                Text("مهام تحتاج متابعة:", style = MaterialTheme.typography.bodySmall)
                                pendingTasks.forEach { detail ->
                                    Text(detail, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text("التقدم لا يُعتبر مكتملًا دون دليل مستقل.", style = MaterialTheme.typography.bodySmall)
                            Text("الخطوة التالية: " + nextStepGuidance(active), style = MaterialTheme.typography.bodySmall)
                            Text("GitHub والفحص الآلي: غير متصلين في هذه النسخة.")
                        }
                    }
                    1 -> key(active.id) { EditableDocument(active.id, "blueprint", active.blueprint, "المخطط الهندسي للمشروع", { editorDirty = it }) {
                        update(active.copy(blueprint = it))
                    } }
                    2 -> key(active.id) { EditableDocument(active.id, "roadmap", active.roadmap, "المراحل والمعالم وشروط النجاح", { editorDirty = it }) {
                        update(active.copy(roadmap = it))
                    } }
                    3 -> TaskScreen(active, onEditTitle = { taskId, title ->
                        try {
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
            title = { Text(if (restoreError != null) "تعذرت المعاينة" else "معاينة نسخة احتياطية") },
            text = {
                if (restoreError != null) Text(restoreError)
                else if (restorePreview != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("عدد المشاريع: ${restorePreview.projects.size} | عدد المهام: ${restorePreview.totalTasks}")
                    restorePreview.projects.take(30).forEach { project ->
                        Text("${project.name} — ${project.taskCount} مهمة")
                    }
                    if (restorePreview.projects.size > 30) Text("تُعرض أول 30 مشروعًا فقط")
                    Text("معاينة فقط: لن يتم استبدال أو دمج أي بيانات.", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = { TextButton(onClick = onDismissPreview) { Text("إغلاق") } })
    }
    if (writeError && !showNew) {
        AlertDialog(onDismissRequest = { writeError = false }, title = { Text("تعذّر الحفظ") },
            text = { Text("تعذّر حفظ المشروع. لم تُعتمد التغييرات؛ حاول مجددًا.") },
            confirmButton = { TextButton(onClick = { writeError = false }) { Text("إغلاق") } })
    }
    if (warnOnExit) {
        AlertDialog(onDismissRequest = { warnOnExit = false }, title = { Text("تعديلات غير محفوظة") },
            text = { Text("لم يُؤكد حفظ التعديلات. ابق في المحرر واضغط حفظ ثم حاول الانتقال مجددًا.") },
            confirmButton = { TextButton(onClick = { warnOnExit = false }) { Text("العودة إلى المحرر") } })
    }
    val editingProject = active?.takeIf { it.id == editingProjectId }
    if (editingProject != null) {
        key(editingProject.id) {
            var name by rememberSaveable { mutableStateOf(editingProject.name) }
            var description by rememberSaveable { mutableStateOf(editingProject.description) }
            var saveError by rememberSaveable { mutableStateOf(false) }
            AlertDialog(onDismissRequest = { editingProjectId = null },
                title = { Text("تعديل المشروع") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (saveError) Text("تعذّر حفظ المشروع. لم تُعتمد التغييرات؛ حاول مجددًا.", color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(name, { name = it }, label = { Text("اسم المشروع") },
                            singleLine = true, isError = name.isBlank())
                        OutlinedTextField(description, { description = it }, label = { Text("وصف مختصر") })
                    }
                },
                confirmButton = { TextButton(onClick = {
                    try {
                        val edited = editedProjectMetadata(editingProject, name, description)
                        if (edited != editingProject) update(edited)
                        editingProjectId = null
                    } catch (_: Exception) { saveError = true }
                }, enabled = name.isNotBlank()) { Text("حفظ") } },
                dismissButton = { TextButton(onClick = { editingProjectId = null }) { Text("إلغاء") } })
        }
    }
    if (showNew) {
        var name by rememberSaveable { mutableStateOf("") }
        var details by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showNew = false },
            title = { Text("إنشاء مشروع") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(contextualTip("new_project"), style = MaterialTheme.typography.bodySmall)
                if (writeError) Text("تعذّر حفظ المشروع. لم تُعتمد التغييرات؛ حاول مجددًا.")
                OutlinedTextField(name, { name = it }, label = { Text("اسم المشروع") })
                OutlinedTextField(details, { details = it }, label = { Text("وصف مختصر") })
            } },
            confirmButton = { TextButton(onClick = {
                if (name.isNotBlank()) {
                    val p = ProjectItem(UUID.randomUUID().toString(), name.trim(), details.trim())
                    try {
                        persistProjectChange(storage, projects.toList(), projects.toList() + p)
                        projects.add(p); activeId = p.id
                        showNew = false; writeError = false
                    } catch (_: Exception) { writeError = true }
                }
            }, enabled = name.isNotBlank()) { Text("إنشاء") } },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("إلغاء") } })
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
    Column(
        Modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(hint, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Button(onClick = {
                try {
                    draftStore.save(projectId, document, value, draft)
                    latestSave(draft)
                    saveError = false
                    recoveryError = false
                    runCatching { draftStore.discard(projectId, document) }
                } catch (_: Exception) { saveError = true }
            }, enabled = !recoveryError && conflictingDraft == null && (draft != value || saveError)) { Text("حفظ") }
        }
        Text(contextualTip(document), style = MaterialTheme.typography.bodySmall)
        Text(
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
            if (preservationFailed) Text("تعذّر الاحتفاظ بالمسودة؛ لم تُحذف أي بيانات. حاول مجددًا؛ ستبقى التعديلات غير المحفوظة محمية من المغادرة.")
            TextButton(onClick = {
                try {
                    draftStore.preserveUnreadable(projectId, document)
                    recoveryError = false
                    preservationFailed = false
                    preservationCount++
                } catch (_: Exception) { preservationFailed = true }
            }) { Text("الاحتفاظ بالمسودة ومتابعة التحرير") }
        }
        if (preservationCount > 0) Text("تم الاحتفاظ بالمسودة السابقة دون تغيير، ولم تتم استعادتها.")
        if (conflictingDraft != null) {
            Text("نص المسودة السابقة (للمراجعة فقط):", style = MaterialTheme.typography.bodySmall)
            Text(conflictingDraft!!, style = MaterialTheme.typography.bodySmall, maxLines = 5)
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
        OutlinedTextField(
            value = draft, onValueChange = { draft = it },
            enabled = !recoveryError,
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("محتوى المستند") },
            placeholder = { Text("اكتب أو الصق الخطة هنا...") }
        )
    }
}

@Composable
private fun TaskScreen(
    project: ProjectItem, onEditTitle: (String, String) -> Boolean, update: (ProjectItem) -> Boolean
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
            var saveError by rememberSaveable { mutableStateOf(false) }
            AlertDialog(onDismissRequest = { editingTaskId = null },
                title = { Text("تعديل عنوان المهمة") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (saveError) Text("تعذّر حفظ المهمة. لم تُعتمد التغييرات؛ حاول مجددًا.", color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(title, { title = it }, label = { Text("عنوان المهمة") },
                            isError = title.isBlank(), modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { TextButton(onClick = {
                    if (onEditTitle(editingTask.id, title)) editingTaskId = null else saveError = true
                }, enabled = title.isNotBlank()) { Text("حفظ") } },
                dismissButton = { TextButton(onClick = { editingTaskId = null }) { Text("إلغاء") } })
        }
    }
    Column(Modifier.padding(16.dp)) {
        Text(contextualTip("tasks"), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(newTask, { newTask = it },
            label = { Text("مهمة جديدة") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (update(project.copy(tasks = project.tasks + TaskItem(UUID.randomUUID().toString(), newTask.trim())))) {
                newTask = ""
            }
        }, enabled = newTask.isNotBlank()) { Text("إضافة") }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(searchQuery, { searchQuery = it }, label = { Text("بحث في المهام") },
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
                item { Text("لا توجد مهام تطابق خيارات البحث والتصفية.") }
            }
            items(visibleTasks, key = { it.id }) { task ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(task.title, fontWeight = FontWeight.Bold)
                        Text(task.status, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { editingTaskId = task.id }) { Text("تعديل العنوان") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
