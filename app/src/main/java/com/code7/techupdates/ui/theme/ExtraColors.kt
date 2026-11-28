package com.code7.techupdates.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Colour for "hot" point counts (web `.meta .hot`), picked to contrast with the current surface. */
val ColorScheme.hot: Color get() = if (surface.luminance() < 0.5f) HotDark else HotLight

/** Rail colour for comment nesting [level] (1-based), cycling through a palette tuned for the surface. */
fun ColorScheme.commentRail(level: Int): Color {
    val rails = if (surface.luminance() < 0.5f) RailsDark else RailsLight
    return rails[(level - 1).mod(rails.size)]
}
