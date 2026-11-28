package com.code7.techupdates.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Colour for "hot" point counts (web `.meta .hot`), picked to contrast with the current surface. */
val ColorScheme.hot: Color get() = if (surface.luminance() < 0.5f) HotDark else HotLight
