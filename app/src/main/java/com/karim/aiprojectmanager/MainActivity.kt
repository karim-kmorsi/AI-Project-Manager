package com.karim.aiprojectmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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

data class TaskItem(val id: String, val title: String, val status: String = "NOT_STARTED")
data class ProjectItem(
    val id: String, val name: String, val description: String,
    val blueprint: String = "", val roadmap: String = "",
    val tasks: List<TaskItem> = emptyList()
)

class MainActivity : ComponentActivity() {
    private lateinit var storage: ProjectStorage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = ProjectStorage(File(filesDir, "projects.json"))
        setContent { MaterialTheme { ProjectManagerApp(storage) } }
    }
}

class ProjectStorage(private val file: File) {
    fun load(): List<ProjectItem> {
        if (!file.exists() && !File(file.path + ".bak").exists()) return emptyList()
        val array = try { JSONArray(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }) } catch (e: Exception) {
            throw IOException("Cannot read saved projects; writes are blocked until recovery", e)
        }
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

    fun save(data: List<ProjectItem>) {
        // Never overwrite data that cannot be read or validated.
        if (file.exists() || File(file.path + ".bak").exists()) load()
        val arr = JSONArray()
        data.forEach { project ->
            val obj = JSONObject()
            obj.put("id", project.id)
            obj.put("name", project.name)
            obj.put("description", project.description)
            obj.put("blueprint", project.blueprint)
            obj.put("roadmap", project.roadmap)
            val tasks = JSONArray()
            project.tasks.forEach { t ->
                tasks.put(JSONObject().put("id", t.id).put("title", t.title).put("status", t.status))
            }
            obj.put("tasks", tasks)
            arr.put(obj)
        }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(arr.toString(2).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManagerApp(storage: ProjectStorage) {
    val projects = remember { mutableStateListOf<ProjectItem>().apply { addAll(storage.load()) } }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var editorDirty by rememberSaveable { mutableStateOf(false) }
    var warnOnExit by remember { mutableStateOf(false) }
    fun update(item: ProjectItem) {
        val index = projects.indexOfFirst { it.id == item.id }
        if (index >= 0) { projects[index] = item; storage.save(projects.toList()) }
    }
    val active = projects.find { it.id == activeId }
    // Respect unsaved document edits when the system Back gesture/button is used.
    BackHandler(enabled = activeId != null) {
        if (editorDirty) warnOnExit = true else activeId = null
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (active == null) "إدارة المشاريع" else active.name) },
            navigationIcon = {
                if (active != null) TextButton(onClick = { if (editorDirty) warnOnExit = true else activeId = null }) { Text("رجوع") }
            })
    }, floatingActionButton = {
        if (active == null) FloatingActionButton(onClick = { showNew = true }) { Text("+") }
    }) { padding ->
        if (active == null) {
            LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("مشاريعي", style = MaterialTheme.typography.headlineMedium) }
                item { Text("كل مشروع مستقل ببلوبرنت وخريطة طريق ومهام خاصة به.") }
                if (projects.isEmpty()) item { Text("لا توجد مشاريع بعد. اضغط + لإضافة مشروع.") }
                items(projects, key = { it.id }) { item ->
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
            var tab by remember(active.id) { mutableIntStateOf(0) }
            Column(Modifier.padding(padding)) {
                ScrollableTabRow(selectedTabIndex = tab) {
                    listOf("نظرة عامة", "Blueprint", "Roadmap", "المهام").forEachIndexed { index, label ->
                        Tab(selected = index == tab, onClick = { if (index != tab && editorDirty) warnOnExit = true else tab = index }, text = { Text(label) })
                    }
                }
                when (tab) {
                    0 -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(active.description)
                        val verified = active.tasks.count { it.status == "VERIFIED" }
                        Text("تقدم مثبت: $verified من ${active.tasks.size} مهمة")
                        Text("التقدم لا يُعتبر مكتملًا دون دليل مستقل.", style = MaterialTheme.typography.bodySmall)
                        Text("GitHub والفحص الآلي: غير متصلين في هذه النسخة.")
                    }
                    1 -> key(active.id) { EditableDocument(active.id, "blueprint", active.blueprint, "المخطط الهندسي للمشروع", { editorDirty = it }) {
                        update(active.copy(blueprint = it))
                    } }
                    2 -> key(active.id) { EditableDocument(active.id, "roadmap", active.roadmap, "المراحل والمعالم وشروط النجاح", { editorDirty = it }) {
                        update(active.copy(roadmap = it))
                    } }
                    3 -> TaskScreen(active, ::update)
                }
            }
        }
    }
    if (warnOnExit) {
        AlertDialog(onDismissRequest = { warnOnExit = false }, title = { Text("تعديلات غير محفوظة") },
            text = { Text("لم يُؤكد حفظ التعديلات. ابق في المحرر واضغط حفظ ثم حاول الانتقال مجددًا.") },
            confirmButton = { TextButton(onClick = { warnOnExit = false }) { Text("العودة إلى المحرر") } })
    }
    if (showNew) {
        var name by remember { mutableStateOf("") }
        var details by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showNew = false },
            title = { Text("إنشاء مشروع") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم المشروع") })
                OutlinedTextField(details, { details = it }, label = { Text("وصف مختصر") })
            } },
            confirmButton = { TextButton(onClick = {
                if (name.isNotBlank()) {
                    val p = ProjectItem(UUID.randomUUID().toString(), name.trim(), details.trim())
                    projects.add(p); storage.save(projects.toList()); activeId = p.id
                    showNew = false
                }
            }, enabled = name.isNotBlank()) { Text("إنشاء") } },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("إلغاء") } })
    }
}

