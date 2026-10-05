package me.mudkip.moememos.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.mudkip.moememos.R
import me.mudkip.moememos.ui.theme.MoeMemosTheme
import me.mudkip.moememos.ui.theme.SpaceBlue
import me.mudkip.moememos.ui.theme.SpaceBlueDark
import java.net.URI

@Composable
internal fun SpaceProfileHeader(
    displayName: String,
    avatarUrl: String?,
    host: String,
    memoCount: Int,
    tagCount: Int,
    dayCount: Long,
    onCompose: () -> Unit,
    onResources: () -> Unit,
    onArchived: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("space_profile_header")
    ) {
        val compact = maxWidth < 360.dp
        val wide = maxWidth >= 700.dp
        val coverHeight = when {
            wide -> 188.dp
            compact -> 160.dp
            else -> 174.dp
        }
        val avatarSize = if (compact) 80.dp else 92.dp
        val avatarInset = if (compact) 16.dp else 24.dp
        val profileTextInset = avatarInset + avatarSize + 12.dp

        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    SpaceCover(coverHeight)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                        shadowElevation = 3.dp,
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = if (compact) 80.dp else 88.dp)
                                    .padding(
                                        start = profileTextInset,
                                        top = 14.dp,
                                        end = if (compact) 12.dp else 20.dp,
                                        bottom = 10.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = displayName.ifBlank { stringResource(R.string.my_space) },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(R.string.space_tagline),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            if (wide) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SpaceStats(
                                        memoCount = memoCount,
                                        tagCount = tagCount,
                                        dayCount = dayCount,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    SpaceQuickActions(
                                        onCompose = onCompose,
                                        onResources = onResources,
                                        onArchived = onArchived,
                                        onSearch = onSearch,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            } else {
                                SpaceStats(memoCount, tagCount, dayCount)
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                )
                                SpaceQuickActions(
                                    onCompose = onCompose,
                                    onResources = onResources,
                                    onArchived = onArchived,
                                    onSearch = onSearch,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                SpaceAvatar(
                    name = displayName,
                    avatarUrl = avatarUrl,
                    host = host,
                    size = avatarSize,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = avatarInset,
                            y = coverHeight - (avatarSize.value / 2f).dp,
                        ),
                    borderWidth = 4.dp,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 4.dp)
                    .testTag("space_activity_header"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.recent_activity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.activity_count, memoCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpaceCover(height: Dp) {
    val coverDescription = stringResource(R.string.space_cover_caption)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .testTag("space_cover")
            .semantics(mergeDescendants = true) {
                contentDescription = coverDescription
            }
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF66B6FF), SpaceBlue, SpaceBlueDark),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.14f), radius = size.minDimension * 0.34f, center = Offset(size.width * 0.86f, size.height * 0.16f))
            drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.20f, center = Offset(size.width * 0.10f, size.height * 0.82f))
            drawCircle(Color(0xFFFFD36A).copy(alpha = 0.45f), radius = size.minDimension * 0.055f, center = Offset(size.width * 0.78f, size.height * 0.33f))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 22.dp)
        ) {
            Text(
                text = "MOE SPACE",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = stringResource(R.string.space_cover_caption),
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SpaceStats(
    memoCount: Int,
    tagCount: Int,
    dayCount: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("space_stats"),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SpaceStat(
            memoCount.toString(),
            stringResource(R.string.memo),
            Modifier.weight(1f),
            "space_stat_memos",
        )
        SpaceStat(
            tagCount.toString(),
            stringResource(R.string.tag),
            Modifier.weight(1f),
            "space_stat_tags",
        )
        SpaceStat(
            dayCount.toString(),
            stringResource(R.string.day),
            Modifier.weight(1f),
            "space_stat_days",
        )
    }
}

@Composable
private fun SpaceStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    Column(
        modifier = modifier
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = "$value $label"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpaceQuickActions(
    onCompose: () -> Unit,
    onResources: () -> Unit,
    onArchived: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("space_quick_actions"),
    ) {
        if (maxWidth < 320.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceQuickAction(
                        stringResource(R.string.compose),
                        Icons.Outlined.EditNote,
                        onCompose,
                        "space_quick_action_compose",
                        Modifier.weight(1f),
                    )
                    SpaceQuickAction(
                        stringResource(R.string.resources),
                        Icons.Outlined.PhotoLibrary,
                        onResources,
                        "space_quick_action_resources",
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceQuickAction(
                        stringResource(R.string.archived),
                        Icons.Outlined.Archive,
                        onArchived,
                        "space_quick_action_archived",
                        Modifier.weight(1f),
                    )
                    SpaceQuickAction(
                        stringResource(R.string.search),
                        Icons.Outlined.Search,
                        onSearch,
                        "space_quick_action_search",
                        Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpaceQuickAction(
                    stringResource(R.string.compose),
                    Icons.Outlined.EditNote,
                    onCompose,
                    "space_quick_action_compose",
                    Modifier.weight(1f),
                )
                SpaceQuickAction(
                    stringResource(R.string.resources),
                    Icons.Outlined.PhotoLibrary,
                    onResources,
                    "space_quick_action_resources",
                    Modifier.weight(1f),
                )
                SpaceQuickAction(
                    stringResource(R.string.archived),
                    Icons.Outlined.Archive,
                    onArchived,
                    "space_quick_action_archived",
                    Modifier.weight(1f),
                )
                SpaceQuickAction(
                    stringResource(R.string.search),
                    Icons.Outlined.Search,
                    onSearch,
                    "space_quick_action_search",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SpaceQuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 68.dp)
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = label
            },
        color = SpaceBlue.copy(alpha = 0.09f),
        contentColor = SpaceBlueDark,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SpaceAvatar(
    name: String,
    avatarUrl: String?,
    host: String,
    size: Dp,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 2.dp,
) {
    val resolvedAvatar = remember(host, avatarUrl) { resolveAvatarUrl(host, avatarUrl) }
    val initial = remember(name) { name.trim().firstOrNull()?.uppercase() ?: "M" }
    val avatarDescription = stringResource(R.string.profile_avatar)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFFCB6B), Color(0xFFFF8A72))))
            .border(borderWidth, Color.White, CircleShape)
            .testTag("space_avatar")
            .semantics {
                contentDescription = avatarDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.Bold,
        )
        if (resolvedAvatar != null) {
            AsyncImage(
                model = resolvedAvatar,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

internal fun resolveAvatarUrl(host: String, avatarUrl: String?): String? {
    val value = avatarUrl?.trim().orEmpty()
    if (value.isEmpty()) return null
    return try {
        val avatar = URI(value)
        if (avatar.isAbsolute) value else {
            val base = URI(if (host.endsWith('/')) host else "$host/")
            base.resolve(avatar).toString()
        }
    } catch (_: Exception) {
        value
    }
}

@Preview(name = "Space profile compact", widthDp = 390, showBackground = true)
@Preview(name = "Space profile wide", widthDp = 720, showBackground = true)
@Composable
private fun SpaceProfileHeaderPreview() {
    MoeMemosTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            SpaceProfileHeader(
                displayName = "云端漫游者",
                avatarUrl = null,
                host = "",
                memoCount = 128,
                tagCount = 16,
                dayCount = 365,
                onCompose = {},
                onResources = {},
                onArchived = {},
                onSearch = {},
            )
        }
    }
}
