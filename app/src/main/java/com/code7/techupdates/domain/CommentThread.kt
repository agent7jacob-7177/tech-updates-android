package com.code7.techupdates.domain

/**
 * One comment of a flattened thread. [depth] is 0 for top-level comments. [replyCount] is what a
 * collapsed node reports as "N replies": its shown direct replies plus their comments (JS
 * `rest.reduce((s, k) => s + 1 + countDesc(k), 0)`).
 */
data class ThreadNode(
    val id: Long,
    val author: String?,
    /** HN HTML, or null for a deleted comment that is kept because it has replies. */
    val html: String?,
    val createdAtEpochSec: Long,
    val depth: Int,
    val replyCount: Int,
) {
    val hasReplies: Boolean get() = replyCount > 0
}

/**
 * A comment tree flattened in display order (port of JS `renderComments` / `commentNode`), ready
 * for a lazy list. Comments with no text and no replies are dropped; siblings are ordered most
 * active first (by `countDesc`). Collapsing a node hides its whole subtree; [visible] skips hidden
 * subtrees in O(visible rows) using each node's precomputed subtree end.
 */
class CommentThread internal constructor(
    val nodes: List<ThreadNode>,
    private val subtreeEnd: IntArray,
    /** Comments with text in the whole thread (JS `countDesc(item)`). */
    val total: Int,
) {
    val isEmpty: Boolean get() = total == 0

    /** The rows to show when the nodes in [collapsed] have their replies hidden. */
    fun visible(collapsed: Set<Long>): List<ThreadNode> {
        val out = ArrayList<ThreadNode>(nodes.size)
        var i = 0
        while (i < nodes.size) {
            val node = nodes[i]
            out += node
            i = if (node.id in collapsed) subtreeEnd[i] else i + 1
        }
        return out
    }

    /**
     * Initially collapsed nodes: replies below the first level, like the web app which shows the
     * direct replies of a top-level comment and hides deeper threads behind "Show N replies".
     */
    fun defaultCollapsed(): Set<Long> = nodes.filter { it.depth >= 1 && it.hasReplies }.mapTo(HashSet()) { it.id }

    /** The ids of nodes that can be collapsed. */
    val collapsibleIds: Set<Long> by lazy { nodes.filter { it.hasReplies }.mapTo(HashSet()) { it.id } }

    companion object {
        val EMPTY = of(emptyList())

        /** Flattens the top-level [comments] of a story (with their nested replies). */
        fun of(comments: List<Comment>): CommentThread = Flattener().run(comments)

        /** Descendants that have text (JS `countDesc`). */
        fun countDescendants(c: Comment): Int =
            c.children.sumOf { (if (it.text.isNullOrEmpty()) 0 else 1) + countDescendants(it) }
    }
}

/** Builds a [CommentThread]; descendant counts are memoised so big threads stay linear. */
private class Flattener {
    private val nodes = ArrayList<ThreadNode>()
    private val ends = ArrayList<Int>()
    private val counts = java.util.IdentityHashMap<Comment, Int>()

    fun run(comments: List<Comment>): CommentThread {
        shown(comments).forEach { visit(it, 0) }
        val total = comments.sumOf { selfCount(it) + count(it) }
        return CommentThread(nodes, ends.toIntArray(), total)
    }

    private fun visit(
        c: Comment,
        depth: Int,
    ) {
        val kids = shown(c.children)
        val index = nodes.size
        nodes +=
            ThreadNode(
                id = c.id,
                author = c.author?.takeIf { it.isNotEmpty() },
                html = c.text?.takeIf { it.isNotEmpty() },
                createdAtEpochSec = c.createdAtEpochSec,
                depth = depth,
                replyCount = kids.sumOf { 1 + count(it) },
            )
        ends += 0
        kids.forEach { visit(it, depth + 1) }
        ends[index] = nodes.size
    }

    private fun selfCount(c: Comment) = if (c.text.isNullOrEmpty()) 0 else 1

    private fun count(c: Comment): Int = counts.getOrPut(c) { c.children.sumOf { selfCount(it) + count(it) } }

    private fun shown(comments: List<Comment>): List<Comment> =
        comments
            .filter { !it.text.isNullOrEmpty() || it.children.isNotEmpty() }
            .sortedByDescending { count(it) }
}
