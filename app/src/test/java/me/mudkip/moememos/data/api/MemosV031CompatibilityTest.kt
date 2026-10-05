package me.mudkip.moememos.data.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.mudkip.moememos.data.constant.MemosVersionSupport
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.repository.WORKSPACE_MEMO_FILTER
import net.swiftzer.semver.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemosV031CompatibilityTest {
    private val networkJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun supportedRangeIncludesMemos031() {
        val supported = SemVer.parseOrNull("0.31.0") ?: error("invalid test version")
        val newer = SemVer.parseOrNull("0.32.0") ?: error("invalid test version")

        assertEquals("0.31.0", MemosVersionSupport.MEMOS_V1_MAX_VERSION_NAME)
        assertTrue(supported <= MemosVersionSupport.MEMOS_V1_MAX_VERSION)
        assertTrue(newer > MemosVersionSupport.MEMOS_V1_MAX_VERSION)
    }

    @Test
    fun spaceVisibilityRoundTripsThroughApiModel() {
        val encoded = networkJson.encodeToString(MemosVisibility.SPACE)
        val decoded = networkJson.decodeFromString<MemosVisibility>(encoded)

        assertEquals("\"SPACE\"", encoded)
        assertEquals(MemosVisibility.SPACE, decoded)
        assertEquals(MemoVisibility.SPACE, decoded.toMemoVisibility())
        assertEquals(MemosVisibility.SPACE, MemosVisibility.fromMemoVisibility(MemoVisibility.SPACE))
    }

    @Test
    fun decodesMemos031SpacePlacementAndFutureFields() {
        val memo = networkJson.decodeFromString<MemosV1Memo>(
            """
            {
              "name": "memos/space-note",
              "creator": "users/alice",
              "space": "spaces/team",
              "visibility": "SPACE",
              "content": "team update",
              "parent": "memos/root",
              "reactions": [{"name":"memos/space-note/reactions/1","reactionType":"👍"}],
              "unknown031Field": {"enabled": true}
            }
            """.trimIndent()
        )

        assertEquals("spaces/team", memo.space)
        assertEquals(MemosVisibility.SPACE, memo.visibility)
        assertEquals("memos/root", memo.parent)
        assertEquals("👍", memo.reactions?.single()?.reactionType)
    }

    @Test
    fun workspaceFeedIncludesSpaceAudience() {
        assertEquals(
            "visibility in [\"PUBLIC\", \"PROTECTED\", \"SPACE\"]",
            WORKSPACE_MEMO_FILTER,
        )
    }
}
