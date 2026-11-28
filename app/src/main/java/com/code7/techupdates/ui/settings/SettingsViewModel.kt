package com.code7.techupdates.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.code7.techupdates.BuildConfig
import com.code7.techupdates.TechUpdatesApp
import com.code7.techupdates.data.LearnerStore
import com.code7.techupdates.data.local.AppearanceSettings
import com.code7.techupdates.domain.ThemeMode
import com.code7.techupdates.versionLabel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings / About: theme, dynamic colour, and resetting the "For you" learner (JS `Learner.reset`). */
class SettingsViewModel(
    private val settings: AppearanceSettings,
    private val learner: LearnerStore,
    private val dynamicColorSupported: Boolean,
    private val versionLabel: String,
) : ViewModel() {
    private val confirmingReset = MutableStateFlow(false)
    private val events = Channel<SettingsEvent>(Channel.BUFFERED)

    val uiEvents: Flow<SettingsEvent> = events.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.themeMode, settings.dynamicColor, confirmingReset) { theme, dynamic, confirming ->
            SettingsUiState(
                isLoading = false,
                themeMode = theme,
                dynamicColor = dynamic,
                dynamicColorSupported = dynamicColorSupported,
                confirmingReset = confirming,
                versionLabel = versionLabel,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            SettingsUiState(dynamicColorSupported = dynamicColorSupported, versionLabel = versionLabel),
        )

    fun selectTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        if (!dynamicColorSupported) return
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    /** "Reset For-you learning": asks for confirmation first. */
    fun requestReset() {
        confirmingReset.value = true
    }

    fun dismissReset() {
        confirmingReset.value = false
    }

    fun confirmReset() {
        confirmingReset.value = false
        viewModelScope.launch {
            learner.reset()
            events.send(SettingsEvent.LearningReset)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as TechUpdatesApp).container
                    SettingsViewModel(
                        settings = container.settings,
                        learner = container.learnerStore,
                        dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        versionLabel = versionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    )
                }
            }
    }
}
