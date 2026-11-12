package com.karim.aiprojectmanager

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The two explicit UI languages offered by Settings. */
internal enum class AppLanguage(val tag: String) {
    ARABIC("ar"), ENGLISH("en");

    fun locale(): Locale = Locale.forLanguageTag(tag)

    companion object {
        fun decode(value: String?): AppLanguage = entries.firstOrNull { it.tag == value } ?: ENGLISH
    }
}

/** Persists only the UI language; it is independent of projects and device locale. */
internal class AppLanguagePreferences(private val context: Context) {
    fun current(): AppLanguage = AppLanguage.decode(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
    )

    fun select(language: AppLanguage): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .edit().putString(KEY, language.tag).commit()

    companion object {
        private const val FILE = "ui_preferences"
        private const val KEY = "language"
    }
}

internal fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = language.locale()
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}
