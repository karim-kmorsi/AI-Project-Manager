package com.karim.aiprojectmanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubConnectionSettingsTest {
    @Test fun unexpectedFailureDisplaysRetryMessage() = runBlocking {
        assertEquals(
            "حدث خطأ غير متوقع أثناء اختبار اتصال GitHub. حاول مرة أخرى.",
            checkGitHubConnection { throw IllegalStateException("Unexpected failure") }
        )
    }

    @Test fun cancellationPropagates() {
        val cancelled = CancellationException("Settings closed")
        val actual = assertThrows(CancellationException::class.java) {
            runBlocking { checkGitHubConnection { throw cancelled } }
        }
        assertSame(cancelled, actual)
    }

    @Test fun preservesExpectedErrorMessages() = runBlocking {
        val errors = listOf(
            GitHubResult.HttpError(429) to "أعاد GitHub خطأ HTTP (429).",
            GitHubResult.NetworkError to "تعذّر الاتصال بواجهة GitHub العامة. تحقق من اتصال الشبكة.",
            GitHubResult.Timeout to "انتهت مهلة الاتصال بواجهة GitHub العامة.",
            GitHubResult.InvalidResponse to "أعاد GitHub استجابة غير صالحة."
        )
        for ((result, message) in errors) {
            assertEquals(message, checkGitHubConnection { result })
        }
    }
}
