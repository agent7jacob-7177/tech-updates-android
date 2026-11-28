package com.code7.techupdates.data

import com.code7.techupdates.domain.Story
import com.code7.techupdates.recommender.Event
import com.code7.techupdates.recommender.Item
import com.code7.techupdates.recommender.Seen
import com.code7.techupdates.recommender.Signal

/** Maps [LearnerStore] types onto the pure `recommender` inputs. */
fun Story.toItem() = Item(id, title, url)

fun LearnerEvent.toEvent() =
    Event(
        kind =
            when (kind) {
                SignalKind.OPEN -> Signal.OPEN
                SignalKind.SAVE -> Signal.SAVE
                SignalKind.READ -> Signal.READ
            },
        item = story.toItem(),
        at = at,
    )

fun Impression.toSeen() = Seen(story.toItem(), count, at)
