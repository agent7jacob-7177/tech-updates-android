package com.code7.techupdates.domain

/** Light/dark choice from Settings. [id] is the stored value. */
enum class ThemeMode(
    val id: String,
) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    /** Whether to use the dark palette, given the system's current setting. */
    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemDark
            LIGHT -> false
            DARK -> true
        }

    companion object {
        /** Unknown or missing ids fall back to [SYSTEM]. */
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
