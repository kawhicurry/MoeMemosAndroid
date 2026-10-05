package me.mudkip.moememos.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpaceHomeComponentsTest {
    @Test
    fun resolvesRelativeAndAbsoluteAvatarUrls() {
        assertEquals(
            "https://memos.example.com/file/users/alice/avatar",
            resolveAvatarUrl("https://memos.example.com/memos", "/file/users/alice/avatar"),
        )
        assertEquals(
            "https://cdn.example.com/alice.png",
            resolveAvatarUrl("https://memos.example.com", "https://cdn.example.com/alice.png"),
        )
        assertNull(resolveAvatarUrl("https://memos.example.com", "  "))
    }
}
