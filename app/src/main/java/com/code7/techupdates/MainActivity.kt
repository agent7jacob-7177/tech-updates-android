package com.code7.techupdates

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.code7.techupdates.domain.DeepLink
import com.code7.techupdates.domain.ThemeMode
import com.code7.techupdates.ui.AppRoot
import com.code7.techupdates.ui.theme.Code7Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {
    private data class Appearance(
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
    )

    /** Story id from a web reader link, waiting to be opened by [AppRoot]. */
    private val pendingStoryId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // On recreation the back stack already holds the deep-linked reader.
        if (savedInstanceState == null) pendingStoryId.value = DeepLink.storyId(intent?.dataString)

        val settings = (application as TechUpdatesApp).container.settings
        val appearance =
            combine(settings.themeMode, settings.dynamicColor, ::Appearance)
                .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        // Keep the splash up until the stored theme is known, so the app never flashes the wrong one.
        splash.setKeepOnScreenCondition { appearance.value == null }

        setContent {
            val loaded by appearance.collectAsStateWithLifecycle()
            val current = loaded ?: return@setContent
            val darkTheme = current.themeMode.isDark(isSystemInDarkTheme())
            DisposableEffect(darkTheme) {
                // System bar icons follow the app theme, not just the system one.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                )
                onDispose {}
            }
            val storyId by pendingStoryId.collectAsStateWithLifecycle()
            Code7Theme(
                darkTheme = darkTheme,
                dynamicColor = current.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            ) {
                AppRoot(deepLinkStoryId = storyId, onDeepLinkHandled = { pendingStoryId.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLink.storyId(intent.dataString)?.let { pendingStoryId.value = it }
    }

    private companion object {
        /** Default `enableEdgeToEdge` navigation bar scrims (3-button navigation). */
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
