package me.mudkip.moememos.ui.component

import me.mudkip.moememos.data.model.MemoReaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SpaceMemoSocialTest {
    @Test
    fun reactionCountsUseStableCommonOrderThenCustomTypes() {
        val reactions = listOf(
            reaction("custom"),
            reaction("❤️"),
            reaction("👍"),
            reaction("❤️"),
            reaction("custom"),
        )

        assertEquals(
            listOf("👍" to 1, "❤️" to 2, "custom" to 2),
            reactionCounts(reactions),
        )
    }

    private fun reaction(type: String) = MemoReaction(
        remoteId = "reactions/$type",
        creatorId = "users/test",
        reactionType = type,
        date = Instant.EPOCH,
        mine = false,
    )
}
