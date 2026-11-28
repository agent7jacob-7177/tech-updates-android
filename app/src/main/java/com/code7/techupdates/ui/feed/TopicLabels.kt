package com.code7.techupdates.ui.feed

import androidx.annotation.StringRes
import com.code7.techupdates.R
import com.code7.techupdates.domain.Topic

/** Chip label (JS `t.label`), localisable. */
@get:StringRes
val Topic.labelRes: Int
    get() =
        when (this) {
            Topic.ALL -> R.string.topic_all
            Topic.AI -> R.string.topic_ai
            Topic.ML -> R.string.topic_ml
            Topic.SECURITY -> R.string.topic_security
            Topic.PROGRAMMING -> R.string.topic_programming
            Topic.HARDWARE -> R.string.topic_hardware
            Topic.SPACE -> R.string.topic_space
            Topic.STARTUPS -> R.string.topic_startups
        }

/** Card tag (JS `t.tag || t.label`). */
@get:StringRes
val Topic.tagRes: Int
    get() = if (this == Topic.ML) R.string.topic_tag_ml else labelRes

@get:StringRes
val FeedTab.labelRes: Int
    get() =
        when (this) {
            FeedTab.TOP -> R.string.feed_tab_top
            FeedTab.LATEST -> R.string.feed_tab_latest
            FeedTab.WEEK -> R.string.feed_tab_this_week
            FeedTab.FOR_YOU -> R.string.feed_tab_for_you
        }
