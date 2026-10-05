package me.mudkip.moememos.ui.component

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.MemoComment
import me.mudkip.moememos.data.model.MemoReaction
import me.mudkip.moememos.data.model.MemoSocialSnapshot
import me.mudkip.moememos.ext.getErrorMessage

internal val COMMON_MEMO_REACTIONS = listOf("👍", "❤️", "🎉", "😄", "👀", "🚀")

internal data class MemoSocialUiState(
    val loading: Boolean = false,
    val mutating: Boolean = false,
    val comments: List<MemoComment> = emptyList(),
    val reactions: List<MemoReaction> = emptyList(),
    val error: String? = null,
)

internal fun reactionCounts(reactions: List<MemoReaction>): List<Pair<String, Int>> {
    val counts = reactions.groupingBy { it.reactionType }.eachCount()
    val preferred = COMMON_MEMO_REACTIONS.mapNotNull { type ->
        counts[type]?.let { type to it }
    }
    val custom = counts
        .filterKeys { it !in COMMON_MEMO_REACTIONS }
        .toList()
        .sortedBy { it.first }
    return preferred + custom
}

@Composable
internal fun SpaceMemoSocialSection(
    memoIdentifier: String,
    onLoad: suspend () -> ApiResponse<MemoSocialSnapshot>,
    onComment: suspend (String) -> ApiResponse<MemoComment>,
    onToggleReaction: suspend (String) -> ApiResponse<List<MemoReaction>>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val unknownError = stringResource(R.string.error_unknown)
    var state by remember(memoIdentifier) { mutableStateOf(MemoSocialUiState()) }
    var expanded by rememberSaveable(memoIdentifier) { mutableStateOf(false) }
    var detailsLoaded by remember(memoIdentifier) { mutableStateOf(false) }
    var commentDraft by rememberSaveable(memoIdentifier) { mutableStateOf("") }

    fun reload() {
        scope.launch {
            state = state.copy(loading = true, error = null)
            state = when (val response = onLoad()) {
                is ApiResponse.Success -> {
                    detailsLoaded = true
                    state.copy(
                        loading = false,
                        comments = response.data.comments,
                        reactions = response.data.reactions,
                        error = null,
                    )
                }
                else -> state.copy(
                    loading = false,
                    error = response.getErrorMessage().ifBlank { unknownError },
                )
            }
        }
    }

    fun toggleReaction(type: String) {
        if (state.mutating) return
        scope.launch {
            state = state.copy(mutating = true, error = null)
            state = when (val response = onToggleReaction(type)) {
                is ApiResponse.Success -> state.copy(
                    mutating = false,
                    reactions = response.data,
                    error = null,
                )
                else -> state.copy(
                    mutating = false,
                    error = response.getErrorMessage().ifBlank { unknownError },
                )
            }
        }
    }

    LaunchedEffect(expanded, detailsLoaded) {
        if (expanded && !detailsLoaded && !state.loading) {
            reload()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("space_memo_social"),
    ) {
        if (state.loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("space_memo_social_loading"),
            )
        }

        if (state.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("space_memo_reaction_summary"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                reactionCounts(state.reactions).forEach { (type, count) ->
                    val selected = state.reactions.any { it.mine && it.reactionType == type }
                    Surface(
                        onClick = { toggleReaction(type) },
                        enabled = !state.mutating,
                        shape = RoundedCornerShape(50),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.testTag("space_memo_reaction_$type"),
                    ) {
                        Text(
                            text = "$type $count",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        if (state.comments.isNotEmpty()) {
            val visibleComments = if (expanded) state.comments else state.comments.takeLast(2)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .fillMaxWidth()
                    .testTag("space_memo_comments"),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    visibleComments.forEachIndexed { index, comment ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                        MemoCommentRow(comment)
                    }
                    if (!expanded && state.comments.size > visibleComments.size) {
                        TextButton(
                            onClick = { expanded = true },
                            modifier = Modifier.testTag("space_memo_show_all_comments"),
                        ) {
                            Text(stringResource(R.string.show_all_comments, state.comments.size))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("space_memo_social_actions"),
        ) {
            val liked = state.reactions.any { it.mine && it.reactionType == "👍" }
            TextButton(
                onClick = { toggleReaction("👍") },
                enabled = !state.loading && !state.mutating,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (liked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("space_memo_like"),
            ) {
                Icon(Icons.Outlined.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (liked) R.string.unlike else R.string.like))
            }
            TextButton(
                onClick = {
                    expanded = !expanded
                },
                enabled = !state.loading,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("space_memo_comment"),
            ) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.comments.isEmpty()) {
                        stringResource(R.string.comment)
                    } else {
                        stringResource(R.string.comment_count, state.comments.size)
                    }
                )
            }
        }

        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("space_memo_reaction_picker"),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                COMMON_MEMO_REACTIONS.forEach { type ->
                    val selected = state.reactions.any { it.mine && it.reactionType == type }
                    Surface(
                        onClick = { toggleReaction(type) },
                        enabled = !state.mutating,
                        shape = RoundedCornerShape(50),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier.testTag("space_memo_reaction_choice_$type"),
                    ) {
                        Text(
                            type,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, bottom = 7.dp)
                    .testTag("space_memo_comment_composer"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    enabled = !state.mutating,
                    placeholder = { Text(stringResource(R.string.write_a_comment)) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("space_memo_comment_input"),
                )
                IconButton(
                    enabled = commentDraft.isNotBlank() && !state.mutating,
                    onClick = {
                        val content = commentDraft.trim()
                        if (content.isEmpty()) return@IconButton
                        scope.launch {
                            state = state.copy(mutating = true, error = null)
                            when (val response = onComment(content)) {
                                is ApiResponse.Success -> {
                                    state = state.copy(
                                        mutating = false,
                                        comments = (state.comments + response.data).sortedBy { it.date },
                                        error = null,
                                    )
                                    commentDraft = ""
                                }
                                else -> {
                                    state = state.copy(
                                        mutating = false,
                                        error = response.getErrorMessage().ifBlank { unknownError },
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("space_memo_comment_send"),
                ) {
                    if (state.mutating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.send_comment),
                        )
                    }
                }
            }
        }

        state.error?.takeIf { it.isNotBlank() }?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("space_memo_social_error"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = ::reload, enabled = !state.loading) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun MemoCommentRow(comment: MemoComment) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .testTag("space_memo_comment_item"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.creator?.name ?: stringResource(R.string.unknown_user),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    comment.date.toEpochMilli(),
                    System.currentTimeMillis(),
                    DateUtils.SECOND_IN_MILLIS,
                ).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = comment.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
