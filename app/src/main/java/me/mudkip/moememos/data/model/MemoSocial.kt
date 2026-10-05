package me.mudkip.moememos.data.model

import java.time.Instant

/** A Memos v1 comment represented independently from the local memo timeline. */
data class MemoComment(
    val remoteId: String,
    val content: String,
    val creator: User?,
    val date: Instant,
)

/** A server-backed reaction. [mine] is resolved against the authenticated account. */
data class MemoReaction(
    val remoteId: String,
    val creatorId: String,
    val reactionType: String,
    val date: Instant,
    val mine: Boolean,
)

data class MemoSocialSnapshot(
    val comments: List<MemoComment>,
    val reactions: List<MemoReaction>,
)
