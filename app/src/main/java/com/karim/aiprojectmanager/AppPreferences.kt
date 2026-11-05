package com.karim.aiprojectmanager

import android.content.Context
import android.content.SharedPreferences

/** Separate from project JSON. This batch defines no preference keys or options. */
internal interface AppPreferences {
    /** Missing, unreadable or incorrectly typed entries use the caller's default. */
    fun readString(key: String, defaultValue: String): String
    /** True only when the platform confirms persistence. Call from a background dispatcher. */
    fun writeString(key: String, value: String): Boolean
}

/** Not instantiated by the informational Settings screen; there is nothing to save yet. */
internal class AndroidAppPreferences(private val preferences: SharedPreferences) : AppPreferences {
    override fun readString(key: String, defaultValue: String): String {
        require(key.isNotBlank())
        return try { preferences.getString(key, defaultValue) ?: defaultValue }
        catch (_: RuntimeException) { defaultValue }
    }

    override fun writeString(key: String, value: String): Boolean {
        require(key.isNotBlank())
        return try { preferences.edit().putString(key, value).commit() }
        catch (_: RuntimeException) { false }
    }

    companion object {
        fun open(context: Context): AppPreferences = AndroidAppPreferences(
            context.applicationContext.getSharedPreferences("application_preferences", Context.MODE_PRIVATE)
        )
    }
}
