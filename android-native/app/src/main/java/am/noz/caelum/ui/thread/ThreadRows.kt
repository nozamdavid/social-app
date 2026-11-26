package am.noz.caelum.ui.thread

import am.noz.caelum.core.model.ThreadNode

internal data class ThreadRow(
    val node: ThreadNode,
    val depth: Int,
    val connectsAbove: Boolean = false,
    val branches: List<ThreadNode> = emptyList(),
) {
    val continuation: Boolean get() = branches.isNotEmpty()
    val key: String get() = if (continuation) "more_${node.uri}" else "reply_${node.uri}"
}

internal fun ThreadNode.replyRows(): List<ThreadRow> = buildList {
    fun appendChildren(parent: ThreadNode, depth: Int) {
        parent.children.forEachIndexed { index, child ->
            add(ThreadRow(child, depth, connectsAbove = depth > 0 && index == 0))
            appendChildren(child, depth + 1)
        }
        if (parent.moreReplies > 0) {
            val previous = lastOrNull()?.takeIf { it.continuation }
            if (previous == null) {
                add(ThreadRow(parent, depth, branches = listOf(parent)))
            } else {
                // Several branches can end at the same point in the flattened list.
                set(lastIndex, previous.copy(depth = depth, branches = previous.branches + parent))
            }
        }
    }
    appendChildren(this@replyRows, 0)
}

