package com.code7.techupdates.ui.library

import com.code7.techupdates.domain.Story

/** User actions on the library screen. */
data class LibraryActions(
    val onSelectTab: (LibraryTab) -> Unit = {},
    val onOpen: (Story) -> Unit = {},
    val onOpenComments: (Story) -> Unit = {},
    val onToggleLater: (Story) -> Unit = {},
    val onMarkRead: (Story) -> Unit = {},
    val onRemove: (Story) -> Unit = {},
    val onRequestClear: () -> Unit = {},
    val onConfirmClear: () -> Unit = {},
    val onDismissClear: () -> Unit = {},
)
