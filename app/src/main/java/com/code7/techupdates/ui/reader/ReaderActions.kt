package com.code7.techupdates.ui.reader

/** User actions of the reader screen. */
data class ReaderActions(
    val onBack: () -> Unit = {},
    val onToggleLater: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onOpenInBrowser: () -> Unit = {},
    val onContinueOnSite: () -> Unit = {},
    val onOpenComments: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onReachedEnd: () -> Unit = {},
)
