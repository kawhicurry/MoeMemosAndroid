package me.mudkip.moememos.data.repository

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.StatusCode
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.mapSuccess
import com.skydoves.sandwich.onSuccess
import com.skydoves.sandwich.retrofit.statusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.mudkip.moememos.data.api.MemosV1Api
import me.mudkip.moememos.data.api.MemosV1CreateMemoRequest
import me.mudkip.moememos.data.api.MemosV1Memo
import me.mudkip.moememos.data.api.MemosV1Reaction
import me.mudkip.moememos.data.api.MemosV1Resource
import me.mudkip.moememos.data.api.MemosV1State
import me.mudkip.moememos.data.api.MemosV1UpsertReactionRequest
import me.mudkip.moememos.data.api.MemosVisibility
import me.mudkip.moememos.data.api.UpdateMemoRequest
import me.mudkip.moememos.data.constant.MoeMemosException
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.Memo
import me.mudkip.moememos.data.model.MemoComment
import me.mudkip.moememos.data.model.MemoReaction
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.Resource
import me.mudkip.moememos.data.model.User
import okhttp3.MediaType
import java.time.Instant
import java.util.UUID

internal const val WORKSPACE_MEMO_FILTER =
    "visibility in [\"PUBLIC\", \"PROTECTED\", \"SPACE\"]"