@Composable
private fun EditableDocument(
    projectId: String, document: String, value: String, hint: String,
    onDirtyChange: (Boolean) -> Unit, save: (String) -> Unit
) {
    val context = LocalContext.current
    val draftStore = remember(context) { DraftStorage(File(context.filesDir, "recovery-drafts")) }
    var recoveryError by remember(projectId, document) { mutableStateOf(false) }
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
    SideEffect { onDirtyChange(draft != value || saveError || recoveryError || conflictingDraft != null) }

    LaunchedEffect(draft, value) {
        if (conflictingDraft != null) return@LaunchedEffect
        if (draft != value) {
            // A draft is written to a separate atomic file before touching projects.json.
            try {
                draftStore.save(projectId, document, value, draft)
                recoveryError = false
            } catch (_: Exception) {
                recoveryError = true
                return@LaunchedEffect
            }
            delay(700)
            try {
                latestSave(draft)
                saveError = false
                // Failure to delete a now-redundant recovery file must not reclassify successful persistence.
                runCatching { draftStore.discard(projectId, document) }
            } catch (_: Exception) {
                saveError = true
            }
        } else if (!recoveryError && recovered?.baseText == value && recovered.text == value) {
            runCatching { draftStore.discard(projectId, document) }
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
            }, enabled = conflictingDraft == null && (draft != value || saveError)) { Text("حفظ") }
        }
        Text(
            when {
                recoveryError -> "تعذّر حفظ أو قراءة المسودة الاحتياطية — لا تغادر المحرر"
                conflictingDraft != null -> "تعارض: توجد مسودة سابقة مختلفة عن النص الحالي؛ راجعها قبل اعتمادها"
                saveError -> "فشل حفظ المشروع — المسودة الاحتياطية محفوظة إن أمكن"
                draft != value -> "تعديلات قيد الحفظ..."
                else -> "تم الحفظ"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (saveError || recoveryError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("محتوى المستند") },
            placeholder = { Text("اكتب أو الصق الخطة هنا...") }
        )
    }
}

@Composable
private fun TaskScreen(project: ProjectItem, update: (ProjectItem) -> Unit) {
    var newTask by remember(project.id) { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(newTask, { newTask = it },
            label = { Text("مهمة جديدة") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            update(project.copy(tasks = project.tasks + TaskItem(UUID.randomUUID().toString(), newTask.trim())))
            newTask = ""
        }, enabled = newTask.isNotBlank()) { Text("إضافة") }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(project.tasks, key = { it.id }) { task ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(task.title, fontWeight = FontWeight.Bold)
                        Text(task.status, style = MaterialTheme.typography.bodySmall)
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
                        }
                    }
                }
            }
        }
    }
}
