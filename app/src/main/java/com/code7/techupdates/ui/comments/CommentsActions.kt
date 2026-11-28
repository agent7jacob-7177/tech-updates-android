package com.code7.techupdates.ui.comments

/** User actions of the comments screen. */
data class CommentsActions(
    val onBack: () -> Unit = {},
    val onOpenOnHn: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onToggle: (Long) -> Unit = {},
)
