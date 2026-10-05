package me.mudkip.moememos.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemoEditGesture
import me.mudkip.moememos.ext.icon
import me.mudkip.moememos.ext.navigateToMemoEditor
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ext.titleResource
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState

@Composable
fun MemosCard(
    memo: MemoEntity,
    onClick: (MemoEntity) -> Unit,
    editGesture: MemoEditGesture = MemoEditGesture.NONE,
    previewMode: Boolean = false,
    showSyncStatus: Boolean = false,
    onTagClick: ((String) -> Unit)? = null
) {
    val memosViewModel = LocalMemos.current
    val rootNavController = LocalRootNavController.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsStateWithLifecycle()
    val currentUser = userStateViewModel.currentUser
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visibilityLabel = stringResource(memo.visibility.titleResource)

    val cardModifier = Modifier
        .padding(horizontal = 12.dp, vertical = 7.dp)
        .fillMaxWidth()
        .testTag("space_memo_card")
        .combinedClickable(
            onClick = {
                if (editGesture == MemoEditGesture.SINGLE) {
                    rootNavController.navigateToMemoEditor(memo.identifier)
                } else {
                    onClick(memo)
                }
            },
            onLongClick = if (editGesture == MemoEditGesture.LONG) {
                {
                    rootNavController.navigateToMemoEditor(memo.identifier)
                }
            } else {
                null
            },
            onDoubleClick = if (editGesture == MemoEditGesture.DOUBLE) {
                {
                    rootNavController.navigateToMemoEditor(memo.identifier)
                }
            } else {
                null
            }
        )

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (memo.pinned) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(start = 14.dp, top = 12.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpaceAvatar(
                    name = currentUser?.name.orEmpty(),
                    avatarUrl = currentUser?.avatarUrl,
                    host = userStateViewModel.host,
                    size = 42.dp,
                    borderWidth = 1.5.dp,
                )
                Column(Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentUser?.name ?: stringResource(R.string.my_space),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (memo.pinned) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = stringResource(R.string.pinned),
                                modifier = Modifier.padding(start = 5.dp).size(15.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            DateUtils.getRelativeTimeSpanString(
                                memo.date.toEpochMilli(),
                                System.currentTimeMillis(),
                                DateUtils.SECOND_IN_MILLIS
                            ).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (showSyncStatus && memo.needsSync) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = R.string.memo_sync_pending.string,
                                modifier = Modifier.padding(start = 5.dp).size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                MemosCardActionButton(memo)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("space_memo_content"),
            ) {
                MemoContent(
                    memo,
                    previewMode = previewMode,
                    checkboxChange = { checked, startOffset, endOffset ->
                        scope.launch {
                            var text = memo.content.substring(startOffset, endOffset)
                            text = if (checked) {
                                text.replace("[ ]", "[x]")
                            } else {
                                text.replace("[x]", "[ ]")
                            }
                            memosViewModel.editMemo(
                                memo.identifier,
                                memo.content.replaceRange(startOffset, endOffset, text),
                                memo.resources,
                                memo.visibility
                            )
                        }
                    },
                    onViewMore = {
                        onClick(memo)
                    },
                    onTagClick = onTagClick
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("space_memo_metadata"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("space_memo_visibility")
                        .semantics(mergeDescendants = true) {
                            contentDescription = visibilityLabel
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        memo.visibility.icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        visibilityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (memo.resources.isNotEmpty()) {
                    SpaceMemoMediaSummary(
                        imageCount = memo.resources.count { it.mimeType?.startsWith("image/") == true },
                        attachmentCount = memo.resources.count { it.mimeType?.startsWith("image/") != true },
                    )
                }
            }

            if (currentAccount is Account.MemosV1 && memo.remoteId != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                SpaceMemoSocialSection(
                    memoIdentifier = memo.identifier,
                    onLoad = { memosViewModel.getMemoSocial(memo.identifier) },
                    onComment = { content ->
                        memosViewModel.createMemoComment(memo.identifier, content)
                    },
                    onToggleReaction = { reactionType ->
                        memosViewModel.toggleMemoReaction(memo.identifier, reactionType)
                    },
                )
            }

            SpaceMemoActionBar(
                pinned = memo.pinned,
                onShare = { shareMemo(context, memo.content) },
                onEdit = { rootNavController.navigateToMemoEditor(memo.identifier) },
                onTogglePin = {
                    scope.launch {
                        memosViewModel.updateMemoPinned(memo.identifier, !memo.pinned)
                    }
                },
                onArchive = {
                    scope.launch {
                        memosViewModel.archiveMemo(memo.identifier)
                    }
                },
            )
        }
    }
}

@Composable
internal fun SpaceMemoMediaSummary(
    imageCount: Int,
    attachmentCount: Int,
) {
    val imageLabel = stringResource(R.string.image)
    val attachmentLabel = stringResource(R.string.attachment)
    Row(
        modifier = Modifier.testTag("space_memo_media_summary"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imageCount > 0) {
            Row(
                modifier = Modifier
                    .testTag("space_memo_media_images")
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$imageCount $imageLabel"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    imageCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (attachmentCount > 0) {
            Row(
                modifier = Modifier
                    .testTag("space_memo_media_attachments")
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$attachmentCount $attachmentLabel"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    attachmentCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SpaceMemoActionBar(
    pinned: Boolean,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("space_memo_actions"),
    ) {
        val stacked = maxWidth < 340.dp
        Row(Modifier.fillMaxWidth()) {
            SpaceMemoAction(
                label = stringResource(R.string.share),
                icon = Icons.Outlined.Share,
                testTag = "space_memo_action_share",
                stacked = stacked,
                onClick = onShare,
                modifier = Modifier.weight(1f),
            )
            SpaceMemoAction(
                label = stringResource(R.string.edit),
                icon = Icons.Outlined.Edit,
                testTag = "space_memo_action_edit",
                stacked = stacked,
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
            SpaceMemoAction(
                label = stringResource(if (pinned) R.string.unpin else R.string.pin),
                icon = if (pinned) Icons.Outlined.PinDrop else Icons.Outlined.PushPin,
                testTag = "space_memo_action_pin",
                stacked = stacked,
                onClick = onTogglePin,
                modifier = Modifier.weight(1f),
            )
            SpaceMemoAction(
                label = stringResource(R.string.archive),
                icon = Icons.Outlined.Archive,
                testTag = "space_memo_action_archive",
                stacked = stacked,
                onClick = onArchive,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SpaceMemoAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    stacked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 54.dp)
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = label
            },
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        if (stacked) {
            Column(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

private fun shareMemo(context: android.content.Context, content: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, content)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

@Composable
fun MemosCardActionButton(
    memo: MemoEntity,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsStateWithLifecycle()
    val rootNavController = LocalRootNavController.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val memoLabel = stringResource(R.string.memo)

    Box {
        ActionIconButton(
            label = stringResource(R.string.more_options),
            onClick = { menuExpanded = true },
            modifier = Modifier.testTag("space_memo_more"),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (memo.pinned) {
                DropdownMenuItem(
                    modifier = Modifier.testTag("space_memo_menu_unpin"),
                    text = { Text(R.string.unpin.string) },
                    onClick = {
                        scope.launch {
                            memosViewModel.updateMemoPinned(memo.identifier, false).suspendOnSuccess {
                                menuExpanded = false
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PinDrop,
                            contentDescription = null
                        )
                    })
            } else {
                DropdownMenuItem(
                    modifier = Modifier.testTag("space_memo_menu_pin"),
                    text = { Text(R.string.pin.string) },
                    onClick = {
                        scope.launch {
                            memosViewModel.updateMemoPinned(memo.identifier, true).suspendOnSuccess {
                                menuExpanded = false
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = null
                        )
                    })
            }
            DropdownMenuItem(
                modifier = Modifier.testTag("space_memo_menu_edit"),
                text = { Text(R.string.edit.string) },
                onClick = {
                    rootNavController.navigateToMemoEditor(memo.identifier)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                modifier = Modifier.testTag("space_memo_menu_share"),
                text = { Text(R.string.share.string) },
                onClick = {
                    shareMemo(context, memo.content)
                    menuExpanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                modifier = Modifier.testTag("space_memo_menu_copy"),
                text = { Text(R.string.copy.string) },
                onClick = {
                    clipboardManager?.setPrimaryClip(
                        ClipData.newPlainText(memoLabel, memo.content)
                    )
                    menuExpanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null
                    )
                })
            if (currentAccount !is Account.Local) {
                DropdownMenuItem(
                    modifier = Modifier.testTag("space_memo_menu_copy_link"),
                    text = { Text(R.string.copy_link.string) },
                    onClick = {
                        memosViewModel.host.value?.let { host ->
                            val memoUrl = "$host/${memo.remoteId ?: memo.identifier}"
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, memoUrl)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null
                        )
                    })
            }
            DropdownMenuItem(
                modifier = Modifier.testTag("space_memo_menu_archive"),
                text = { Text(R.string.archive.string) },
                onClick = {
                    scope.launch {
                        memosViewModel.archiveMemo(memo.identifier).suspendOnSuccess {
                            menuExpanded = false
                        }
                    }
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Archive,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                modifier = Modifier.testTag("space_memo_menu_delete"),
                text = { Text(R.string.delete.string) },
                onClick = {
                    showDeleteDialog = true
                    menuExpanded = false
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null
                    )
                })
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(R.string.delete_this_memo.string) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            memosViewModel.deleteMemo(memo.identifier).suspendOnSuccess {
                                showDeleteDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(R.string.confirm.string)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text(R.string.cancel.string)
                }
            }
        )
    }
}
