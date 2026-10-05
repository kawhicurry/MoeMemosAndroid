package me.mudkip.moememos.data.api

import android.net.Uri
import androidx.core.net.toUri
import com.skydoves.sandwich.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant

interface MemosV1Api {
    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(): ApiResponse<GetCurrentUserResponse>

    @GET("api/v1/users/{id}/settings/GENERAL")
    suspend fun getUserSetting(@Path("id") userId: String): ApiResponse<MemosV1UserSetting>

    @GET("api/v1/memos")
    suspend fun listMemos(
        @Query("pageSize") pageSize: Int,
        @Query("pageToken") pageToken: String? = null,
        @Query("state") state: MemosV1State? = null,
        @Query("filter") filter: String? = null,
    ): ApiResponse<ListMemosResponse>

    @POST("api/v1/memos")
    suspend fun createMemo(
        @Query("memoId") memoId: String? = null,
        @Body body: MemosV1CreateMemoRequest,
    ): ApiResponse<MemosV1Memo>

    @GET("api/v1/memos/{id}")
    suspend fun getMemo(@Path("id") memoId: String): ApiResponse<MemosV1Memo>

    @PATCH("api/v1/memos/{id}")
    suspend fun updateMemo(
        @Path("id") memoId: String,
        @Query("updateMask") updateMask: String,
        @Body body: UpdateMemoRequest,
    ): ApiResponse<MemosV1Memo>

    @DELETE("api/v1/memos/{id}")
    suspend fun deleteMemo(@Path("id") memoId: String): ApiResponse<Unit>

    @GET("api/v1/attachments")
    suspend fun listResources(
        @Query("pageSize") pageSize: Int,
        @Query("pageToken") pageToken: String? = null,
    ): ApiResponse<ListResourceResponse>

    @POST("api/v1/attachments")
    suspend fun createResource(
        @Query("attachmentId") attachmentId: String? = null,
        @Body body: RequestBody,
    ): ApiResponse<MemosV1Resource>

    @POST("api/v1/attachments:upload")
    suspend fun uploadResource(@Body body: MemosV1UploadAttachmentRequest): ApiResponse<MemosV1UploadAttachmentResponse>

    @GET("api/v1/attachments/{id}")
    suspend fun getResource(@Path("id") resourceId: String): ApiResponse<MemosV1Resource>

    @DELETE("api/v1/attachments/{id}")
    suspend fun deleteResource(@Path("id") resourceId: String): ApiResponse<Unit>

    @GET("api/v1/instance/profile")
    suspend fun getProfile(): ApiResponse<MemosProfile>

    @GET("api/v1/users/{id}")
    suspend fun getUser(@Path("id") userId: String): ApiResponse<MemosV1User>

    @GET("api/v1/users/{id}:getStats")
    suspend fun getUserStats(@Path("id") userId: String): ApiResponse<MemosV1Stats>

    @POST("api/v1/memos/{id}/comments")
    suspend fun createMemoComment(
        @Path("id") memoId: String,
        @Query("commentId") commentId: String? = null,
        @Body body: MemosV1CreateMemoRequest,
    ): ApiResponse<MemosV1Memo>

    @GET("api/v1/memos/{id}/comments")
    suspend fun listMemoComments(
        @Path("id") memoId: String,
        @Query("pageSize") pageSize: Int,
        @Query("pageToken") pageToken: String? = null,
        @Query("orderBy") orderBy: String? = null,
    ): ApiResponse<MemosV1ListMemoCommentsResponse>

    @POST("api/v1/memos/{id}/reactions")
    suspend fun upsertMemoReaction(
        @Path("id") memoId: String,
        @Body body: MemosV1UpsertReactionRequest,
    ): ApiResponse<MemosV1Reaction>

    @GET("api/v1/memos/{id}/reactions")
    suspend fun listMemoReactions(
        @Path("id") memoId: String,
        @Query("pageSize") pageSize: Int,
        @Query("pageToken") pageToken: String? = null,
    ): ApiResponse<MemosV1ListMemoReactionsResponse>

    @DELETE("api/v1/memos/{memoId}/reactions/{reactionId}")
    suspend fun deleteMemoReaction(
        @Path("memoId") memoId: String,
        @Path("reactionId") reactionId: String,
    ): ApiResponse<Unit>
}

@Serializable
data class MemosV1User(
    val name: String,
    val role: MemosRole = MemosRole.ROLE_UNSPECIFIED,
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val state: MemosV1State = MemosV1State.STATE_UNSPECIFIED,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createTime: Instant? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val updateTime: Instant? = null
)

@Serializable
data class GetCurrentUserResponse(
    val user: MemosV1User?
)

