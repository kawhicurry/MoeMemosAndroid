package me.mudkip.moememos.data.repository

import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.test.runTest
import me.mudkip.moememos.data.api.ListMemosResponse
import me.mudkip.moememos.data.api.MemosV1Api
import me.mudkip.moememos.data.api.MemosV1CreateMemoRequest
import me.mudkip.moememos.data.api.MemosV1ListMemoCommentsResponse
import me.mudkip.moememos.data.api.MemosV1ListMemoReactionsResponse
import me.mudkip.moememos.data.api.MemosV1Memo
import me.mudkip.moememos.data.api.MemosV1Reaction
import me.mudkip.moememos.data.api.MemosV1State
import me.mudkip.moememos.data.api.MemosV1User
import me.mudkip.moememos.data.api.MemosVisibility
import me.mudkip.moememos.data.api.UpdateMemoRequest
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.MemosAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant

class MemosV1RepositoryContractTest {
    private val account = Account.MemosV1(
        MemosAccount(
            host = "https://example.test/",
            remoteIdentifier = "users/alice",
        )
    )

    @Test
    fun updateSendsActualFieldMaskIncludingAttachmentReplacement() = runTest {
        var capturedId: String? = null
        var capturedMask: String? = null
        var capturedBody: UpdateMemoRequest? = null
        val repository = MemosV1Repository(fakeApi { method, args ->
            when (method.name) {
                "updateMemo" -> {
                    capturedId = args[0] as String
                    capturedMask = args[1] as String
                    capturedBody = args[2] as UpdateMemoRequest
                    ApiResponse.Success(
                        MemosV1Memo(
                            name = "memos/note-id",
                            state = MemosV1State.NORMAL,
                            content = "changed",
                            visibility = MemosVisibility.SPACE,
                            pinned = false,
                            attachments = emptyList(),
                            tags = listOf("remote-tag"),
                        )
                    )
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        }, account)

        val result = repository.updateMemo(
            remoteId = "memos/note-id",
            content = "changed",
            resourceRemoteIds = emptyList(),
            visibility = MemoVisibility.SPACE,
            pinned = false,
            archived = false,
        )

        assertTrue(result is ApiResponse.Success)
        assertEquals("note-id", capturedId)
        assertEquals(
            "content,visibility,state,pinned,update_time,attachments",
            capturedMask,
        )
        assertEquals(emptyList<Any>(), capturedBody?.attachments)
        assertEquals(MemosVisibility.SPACE, capturedBody?.visibility)
        assertEquals(listOf("remote-tag"), (result as ApiResponse.Success).data.tags)
    }

    @Test
    fun createUsesStableMemoIdAndFullAttachmentNames() = runTest {
        var capturedId: String? = null
        var capturedBody: MemosV1CreateMemoRequest? = null
        val stableId = "123e4567-e89b-12d3-a456-426614174000"
        val repository = MemosV1Repository(fakeApi { method, args ->
            when (method.name) {
                "createMemo" -> {
                    capturedId = args[0] as String
                    capturedBody = args[1] as MemosV1CreateMemoRequest
                    ApiResponse.Success(
                        MemosV1Memo(
                            name = "memos/$stableId",
                            content = "created",
                            visibility = MemosVisibility.PRIVATE,
                            attachments = emptyList(),
                        )
                    )
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        }, account)

        val result = repository.createMemo(
            content = "created",
            visibility = MemoVisibility.PRIVATE,
            resourceRemoteIds = listOf("attachments/media-id"),
            createdAt = Instant.EPOCH,
            memoId = stableId,
        )

        assertTrue(result is ApiResponse.Success)
        assertEquals(stableId, capturedId)
        assertEquals("attachments/media-id", capturedBody?.attachments?.single()?.name)
    }

    @Test
    fun listPreservesServerTagsAndPaginatesMemos() = runTest {
        val seenTokens = mutableListOf<String?>()
        val repository = MemosV1Repository(fakeApi { method, args ->
            when (method.name) {
                "listMemos" -> {
                    val token = args[1] as String?
                    seenTokens += token
                    if (token.isNullOrEmpty()) {
                        ApiResponse.Success(
                            ListMemosResponse(
                                memos = listOf(
                                    MemosV1Memo(
                                        name = "memos/one",
                                        content = "#one",
                                        tags = listOf("one"),
                                    )
                                ),
                                nextPageToken = "next",
                            )
                        )
                    } else {
                        ApiResponse.Success(
                            ListMemosResponse(
                                memos = listOf(
                                    MemosV1Memo(
                                        name = "memos/two",
                                        content = "#two",
                                        tags = listOf("two"),
                                    )
                                ),
                                nextPageToken = null,
                            )
                        )
                    }
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        }, account)

        val result = repository.listMemos()

        assertTrue(result is ApiResponse.Success)
        assertEquals(listOf(listOf("one"), listOf("two")), (result as ApiResponse.Success).data.map { it.tags })
        assertEquals(listOf("", "next"), seenTokens)
    }

    @Test
    fun lostCreateResponseRecoversStableMemoWithoutSecondPost() = runTest {
        val stableId = "123e4567-e89b-12d3-a456-426614174000"
        var creates = 0
        var reads = 0
        val repository = MemosV1Repository(fakeApi { method, args ->
            when (method.name) {
                "createMemo" -> {
                    creates += 1
                    assertEquals(stableId, args[0])
                    ApiResponse.Failure.Exception(IOException("response lost after commit"))
                }
                "getMemo" -> {
                    reads += 1
                    assertEquals(stableId, args[0])
                    ApiResponse.Success(
                        MemosV1Memo(
                            name = "memos/$stableId",
                            content = "created once",
                            visibility = MemosVisibility.PRIVATE,
                        )
                    )
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        }, account)

        val result = repository.createMemo(
            content = "created once",
            visibility = MemoVisibility.PRIVATE,
            resourceRemoteIds = emptyList(),
            memoId = stableId,
        )

        assertTrue(result is ApiResponse.Success)
        assertEquals(1, creates)
        assertEquals(1, reads)
    }

    @Test
    fun emptyUpdateFailsBeforeNetworkCall() = runTest {
        var called = false
        val repository = MemosV1Repository(fakeApi { method, _ ->
            called = true
            error("Unexpected API call: ${method.name}")
        }, account)

        val result = repository.updateMemo(remoteId = "memos/note-id")

        assertTrue(result is ApiResponse.Failure.Exception)
        assertFalse(called)
    }

    @Test
    fun commentsPaginateResolveCreatorsAndCreateWithParentAudience() = runTest {
        val firstTime = Instant.parse("2026-01-01T00:00:00Z")
        val secondTime = firstTime.plusSeconds(60)
        var createdBody: MemosV1CreateMemoRequest? = null
        val repository = MemosV1Repository(fakeApi { method, args ->
            when (method.name) {
                "listMemoComments" -> {
                    val token = args[2] as String?
                    if (token == null) {
                        ApiResponse.Success(
                            MemosV1ListMemoCommentsResponse(
                                memos = listOf(
                                    MemosV1Memo(
                                        name = "memos/comment-two",
                                        creator = "users/bob",
                                        createTime = secondTime,
                                        content = "second",
                                    )
                                ),
                                nextPageToken = "next",
                            )
                        )
                    } else {
                        ApiResponse.Success(
                            MemosV1ListMemoCommentsResponse(
                                memos = listOf(
                                    MemosV1Memo(
                                        name = "memos/comment-one",
                                        creator = "users/alice",
                                        createTime = firstTime,
                                        content = "first",
                                    )
                                )
                            )
                        )
                    }
                }
                "getUser" -> {
                    val id = args[0] as String
                    ApiResponse.Success(
                        MemosV1User(
                            name = "users/$id",
                            username = id,
                            displayName = id.replaceFirstChar { it.uppercase() },
                        )
                    )
                }
                "getMemo" -> ApiResponse.Success(
                    MemosV1Memo(
                        name = "memos/parent",
                        content = "parent",
                        visibility = MemosVisibility.SPACE,
                        space = "spaces/friends",
                    )
                )
                "createMemoComment" -> {
                    createdBody = args[2] as MemosV1CreateMemoRequest
                    ApiResponse.Success(
                        MemosV1Memo(
                            name = "memos/new-comment",
                            creator = "users/alice",
                            content = createdBody?.content,
                            visibility = createdBody?.visibility,
                            space = createdBody?.space,
                            createTime = secondTime,
                        )
                    )
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        }, account)

        val comments = repository.listMemoComments("memos/parent")
        assertTrue(comments is ApiResponse.Success)
        comments as ApiResponse.Success
        assertEquals(listOf("first", "second"), comments.data.map { it.content })
        assertEquals(listOf("Alice", "Bob"), comments.data.map { it.creator?.name })

        val created = repository.createMemoComment("memos/parent", "  hello space  ")
        assertTrue(created is ApiResponse.Success)
        assertEquals("hello space", createdBody?.content)
        assertEquals(MemosVisibility.SPACE, createdBody?.visibility)
        assertEquals("spaces/friends", createdBody?.space)
    }

    @Test
    fun reactionToggleUpsertsDifferentChoiceAndDeletesActiveChoice() = runTest {
        var current = listOf(
            MemosV1Reaction(
                name = "memos/parent/reactions/7",
                creator = "users/alice",
                reactionType = "👍",
            )
        )
        var upserts = 0
        var deletes = 0
        val repository = MemosV1Repository(fakeApi { method, args ->
            when (method.name) {
                "listMemoReactions" -> ApiResponse.Success(
                    MemosV1ListMemoReactionsResponse(current)
                )
                "upsertMemoReaction" -> {
                    upserts += 1
                    val request = args[1] as me.mudkip.moememos.data.api.MemosV1UpsertReactionRequest
                    current = listOf(
                        MemosV1Reaction(
                            name = "memos/parent/reactions/7",
                            creator = "users/alice",
                            reactionType = request.reaction.reactionType,
                        )
                    )
                    ApiResponse.Success(current.single())
                }
                "deleteMemoReaction" -> {
                    deletes += 1
                    assertEquals("parent", args[0])
                    assertEquals("7", args[1])
                    current = emptyList()
                    ApiResponse.Success(Unit)
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        }, account)

        val changed = repository.toggleMemoReaction("memos/parent", "❤️")
        assertTrue(changed is ApiResponse.Success)
        assertEquals("❤️", (changed as ApiResponse.Success).data.single().reactionType)
        assertTrue(changed.data.single().mine)
        assertEquals(1, upserts)

        val removed = repository.toggleMemoReaction("memos/parent", "❤️")
        assertTrue(removed is ApiResponse.Success)
        assertTrue((removed as ApiResponse.Success).data.isEmpty())
        assertEquals(1, deletes)
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeApi(
        handler: (method: Method, arguments: Array<out Any?>) -> Any?,
    ): MemosV1Api = Proxy.newProxyInstance(
        MemosV1Api::class.java.classLoader,
        arrayOf(MemosV1Api::class.java),
    ) { _, method, arguments ->
        handler(method, arguments ?: emptyArray())
    } as MemosV1Api
}
