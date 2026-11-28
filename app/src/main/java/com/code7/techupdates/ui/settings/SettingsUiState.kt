package com.code7.techupdates.ui.settings

import com.code7.techupdates.domain.ThemeMode

/** Settings / About screen. [isLoading] is true until the stored settings have been read. */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Dynamic colour needs Android 12+; below that the switch is shown disabled. */
    val dynamicColorSupported: Boolean = true,
    val confirmingReset: Boolean = false,
    val versionLabel: String = "",
)

/** One-off events (snackbars). */
sealed interface SettingsEvent {
    data object LearningReset : SettingsEvent
}

/** UI callbacks, defaulted for previews. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onSelectTheme: (ThemeMode) -> Unit = {},
    val onDynamicColorChange: (Boolean) -> Unit = {},
    val onRequestReset: () -> Unit = {},
    val onDismissReset: () -> Unit = {},
    val onConfirmReset: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
)