@Serializable
data class MemosV1CreateMemoRequest(
    val content: String,
    val visibility: MemosVisibility?,
    val attachments: List<MemosV1Resource>?,
    val space: String? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createTime: Instant? = null
)

@Serializable
data class ListMemosResponse(
    val memos: List<MemosV1Memo>,
    val nextPageToken: String?
)

@Serializable
data class UpdateMemoRequest(
    val content: String? = null,
    val visibility: MemosVisibility? = null,
    val state: MemosV1State? = null,
    val pinned: Boolean? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val updateTime: Instant? = null,
    val attachments: List<MemosV1Resource>? = null
) {
    /**
     * Memos 0.31 requires a non-empty protobuf FieldMask in the query string. Keep the
     * paths in proto snake_case even though the JSON body uses lowerCamelCase.
     */
    fun updateMask(): String = buildList {
        if (content != null) add("content")
        if (visibility != null) add("visibility")
        if (state != null) add("state")
        if (pinned != null) add("pinned")
        if (updateTime != null) add("update_time")
        if (attachments != null) add("attachments")
    }.joinToString(",")
}

@Serializable
data class ListResourceResponse(
    val attachments: List<MemosV1Resource>,
    val nextPageToken: String? = null,
)

@Serializable
data class CreateResourceRequest(
    val filename: String,
    val type: String,
    val content: String,
    val memo: String?
)

@Serializable
data class MemosV1Memo(
    val name: String,
    val state: MemosV1State? = null,
    val creator: String? = null,
    /** Memos 0.31 placement, for example `spaces/{id}`. */
    val space: String? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createTime: Instant? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val updateTime: Instant? = null,
    val content: String? = null,
    val visibility: MemosVisibility? = null,
    val pinned: Boolean? = null,
    val attachments: List<MemosV1Resource>? = null,
    val tags: List<String>? = null,
    val reactions: List<MemosV1Reaction>? = null,
    /** Set for comment memos; fetching the parent still requires normal read permission. */
    val parent: String? = null,
)

@Serializable
data class MemosV1Resource(
    val name: String? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createTime: Instant? = null,
    val filename: String? = null,
    val externalLink: String? = null,
    val type: String? = null,
    val size: String? = null,
    val memo: String? = null
) {
    fun uri(host: String): Uri {
        if (!externalLink.isNullOrEmpty()) {
            return externalLink.toUri()
        }
        return host.toUri()
            .buildUpon().appendPath("file").appendEncodedPath(name ?: "").appendPath(filename ?: "").build()
    }
}

@Serializable
data class MemosV1UploadAttachmentRequest(
    val spec: MemosV1UploadAttachmentSpec? = null,
    val uploadId: String? = null,
    /** Proto JSON represents int64 values as decimal strings. */
    val writeOffset: String,
    /** Base64-encoded raw bytes, as required by proto JSON's bytes mapping. */
    val data: String? = null,
    val finishWrite: Boolean = false,
)

@Serializable
data class MemosV1UploadAttachmentSpec(
    val attachment: MemosV1UploadAttachment,
    val attachmentId: String? = null,
    /** Proto JSON represents int64 values as decimal strings. */
    val totalSize: String,
)

@Serializable
data class MemosV1UploadAttachment(
    val filename: String,
    val type: String,
    val memo: String? = null,
)

@Serializable
data class MemosV1UploadAttachmentResponse(
    val uploadId: String = "",
    /** Proto JSON represents int64 values as decimal strings. */
    val committedSize: String = "0",
    val attachment: MemosV1Resource? = null,
    val maxChunkSize: Int = 0,
)

@Serializable
data class MemosV1Reaction(
    val name: String? = null,
    val creator: String? = null,
    val reactionType: String,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createTime: Instant? = null,
)

@Serializable
data class MemosV1UpsertReactionRequest(
    val reaction: MemosV1Reaction,
)

@Serializable
data class MemosV1ListMemoCommentsResponse(
    val memos: List<MemosV1Memo>,
    val nextPageToken: String? = null,
)

@Serializable
data class MemosV1ListMemoReactionsResponse(
    val reactions: List<MemosV1Reaction>,
    val nextPageToken: String? = null,
)

@Serializable
data class MemosV1UserSettingGeneralSetting(
    val locale: String? = null,
    val memoVisibility: MemosVisibility? = null,
    val theme: String? = null
)

@Serializable
data class MemosV1UserSetting(
    val generalSetting: MemosV1UserSettingGeneralSetting?
)

@Serializable
enum class MemosV1State {
    @SerialName("STATE_UNSPECIFIED")
    STATE_UNSPECIFIED,
    @SerialName("NORMAL")
    NORMAL,
    @SerialName("ARCHIVED")
    ARCHIVED,
}

@Serializable
data class MemosV1Stats(
    val tagCount: Map<String, Int>,
)
