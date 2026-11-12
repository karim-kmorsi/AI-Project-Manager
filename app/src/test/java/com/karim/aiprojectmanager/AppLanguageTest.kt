package com.karim.aiprojectmanager

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun missingOrUnknownPreferenceDefaultsToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.decode(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.decode(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.decode("fr"))
    }

    @Test fun explicitArabicAndEnglishValuesRoundTrip() {
        assertEquals(AppLanguage.ARABIC, AppLanguage.decode(AppLanguage.ARABIC.tag))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.decode(AppLanguage.ENGLISH.tag))
        assertEquals("ar", AppLanguage.ARABIC.locale().language)
        assertEquals("en", AppLanguage.ENGLISH.locale().language)
    }
}
