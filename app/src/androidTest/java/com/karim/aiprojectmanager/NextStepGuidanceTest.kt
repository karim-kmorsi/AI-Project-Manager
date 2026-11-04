package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NextStepGuidanceTest {
    private fun project(
        blueprint: String = "مخطط",
        roadmap: String = "خريطة",
        tasks: List<TaskItem> = emptyList()
    ) = ProjectItem("project-1", "مشروع", "", blueprint, roadmap, tasks)

    @Test fun missingBlueprintComesFirst() {
        assertEquals("ابدأ بكتابة Blueprint للمشروع.",
            nextStepGuidance(project(blueprint = "  ", roadmap = "")))
    }

    @Test fun missingRoadmapComesBeforeTasks() {
        assertEquals("حدد مراحل العمل في Roadmap.",
            nextStepGuidance(project(roadmap = " ")))
    }

    @Test fun emptyTaskListSuggestsAddingTask() {
        assertEquals("أضف أول مهمة قابلة للتنفيذ.", nextStepGuidance(project()))
    }

    @Test fun inProgressTasksTakePrecedence() {
        val tasks = listOf(
            TaskItem("1", "مهمة", "IMPLEMENTED_UNVERIFIED"),
            TaskItem("2", "مهمة ثانية", "IN_PROGRESS")
        )
        assertEquals("راجع المهمة قيد التنفيذ وحدد ما اكتمل منها.",
            nextStepGuidance(project(tasks = tasks)))
    }

    @Test fun implementedUnverifiedNeedsEvidence() {
        assertEquals("راجع أدلة واختبارات المهام المنفذة قبل اعتمادها.",
            nextStepGuidance(project(tasks = listOf(TaskItem("1", "مهمة", "IMPLEMENTED_UNVERIFIED")))))
    }

    @Test fun unstartedTaskIsNotVerified() {
        assertEquals("اختر مهمة لم تبدأ بعد وحدد خطوتها العملية.",
            nextStepGuidance(project(tasks = listOf(TaskItem("1", "مهمة", "NOT_STARTED")))))
    }

    @Test fun verifiedStatusDoesNotClaimIndependentValidation() {
        assertEquals("راجع أدلة تحقق المهام وحدد ما إذا كان المشروع يحتاج أعمالًا إضافية.",
            nextStepGuidance(project(tasks = listOf(TaskItem("1", "مهمة", "VERIFIED")))))
    }

    @Test fun guidanceDoesNotMutateProjectData() {
        val item = project(tasks = listOf(TaskItem("1", "مهمة", "IN_PROGRESS")))
        val snapshot = item.copy(tasks = item.tasks.toList())
        nextStepGuidance(item)
        assertEquals(snapshot, item)
    }
}
