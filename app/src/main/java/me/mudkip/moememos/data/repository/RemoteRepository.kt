package me.mudkip.moememos.data.repository

import com.skydoves.sandwich.ApiResponse
import me.mudkip.moememos.data.model.Memo
import me.mudkip.moememos.data.model.MemoComment
import me.mudkip.moememos.data.model.MemoReaction
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.Resource
import me.mudkip.moememos.data.model.User
import okhttp3.MediaType
import java.io.InputStream
import java.time.Instant

abstract class RemoteRepository {
    abstract suspend fun listMemos(): ApiResponse<List<Memo>>
    abstract suspend fun listArchivedMemos(): ApiResponse<List<Memo>>
    abstract suspend fun listWorkspaceMemos(pageSize: Int, pageToken: String?): ApiResponse<Pair<List<Memo>, String?>>

    abstract suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resourceRemoteIds: List<String>,
        tags: List<String>? = null,
        createdAt: Instant? = null,
        /** Stable client-selected ID when the remote protocol supports idempotent recovery. */
        memoId: String? = null,
    ): ApiResponse<Memo>

    abstract suspend fun updateMemo(
        remoteId: String,
        content: String? = null,
        resourceRemoteIds: List<String>? = null,
        visibility: MemoVisibility? = null,
        tags: List<String>? = null,
        pinned: Boolean? = null,
        archived: Boolean? = null
    ): ApiResponse<Memo>

    abstract suspend fun deleteMemo(remoteId: String): ApiResponse<Unit>

    abstract suspend fun listResources(): ApiResponse<List<Resource>>

    abstract suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> InputStream,
        memoRemoteId: String? = null,
        /** Stable client-selected ID when the remote protocol supports idempotent recovery. */
        resourceId: String? = null,
    ): ApiResponse<Resource>

    abstract suspend fun deleteResource(remoteId: String): ApiResponse<Unit>
    abstract suspend fun getCurrentUser(): ApiResponse<User>

    open suspend fun listMemoComments(remoteId: String): ApiResponse<List<MemoComment>> =
        ApiResponse.exception(UnsupportedOperationException("Comments require Memos v1"))

    open suspend fun createMemoComment(remoteId: String, content: String): ApiResponse<MemoComment> =
        ApiResponse.exception(UnsupportedOperationException("Comments require Memos v1"))

    open suspend fun listMemoReactions(remoteId: String): ApiResponse<List<MemoReaction>> =
        ApiResponse.exception(UnsupportedOperationException("Reactions require Memos v1"))

    /** Toggles [reactionType] for the authenticated user and returns the canonical server list. */
    open suspend fun toggleMemoReaction(
        remoteId: String,
        reactionType: String,
    ): ApiResponse<List<MemoReaction>> =
        ApiResponse.exception(UnsupportedOperationException("Reactions require Memos v1"))
}
