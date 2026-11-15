package com.karim.aiprojectmanager

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** Stored values remain compatible with existing explicit language selections. */
internal enum class AppLanguage(val tag: String) {
    SYSTEM("system"), ARABIC("ar"), ENGLISH("en");

    fun resolve(deviceLanguage: String): AppLanguage = when (this) {
        SYSTEM -> if (deviceLanguage.equals("ar", ignoreCase = true)) ARABIC else ENGLISH
        ARABIC, ENGLISH -> this
    }

    fun locale(deviceLanguage: String = Locale.getDefault().language): Locale =
        Locale.forLanguageTag(resolve(deviceLanguage).tag)

    companion object {
        fun decode(value: String?): AppLanguage = entries.firstOrNull { it.tag == value } ?: SYSTEM
    }
}

/** Persists only the UI language; it is independent of projects and device locale. */
internal class AppLanguagePreferences(private val preferences: AppPreferences) {
    constructor(context: Context) : this(
        AndroidAppPreferences(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    )

    private var confirmed: AppLanguage? = null

    fun current(): AppLanguage = confirmed ?: AppLanguage.decode(
        preferences.readString(KEY, AppLanguage.SYSTEM.tag)
    ).also { confirmed = it }

    fun select(language: AppLanguage): Boolean {
        // SharedPreferences may change its in-memory map even if the disk commit fails.
        // Retain the confirmed selection in this Activity until persistence succeeds.
        current()
        if (!preferences.writeString(KEY, language.tag)) return false
        confirmed = language
        return true
    }

    companion object {
        private const val FILE = "ui_preferences"
        private const val KEY = "language"
    }
}

internal fun Context.withAppLanguage(language: AppLanguage): Context {
    // This context may already have the app's explicit locale. Read the device independently.
    val locale = language.locale(Resources.getSystem().configuration.locales[0].language)
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}