class MemosV1Repository(
    private val memosApi: MemosV1Api,
    private val account: Account.MemosV1
): RemoteRepository() {
    private val remoteUserIdentifier = account.info.remoteIdentifier

    private fun convertUser(user: me.mudkip.moememos.data.api.MemosV1User): User = User(
        identifier = user.name,
        name = user.displayName ?: user.username,
        startDate = user.createTime ?: Instant.now(),
        avatarUrl = user.avatarUrl,
    )

    private fun convertResource(resource: MemosV1Resource): Resource {
        return Resource(
            remoteId = requireNotNull(resource.name),
            date = resource.createTime ?: Instant.now(),
            filename = resource.filename ?: "",
            uri = resource.uri(account.info.host).toString(),
            mimeType = resource.type
        )
    }

    private fun convertMemo(memo: MemosV1Memo): Memo {
        return Memo(
            remoteId = memo.name,
            content = memo.content ?: "",
            date = memo.createTime ?: Instant.now(),
            pinned = memo.pinned ?: false,
            visibility = memo.visibility?.toMemoVisibility() ?: MemoVisibility.PRIVATE,
            resources = memo.attachments?.map { convertResource(it) } ?: emptyList(),
            tags = memo.tags.orEmpty(),
            archived = memo.state == MemosV1State.ARCHIVED,
            updatedAt = memo.updateTime
        )
    }

    private fun convertComment(memo: MemosV1Memo, creators: Map<String, User>): MemoComment =
        MemoComment(
            remoteId = memo.name,
            content = memo.content.orEmpty(),
            creator = memo.creator?.let(creators::get),
            date = memo.createTime ?: Instant.now(),
        )

    private fun convertReaction(reaction: MemosV1Reaction): MemoReaction {
        val creator = reaction.creator.orEmpty()
        return MemoReaction(
            remoteId = reaction.name.orEmpty(),
            creatorId = creator,
            reactionType = reaction.reactionType,
            date = reaction.createTime ?: Instant.EPOCH,
            mine = getName(creator) == getName(remoteUserIdentifier),
        )
    }

    private suspend fun listMemosByFilter(state: MemosV1State, filter: String): ApiResponse<List<Memo>> {
        var nextPageToken = ""
        val memos = arrayListOf<Memo>()

        do {
            val resp = memosApi.listMemos(MEMOS_V1_PAGE_SIZE, nextPageToken, state, filter)
                .onSuccess { nextPageToken = data.nextPageToken.orEmpty() }
                .mapSuccess { this.memos.map { convertMemo(it) } }
            if (resp is ApiResponse.Success) {
                memos.addAll(resp.data)
            } else {
                return resp
            }
        } while (nextPageToken.isNotEmpty())
        return ApiResponse.Success(memos)
    }

    private fun getId(identifier: String): String {
        return identifier.substringBefore('|').substringAfterLast('/')
    }

    private fun getName(identifier: String): String {
        return identifier.substringBefore('|')
    }

    private suspend fun listCurrentUserMemos(state: MemosV1State): ApiResponse<List<Memo>> {
        return listMemosByFilter(state, "creator == \"$remoteUserIdentifier\"")
    }

    override suspend fun listMemos(): ApiResponse<List<Memo>> {
        return listCurrentUserMemos(MemosV1State.NORMAL)
    }

    override suspend fun listArchivedMemos(): ApiResponse<List<Memo>> {
        return listCurrentUserMemos(MemosV1State.ARCHIVED)
    }

    override suspend fun listWorkspaceMemos(
        pageSize: Int,
        pageToken: String?
    ): ApiResponse<Pair<List<Memo>, String?>> {
        val resp = memosApi.listMemos(pageSize, pageToken, filter = WORKSPACE_MEMO_FILTER)
        if (resp !is ApiResponse.Success) {
            return resp.mapSuccess { emptyList<Memo>() to null }
        }
        val users = resp.data.memos.mapNotNull { it.creator }.map { getId(it) }.toSet()
        val userResp = coroutineScope {
            users.map { userId ->
                async { memosApi.getUser(userId).getOrNull() }
            }.awaitAll()
        }.filterNotNull()
        val userMap = mapOf(*userResp.map { user -> user.name to user }.toTypedArray())

        return resp
            .mapSuccess { this.memos.map {
                convertMemo(it).copy(
                    creator = it.creator?.let { creator ->
                        userMap[creator]?.let { user ->
                            User(
                                user.name,
                                user.displayName ?: user.username,
                                user.createTime ?: Instant.now(),
                                avatarUrl = user.avatarUrl,
                            )
                        }
                    }
                )
            } to this.nextPageToken?.ifEmpty { null } }
    }

    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resourceRemoteIds: List<String>,
        tags: List<String>?,
        createdAt: Instant?,
        memoId: String?,
    ): ApiResponse<Memo> {
        val stableId = memoId?.let(::stableMemosObjectId)
        val created = memosApi.createMemo(
            memoId = stableId,
            body = MemosV1CreateMemoRequest(
                content = content,
                visibility = MemosVisibility.fromMemoVisibility(visibility),
                attachments = resourceRemoteIds.map { MemosV1Resource(name = getName(it)) },
                createTime = createdAt
            )
        )
        return recoverCreatedObject(created, stableId, memosApi::getMemo)
            .mapSuccess { convertMemo(this) }
    }

    override suspend fun updateMemo(
        remoteId: String,
        content: String?,
        resourceRemoteIds: List<String>?,
        visibility: MemoVisibility?,
        tags: List<String>?,
        pinned: Boolean?,
        archived: Boolean?
    ): ApiResponse<Memo> {
        val requestedUpdate = UpdateMemoRequest(
            content = content,
            visibility = visibility?.let { MemosVisibility.fromMemoVisibility(it) },
            pinned = pinned,
            state = archived?.let { isArchived -> if (isArchived) MemosV1State.ARCHIVED else MemosV1State.NORMAL },
            attachments = resourceRemoteIds?.map { MemosV1Resource(name = getName(it)) }
        )
        if (requestedUpdate.updateMask().isEmpty()) {
            return ApiResponse.exception(MoeMemosException.invalidParameter)
        }
        // Track every client mutation for the repository's updatedAt-based conflict detection.
        val body = requestedUpdate.copy(updateTime = Instant.now())
        return memosApi.updateMemo(
            memoId = getId(remoteId),
            updateMask = body.updateMask(),
            body = body,
        ).mapSuccess { convertMemo(this) }
    }

    override suspend fun deleteMemo(remoteId: String): ApiResponse<Unit> {
        return memosApi.deleteMemo(getId(remoteId))
    }

    override suspend fun listResources(): ApiResponse<List<Resource>> {
        return listAllMemosV1Resources(memosApi::listResources)
            .mapSuccess { map { convertResource(it) } }
    }

    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> java.io.InputStream,
        memoRemoteId: String?,
        resourceId: String?,
    ): ApiResponse<Resource> {
        return createRemoteResource(
            filename = filename,
            type = type,
            contentLength = contentLength,
            openInputStream = openInputStream,
            memoRemoteId = memoRemoteId,
            resourceId = resourceId,
        ).mapSuccess { convertResource(this) }
    }

    internal suspend fun createRemoteResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> java.io.InputStream,
        memoRemoteId: String?,
        resourceId: String?,
    ): ApiResponse<MemosV1Resource> {
        val mimeType = type?.toString() ?: "application/octet-stream"
        val memoName = memoRemoteId?.let(::getName)
        val stableId = resourceId?.let(::stableMemosObjectId)
        if (contentLength != null && contentLength >= 0L) {
            val chunked = MemosV1ChunkUploader(memosApi::uploadResource).upload(
                filename = filename,
                type = mimeType,
                totalSize = contentLength,
                openInputStream = openInputStream,
                memo = memoName,
                attachmentId = stableId,
            )
            if (!isChunkUploadUnavailable(chunked)) {
                return recoverCreatedObject(chunked, stableId, memosApi::getResource)
            }
        }

        val requestBody = StreamingBase64JsonRequestBody(
            filename = filename,
            type = mimeType,
            memo = memoName,
            contentLength = contentLength,
            openInputStream = openInputStream
        )
        val legacy = memosApi.createResource(stableId, requestBody)
        return recoverCreatedObject(legacy, stableId, memosApi::getResource)
    }

    override suspend fun deleteResource(remoteId: String): ApiResponse<Unit> {
        return memosApi.deleteResource(getId(remoteId))
    }

    override suspend fun getCurrentUser(): ApiResponse<User> {
        val resp = memosApi.getCurrentUser().mapSuccess {
            if (user == null) {
                throw MoeMemosException.notLogin
            }
            User(
                user.name,
                user.displayName ?: user.username,
                user.createTime ?: Instant.now(),
                avatarUrl = user.avatarUrl
            )
        }
        if (resp !is ApiResponse.Success) {
            return resp
        }

        return memosApi.getUserSetting(getId(resp.data.identifier)).mapSuccess {
            resp.data.copy(
                defaultVisibility = generalSetting?.memoVisibility?.toMemoVisibility() ?: MemoVisibility.PRIVATE
            )
        }
    }

    override suspend fun listMemoComments(remoteId: String): ApiResponse<List<MemoComment>> {
        var pageToken: String? = null
        val comments = arrayListOf<MemosV1Memo>()
        do {
            val response = memosApi.listMemoComments(
                memoId = getId(remoteId),
                pageSize = MEMOS_V1_PAGE_SIZE,
                pageToken = pageToken,
                orderBy = "create_time asc",
            )
            if (response !is ApiResponse.Success) {
                return response.mapSuccess { emptyList() }
            }
            comments += response.data.memos
            pageToken = response.data.nextPageToken?.ifBlank { null }
        } while (pageToken != null)

        val creatorNames = comments.mapNotNull { it.creator }.toSet()
        val creators = coroutineScope {
            creatorNames.map { creatorName ->
                async {
                    memosApi.getUser(getId(creatorName)).getOrNull()?.let(::convertUser)
                }
            }.awaitAll()
        }.filterNotNull().associateBy { it.identifier }

        return ApiResponse.Success(
            comments.map { convertComment(it, creators) }.sortedBy { it.date }
        )
    }

    override suspend fun createMemoComment(
        remoteId: String,
        content: String,
    ): ApiResponse<MemoComment> {
        if (content.isBlank()) {
            return ApiResponse.exception(MoeMemosException.invalidParameter)
        }
        val parent = memosApi.getMemo(getId(remoteId))
        if (parent !is ApiResponse.Success) {
            return parent.mapSuccess { convertComment(this, emptyMap()) }
        }
        val stableId = stableMemosObjectId(UUID.randomUUID().toString())
        val created = memosApi.createMemoComment(
            memoId = getId(remoteId),
            commentId = stableId,
            body = MemosV1CreateMemoRequest(
                content = content.trim(),
                visibility = parent.data.visibility ?: MemosVisibility.PRIVATE,
                attachments = emptyList(),
                space = parent.data.space,
            ),
        )
        val recovered = recoverCreatedObject(created, stableId, memosApi::getMemo)
        if (recovered !is ApiResponse.Success) {
            return recovered.mapSuccess { convertComment(this, emptyMap()) }
        }
        val creator = recovered.data.creator
        val creators = if (creator != null && getName(creator) == getName(remoteUserIdentifier)) {
            mapOf(creator to account.toUser())
        } else {
            emptyMap()
        }
        return ApiResponse.Success(convertComment(recovered.data, creators))
    }

    override suspend fun listMemoReactions(remoteId: String): ApiResponse<List<MemoReaction>> {
        var pageToken: String? = null
        val reactions = arrayListOf<MemosV1Reaction>()
        do {
            val response = memosApi.listMemoReactions(
                memoId = getId(remoteId),
                pageSize = MEMOS_V1_PAGE_SIZE,
                pageToken = pageToken,
            )
            if (response !is ApiResponse.Success) {
                return response.mapSuccess { emptyList() }
            }
            reactions += response.data.reactions
            pageToken = response.data.nextPageToken?.ifBlank { null }
        } while (pageToken != null)
        return ApiResponse.Success(reactions.map(::convertReaction))
    }

    override suspend fun toggleMemoReaction(
        remoteId: String,
        reactionType: String,
    ): ApiResponse<List<MemoReaction>> {
        if (reactionType.isBlank()) {
            return ApiResponse.exception(MoeMemosException.invalidParameter)
        }
        val current = listMemoReactions(remoteId)
        if (current !is ApiResponse.Success) return current
        val mine = current.data.firstOrNull { it.mine }
        val mutation = if (mine?.reactionType == reactionType && mine.remoteId.isNotBlank()) {
            memosApi.deleteMemoReaction(
                memoId = getId(remoteId),
                reactionId = getId(mine.remoteId),
            )
        } else {
            memosApi.upsertMemoReaction(
                memoId = getId(remoteId),
                body = MemosV1UpsertReactionRequest(
                    reaction = MemosV1Reaction(reactionType = reactionType),
                ),
            ).mapSuccess { Unit }
        }
        if (mutation !is ApiResponse.Success) {
            return mutation.mapSuccess { emptyList() }
        }
        return listMemoReactions(remoteId)
    }
}

internal suspend fun <T> recoverCreatedObject(
    response: ApiResponse<T>,
    stableId: String?,
    getById: suspend (String) -> ApiResponse<T>,
): ApiResponse<T> {
    if (response is ApiResponse.Success || stableId == null) return response
    val outcomeMayBeCommitted = when (response) {
        is ApiResponse.Failure.Exception -> true
        is ApiResponse.Failure.Error ->
            response.statusCode == StatusCode.Conflict || response.statusCode.code >= 500
        is ApiResponse.Success -> false
    }
    if (!outcomeMayBeCommitted) return response
    val recovered = getById(stableId)
    return if (recovered is ApiResponse.Success) recovered else response
}
