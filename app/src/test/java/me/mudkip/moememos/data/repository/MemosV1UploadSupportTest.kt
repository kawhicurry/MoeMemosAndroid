package me.mudkip.moememos.data.repository

import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.test.runTest
import me.mudkip.moememos.data.api.ListResourceResponse
import me.mudkip.moememos.data.api.MemosV1Resource
import me.mudkip.moememos.data.api.MemosV1UploadAttachmentRequest
import me.mudkip.moememos.data.api.MemosV1UploadAttachmentResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Base64
import retrofit2.Response

class MemosV1UploadSupportTest {
    @Test
    fun retriesIdenticalChunkWithoutRestartingUpload() = runTest {
        val requests = mutableListOf<MemosV1UploadAttachmentRequest>()
        var firstChunkAttempts = 0
        val uploader = MemosV1ChunkUploader { request ->
            requests += request
            when {
                request.spec != null -> ApiResponse.Success(
                    MemosV1UploadAttachmentResponse(
                        uploadId = "upload-1",
                        committedSize = "0",
                        maxChunkSize = 3,
                    )
                )
                request.writeOffset == "0" && firstChunkAttempts++ == 0 ->
                    ApiResponse.Failure.Exception(IOException("response lost"))
                request.writeOffset == "0" -> ApiResponse.Success(
                    MemosV1UploadAttachmentResponse(
                        uploadId = "upload-1",
                        committedSize = "3",
                        maxChunkSize = 3,
                    )
                )
                else -> ApiResponse.Success(
                    MemosV1UploadAttachmentResponse(
                        uploadId = "upload-1",
                        committedSize = "6",
                        attachment = MemosV1Resource(
                            name = "attachments/attachment-id",
                            filename = "clip.bin",
                            type = "application/octet-stream",
                            size = "6",
                        ),
                        maxChunkSize = 3,
                    )
                )
            }
        }

        val result = uploader.upload(
            filename = "clip.bin",
            type = "application/octet-stream",
            totalSize = 6,
            openInputStream = { ByteArrayInputStream("abcdef".encodeToByteArray()) },
            memo = "memos/note-id",
            attachmentId = "attachment-id",
        )

        assertTrue(result is ApiResponse.Success)
        assertEquals("attachments/attachment-id", (result as ApiResponse.Success).data.name)
        assertEquals(1, requests.count { it.spec != null })
        assertEquals(requests[1], requests[2])
        assertEquals("abc", decode(requests[1].data))
        assertEquals("def", decode(requests[3].data))
        assertTrue(requests[3].finishWrite)
        assertEquals("memos/note-id", requests.first().spec?.attachment?.memo)
        assertEquals("attachment-id", requests.first().spec?.attachmentId)
    }

    @Test
    fun paginatesAttachmentsUntilTokenIsAbsent() = runTest {
        val seenTokens = mutableListOf<String?>()
        val result = listAllMemosV1Resources { pageSize, token ->
            assertEquals(MEMOS_V1_PAGE_SIZE, pageSize)
            seenTokens += token
            if (token == null) {
                ApiResponse.Success(
                    ListResourceResponse(
                        attachments = listOf(MemosV1Resource(name = "attachments/one")),
                        nextPageToken = "next",
                    )
                )
            } else {
                ApiResponse.Success(
                    ListResourceResponse(
                        attachments = listOf(MemosV1Resource(name = "attachments/two")),
                    )
                )
            }
        }

        assertTrue(result is ApiResponse.Success)
        assertEquals(
            listOf("attachments/one", "attachments/two"),
            (result as ApiResponse.Success).data.map { it.name },
        )
        assertEquals(listOf(null, "next"), seenTokens)
    }

    @Test
    fun capsChunksBelowServerMaximumForThePublicProxyPath() = runTest {
        val chunkSizes = mutableListOf<Int>()
        val totalSize = MEMOS_V1_DEFAULT_UPLOAD_CHUNK_SIZE + 1
        val uploader = MemosV1ChunkUploader { request ->
            if (request.spec != null) {
                ApiResponse.Success(
                    MemosV1UploadAttachmentResponse(
                        uploadId = "upload-1",
                        committedSize = "0",
                        maxChunkSize = 2 * 1024 * 1024,
                    )
                )
            } else {
                val bytes = Base64.getDecoder().decode(requireNotNull(request.data))
                chunkSizes += bytes.size
                val committed = request.writeOffset.toLong() + bytes.size
                ApiResponse.Success(
                    MemosV1UploadAttachmentResponse(
                        uploadId = "upload-1",
                        committedSize = committed.toString(),
                        attachment = if (request.finishWrite) {
                            MemosV1Resource(name = "attachments/capped")
                        } else {
                            null
                        },
                        maxChunkSize = 2 * 1024 * 1024,
                    )
                )
            }
        }

        val result = uploader.upload(
            filename = "large.bin",
            type = "application/octet-stream",
            totalSize = totalSize.toLong(),
            openInputStream = { ByteArrayInputStream(ByteArray(totalSize)) },
            memo = null,
            attachmentId = "capped",
        )

        assertTrue(result is ApiResponse.Success)
        assertEquals(listOf(MEMOS_V1_DEFAULT_UPLOAD_CHUNK_SIZE, 1), chunkSizes)
    }

    @Test
    fun stableIdsRecoverResponseLostCreatesWithoutCreatingAgain() = runTest {
        val stableId = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals(stableId, stableMemosObjectId(stableId))
        assertNull(stableMemosObjectId("not valid/for server"))

        var lookups = 0
        val recovered = recoverCreatedObject<MemosV1Resource>(
            response = ApiResponse.Failure.Exception(IOException("response lost")),
            stableId = stableId,
        ) { id ->
            lookups += 1
            ApiResponse.Success(MemosV1Resource(name = "attachments/$id"))
        }

        assertTrue(recovered is ApiResponse.Success)
        assertEquals("attachments/$stableId", (recovered as ApiResponse.Success).data.name)
        assertEquals(1, lookups)
    }

    @Test
    fun onlyMissingChunkEndpointFallsBackToLegacyUpload() {
        val notFound = ApiResponse.Failure.Error(
            Response.error<Unit>(404, "missing".toResponseBody())
        )
        val conflict = ApiResponse.Failure.Error(
            Response.error<Unit>(409, "already exists".toResponseBody())
        )

        assertTrue(isChunkUploadUnavailable(notFound))
        assertTrue(!isChunkUploadUnavailable(conflict))
    }

    private fun decode(value: String?): String =
        String(Base64.getDecoder().decode(requireNotNull(value)))
}
