package me.mudkip.moememos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.skydoves.sandwich.ApiResponse
import me.mudkip.moememos.data.model.MemoComment
import me.mudkip.moememos.data.model.MemoReaction
import me.mudkip.moememos.data.model.MemoSocialSnapshot
import me.mudkip.moememos.data.model.User
import me.mudkip.moememos.ui.component.SpaceMemoSocialSection
import me.mudkip.moememos.ui.theme.MoeMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SpaceMemoSocialTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactSocialPanelLoadsTogglesAndPostsRealCallbacks() {
        val author = User(identifier = "users/alice", name = "Alice")
        val posted = mutableListOf<String>()
        val toggled = mutableListOf<String>()
        var reactions = listOf(reaction("👍", mine = false))
        val initialComments = listOf(
            comment("one", author, 1),
            comment("two", author, 2),
            comment("three", author, 3),
        )

        compose.setContent {
            MoeMemosTheme(dynamicColor = false) {
                Box(Modifier.width(280.dp).testTag("compact_social_host")) {
                    SpaceMemoSocialSection(
                        memoIdentifier = "memo-one",
                        onLoad = {
                            ApiResponse.Success(MemoSocialSnapshot(initialComments, reactions))
                        },
                        onComment = { content ->
                            posted += content
                            ApiResponse.Success(comment(content, author, 4))
                        },
                        onToggleReaction = { type ->
                            toggled += type
                            reactions = listOf(reaction(type, mine = true))
                            ApiResponse.Success(reactions)
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag("compact_social_host").assertIsDisplayed()
        compose.onNodeWithTag("space_memo_comment").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasTestTag("space_memo_comments")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("space_memo_reaction_summary").assertIsDisplayed()
        compose.onNodeWithTag("space_memo_comments").assertIsDisplayed()
        compose.onNodeWithTag("space_memo_reaction_picker").assertIsDisplayed()
        compose.onNodeWithTag("space_memo_reaction_choice_❤️").performClick()
        compose.onNodeWithTag("space_memo_comment_input").performTextInput("new reply")
        compose.onNodeWithTag("space_memo_comment_send").performClick()

        compose.waitUntil(5_000) { posted.isNotEmpty() && toggled.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(listOf("❤️"), toggled)
            assertEquals(listOf("new reply"), posted)
        }
    }

    @Test
    fun collapsedPanelDoesNotLoadSocialDetails() {
        var loadCalls = 0

        compose.setContent {
            MoeMemosTheme(dynamicColor = false) {
                SpaceMemoSocialSection(
                    memoIdentifier = "collapsed-memo",
                    onLoad = {
                        loadCalls += 1
                        ApiResponse.Success(MemoSocialSnapshot(emptyList(), emptyList()))
                    },
                    onComment = { error("Collapsed panel must not post a comment") },
                    onToggleReaction = { error("Collapsed panel must not toggle a reaction") },
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("space_memo_social").assertIsDisplayed()
        compose.onNodeWithTag("space_memo_reaction_picker").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, loadCalls) }
    }

    @Test
    fun restoredExpandedPanelReloadsMissingDetailsExactlyOnce() {
        val restoration = StateRestorationTester(compose)
        val author = User(identifier = "users/alice", name = "Alice")
        val snapshot = MemoSocialSnapshot(
            comments = listOf(comment("restored comment", author, 5)),
            reactions = listOf(reaction("👍", mine = true)),
        )
        var loadCalls = 0

        restoration.setContent {
            MoeMemosTheme(dynamicColor = false) {
                SpaceMemoSocialSection(
                    memoIdentifier = "restored-memo",
                    onLoad = {
                        loadCalls += 1
                        ApiResponse.Success(snapshot)
                    },
                    onComment = { error("This test does not post comments") },
                    onToggleReaction = { error("This test does not toggle reactions") },
                )
            }
        }

        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, loadCalls) }
        compose.onNodeWithTag("space_memo_comment").performClick()
        compose.waitUntil(5_000) { loadCalls == 1 }
        compose.onNodeWithTag("space_memo_reaction_picker").assertIsDisplayed()
        compose.onNodeWithTag("space_memo_comments").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.waitUntil(5_000) { loadCalls == 2 }
        compose.waitForIdle()
        compose.onNodeWithTag("space_memo_reaction_picker").assertIsDisplayed()
        compose.onNodeWithTag("space_memo_comments").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                "The restored expanded panel must issue one, and only one, replacement load",
                2,
                loadCalls,
            )
        }
    }

    private fun comment(content: String, author: User, second: Long) = MemoComment(
        remoteId = "memos/comment-$second",
        content = content,
        creator = author,
        date = Instant.EPOCH.plusSeconds(second),
    )

    private fun reaction(type: String, mine: Boolean) = MemoReaction(
        remoteId = "memos/memo-one/reactions/1",
        creatorId = if (mine) "users/me" else "users/other",
        reactionType = type,
        date = Instant.EPOCH,
        mine = mine,
    )
}
