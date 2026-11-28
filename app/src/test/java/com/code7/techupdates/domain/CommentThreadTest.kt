package com.code7.techupdates.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentThreadTest {
    private fun c(
        id: Long,
        text: String? = "c$id",
        vararg kids: Comment,
    ) = Comment(id, author = "u$id", text = text, createdAtEpochSec = 100 + id, children = kids.toList())

    // 1 (2 replies: 2 -> 3), 4 (no replies), 5 deleted w/o replies (dropped), 6 deleted with a reply 7
    private val tree =
        listOf(
            c(4),
            c(1, "one", c(2, "two", c(3))),
            c(5, null),
            c(6, null, c(7)),
        )

    private fun ids(nodes: List<ThreadNode>) = nodes.map { it.id }

    @Test
    fun flatten_preOrderMostActiveFirst_dropsEmptyLeaves() {
        val thread = CommentThread.of(tree)
        assertEquals(listOf(1L, 2L, 3L, 6L, 7L, 4L), ids(thread.nodes))
        assertEquals(listOf(0, 1, 2, 0, 1, 0), thread.nodes.map { it.depth })
    }

    @Test
    fun total_countsCommentsWithText() {
        assertEquals(5, CommentThread.of(tree).total) // 1,2,3,4,7
        assertTrue(CommentThread.of(listOf(c(1, null))).isEmpty)
        assertTrue(CommentThread.EMPTY.isEmpty)
    }

    @Test
    fun deletedWithReplies_isKeptWithoutText() {
        val node = CommentThread.of(tree).nodes.first { it.id == 6L }
        assertEquals(null, node.html)
        assertEquals(1, node.replyCount)
    }

    @Test
    fun replyCount_countsDirectRepliesAndTheirComments() {
        val nodes = CommentThread.of(tree).nodes.associateBy { it.id }
        assertEquals(2, nodes.getValue(1).replyCount)
        assertEquals(1, nodes.getValue(2).replyCount)
        assertEquals(0, nodes.getValue(3).replyCount)
        assertFalse(nodes.getValue(4).hasReplies)
    }

    @Test
    fun replyCount_countsDeletedRepliesThatHaveChildren() {
        // JS: rest.reduce((s, k) => s + 1 + countDesc(k)) counts the deleted reply itself
        val thread = CommentThread.of(listOf(c(1, "a", c(2, null, c(3)))))
        assertEquals(2, thread.nodes.first().replyCount)
        assertEquals(2, thread.total)
    }

    @Test
    fun visible_hidesCollapsedSubtrees() {
        val thread = CommentThread.of(tree)
        assertEquals(ids(thread.nodes), ids(thread.visible(emptySet())))
        assertEquals(listOf(1L, 6L, 7L, 4L), ids(thread.visible(setOf(1L))))
        assertEquals(listOf(1L, 2L, 6L, 4L), ids(thread.visible(setOf(2L, 6L))))
        // a collapsed node inside a collapsed subtree doesn't matter
        assertEquals(listOf(1L, 6L, 7L, 4L), ids(thread.visible(setOf(1L, 2L))))
    }

    @Test
    fun defaultCollapsed_hidesRepliesBelowFirstLevel() {
        val thread = CommentThread.of(tree)
        assertEquals(setOf(2L), thread.defaultCollapsed())
        assertEquals(listOf(1L, 2L, 6L, 7L, 4L), ids(thread.visible(thread.defaultCollapsed())))
    }

    @Test
    fun collapsibleIds_areNodesWithReplies() {
        assertEquals(setOf(1L, 2L, 6L), CommentThread.of(tree).collapsibleIds)
    }

    @Test
    fun countDescendants_matchesJs() {
        assertEquals(2, CommentThread.countDescendants(tree[1]))
        assertEquals(1, CommentThread.countDescendants(tree[3]))
    }

    @Test
    fun deepThread_flattensWithoutQuadraticBlowUp() {
        var node = c(2_000)
        for (id in 1_999L downTo 1L) node = c(id, "c$id", node)
        val thread = CommentThread.of(listOf(node))
        assertEquals(2_000, thread.nodes.size)
        assertEquals(1_999, thread.nodes.last().depth)
        assertEquals(listOf(1L), ids(thread.visible(setOf(1L))))
    }
}
