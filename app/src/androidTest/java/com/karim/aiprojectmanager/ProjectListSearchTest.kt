package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectListSearchTest {
    private val projects = listOf(
        ProjectItem("1", "Alpha App", "Offline notes"),
        ProjectItem("2", "Beta", "Finance tracker"),
        ProjectItem("3", "أداة", "تطبيق عربي")
    )

    @Test fun blankQueryKeepsAllProjectsInOrder() {
        assertEquals(projects, filterProjectsByQuery(projects, "  "))
    }

    @Test fun matchesNameOrDescriptionIgnoringCaseAndOuterSpaces() {
        assertEquals(listOf(projects[0]), filterProjectsByQuery(projects, "  ALPHA  "))
        assertEquals(listOf(projects[1]), filterProjectsByQuery(projects, "finance"))
        assertEquals(listOf(projects[2]), filterProjectsByQuery(projects, "عربي"))
    }

    @Test fun noMatchDoesNotMutateSavedProjects() {
        val original = projects.toList()
        assertEquals(emptyList<ProjectItem>(), filterProjectsByQuery(projects, "missing"))
        assertEquals(original, projects)
    }
}
