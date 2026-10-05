package me.mudkip.moememos.ui.component

import android.text.TextUtils
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skydoves.sandwich.ApiResponse
import me.mudkip.moememos.data.model.Memo
import me.mudkip.moememos.data.model.MemoComment
import me.mudkip.moememos.data.model.MemoReaction
import me.mudkip.moememos.data.model.MemoSocialSnapshot
import me.mudkip.moememos.ext.icon
import me.mudkip.moememos.ext.titleResource
import me.mudkip.moememos.viewmodel.LocalUserState

@Composable
fun ExploreMemoCard(
    memo: Memo,
    socialEnabled: Boolean = false,
    onLoadSocial: (suspend (String) -> ApiResponse<MemoSocialSnapshot>)? = null,
    onComment: (suspend (String, String) -> ApiResponse<MemoComment>)? = null,
    onToggleReaction: (suspend (String, String) -> ApiResponse<List<MemoReaction>>)? = null,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .fillMaxWidth()
            .testTag("space_explore_memo_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val creatorName = memo.creator?.name.orEmpty()
                SpaceAvatar(
                    name = creatorName,
                    avatarUrl = memo.creator?.avatarUrl,
                    host = LocalUserState.current.host,
                    size = 42.dp,
                    borderWidth = 1.5.dp,
                )
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        if (!TextUtils.isEmpty(creatorName)) creatorName else "Memos",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        DateUtils.getRelativeTimeSpanString(memo.date.toEpochMilli(), System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    memo.visibility.icon,
                    contentDescription = stringResource(memo.visibility.titleResource),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("space_explore_memo_content"),
            ) {
                MemoContent(memo, previewMode = false)
            }

            if (
                socialEnabled &&
                onLoadSocial != null &&
                onComment != null &&
                onToggleReaction != null
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                SpaceMemoSocialSection(
                    memoIdentifier = memo.remoteId,
                    onLoad = { onLoadSocial(memo.remoteId) },
                    onComment = { content -> onComment(memo.remoteId, content) },
                    onToggleReaction = { type -> onToggleReaction(memo.remoteId, type) },
                )
            }
        }
    }
}
