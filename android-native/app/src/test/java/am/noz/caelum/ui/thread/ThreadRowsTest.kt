package am.noz.caelum.ui.thread

import am.noz.caelum.core.model.ThreadNode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadRowsTest {
    @Test
    fun `continuations for nested replies do not stack between posts`() {
        val leaf = ThreadNode("leaf", moreReplies = 1)
        val middle = ThreadNode("middle", moreReplies = 2, children = listOf(leaf))
        val branch = ThreadNode("branch", moreReplies = 3, children = listOf(middle))
        val sibling = ThreadNode("sibling", moreReplies = 1)
        val anchor = ThreadNode("anchor", children = listOf(branch, sibling))

        val rows = anchor.replyRows()
        assertEquals(listOf(3, 5), rows.indices.filter { rows[it].continuation })
        assertEquals(listOf("leaf", "middle", "branch"), rows[3].branches.map { it.uri })
        assertEquals(listOf("sibling"), rows[5].branches.map { it.uri })
    }
}
