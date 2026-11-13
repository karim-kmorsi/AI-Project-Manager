package com.karim.aiprojectmanager

internal enum class ThemeMode(val storedValue: String, val label: String) {
    LIGHT("LIGHT", "Light"), DARK("DARK", "Dark");

    fun isDark(): Boolean = this == DARK

    companion object {
        const val LEGACY_SYSTEM_VALUE = "SYSTEM"

        fun decode(value: String?): ThemeMode = entries.firstOrNull { it.storedValue == value } ?: DARK
    }
}
