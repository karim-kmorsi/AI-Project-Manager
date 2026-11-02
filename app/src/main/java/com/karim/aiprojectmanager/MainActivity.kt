package com.karim.aiprojectmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import android.util.AtomicFile
import java.util.UUID

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
    var activeId by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    fun update(item: ProjectItem) {
        val index = projects.indexOfFirst { it.id == item.id }
        if (index >= 0) { projects[index] = item; storage.save(projects.toList()) }
    }
    val active = projects.find { it.id == activeId }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (active == null) "إدارة المشاريع" else active.name) },
            navigationIcon = {
                if (active != null) TextButton(onClick = { activeId = null }) { Text("رجوع") }
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
                        Tab(selected = index == tab, onClick = { tab = index }, text = { Text(label) })
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
                    1 -> EditableDocument(active.blueprint, "المخطط الهندسي للمشروع") {
                        update(active.copy(blueprint = it))
                    }
                    2 -> EditableDocument(active.roadmap, "المراحل والمعالم وشروط النجاح") {
                        update(active.copy(roadmap = it))
                    }
                    3 -> TaskScreen(active, ::update)
                }
            }
        }
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
private fun EditableDocument(value: String, hint: String, save: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    Column(
        Modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(hint, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Button(onClick = { save(draft) }, enabled = draft != value) { Text("حفظ") }
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
