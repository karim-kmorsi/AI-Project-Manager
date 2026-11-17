package com.karim.aiprojectmanager

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun missingOrUnknownPreferenceFollowsDevice() {
        listOf(null, "", "fr", "SYSTEM", " ar ").forEach {
            assertEquals(AppLanguage.SYSTEM, AppLanguage.decode(it))
        }
    }

    @Test fun explicitArabicAndEnglishValuesRoundTrip() {
        assertEquals(AppLanguage.ARABIC, AppLanguage.decode(AppLanguage.ARABIC.tag))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.decode(AppLanguage.ENGLISH.tag))
        assertEquals("ar", AppLanguage.ARABIC.locale().language)
        assertEquals("en", AppLanguage.ENGLISH.locale().language)
    }

    @Test fun systemValueIsStableAndResolvesArabicPrimaryLanguage() {
        assertEquals("system", AppLanguage.SYSTEM.tag)
        assertEquals(AppLanguage.SYSTEM, AppLanguage.decode("system"))
        listOf("ar", "AR").forEach {
            assertEquals(AppLanguage.ARABIC, AppLanguage.SYSTEM.resolve(it))
            assertEquals("ar", AppLanguage.SYSTEM.locale(it).language)
        }
    }

    @Test fun systemUsesEnglishForEnglishAndUnsupportedPrimaryLanguages() {
        listOf("en", "fr", "fa", "ur", "").forEach {
            assertEquals(AppLanguage.ENGLISH, AppLanguage.SYSTEM.resolve(it))
            assertEquals("en", AppLanguage.SYSTEM.locale(it).language)
        }
    }

    @Test fun explicitSelectionOverridesDeviceLanguage() {
        listOf("ar", "en", "fr").forEach {
            assertEquals(AppLanguage.ARABIC, AppLanguage.ARABIC.resolve(it))
            assertEquals(AppLanguage.ENGLISH, AppLanguage.ENGLISH.resolve(it))
            assertEquals("ar", AppLanguage.ARABIC.locale(it).language)
            assertEquals("en", AppLanguage.ENGLISH.locale(it).language)
        }
    }
}
