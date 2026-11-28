package com.code7.techupdates.ui.settings

import com.code7.techupdates.data.local.AppearanceSettings
import com.code7.techupdates.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppearance(
    theme: ThemeMode = ThemeMode.SYSTEM,
    dynamic: Boolean = true,
) : AppearanceSettings {
    override val themeMode = MutableStateFlow(theme)
    override val dynamicColor = MutableStateFlow(dynamic)

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dynamicColor.value = enabled
    }
}
