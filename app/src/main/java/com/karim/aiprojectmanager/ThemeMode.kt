package com.karim.aiprojectmanager

internal enum class ThemeMode(val storedValue: String, val label: String) {
    SYSTEM("SYSTEM", "Follow System"), LIGHT("LIGHT", "Light"), DARK("DARK", "Dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun decode(value: String?): ThemeMode = entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}
