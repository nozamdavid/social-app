package am.noz.caelum.ui.social

import am.noz.caelum.core.model.ConvoView
import am.noz.caelum.core.model.ProfileViewBasic
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationIdentityTest {
    private val me = ProfileViewBasic(did = "did:plc:me")
    private val peer = ProfileViewBasic(did = "did:plc:peer")

    @Test
    fun typedGroupUsesItsNameEvenWhenOnlyTwoMembersArePresent() {
        val group = ConvoView(
            members = listOf(me, peer),
            kind = buildJsonObject {
                put("\$type", "chat.bsky.convo.defs#groupConvo")
                put("name", "Weekend plans")
                put("lockStatus", "locked")
            },
        )

        assertTrue(group.isGroupConvo())
        assertTrue(group.isLockedGroup())
        assertEquals("Weekend plans", group.groupProperty("name"))
        assertNull(group.directPeer(me.did))
    }

    @Test
    fun legacyTypedAndUntypedConversationsKeepTheCorrectAudience() {
        val direct = ConvoView(members = listOf(me, peer))
        val legacyGroup = direct.copy(
            kind = JsonPrimitive("group"),
            details = buildJsonObject { put("name", "Family") },
        )
        val untypedGroup = direct.copy(members = direct.members + ProfileViewBasic(did = "did:plc:third"))

        assertFalse(direct.isGroupConvo())
        assertEquals(peer, direct.directPeer(me.did))
        assertTrue(legacyGroup.isGroupConvo())
        assertEquals("Family", legacyGroup.groupProperty("name"))
        assertTrue(untypedGroup.isGroupConvo())
        assertNull(untypedGroup.directPeer(me.did))
    }
}
