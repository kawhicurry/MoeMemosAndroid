package me.mudkip.moememos.data.repository

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.StatusCode
import com.skydoves.sandwich.mapSuccess
import com.skydoves.sandwich.retrofit.statusCode
import me.mudkip.moememos.data.api.ListResourceResponse
import me.mudkip.moememos.data.api.MemosV1Resource
import me.mudkip.moememos.data.api.MemosV1UploadAttachment
import me.mudkip.moememos.data.api.MemosV1UploadAttachmentRequest
import me.mudkip.moememos.data.api.MemosV1UploadAttachmentResponse
import me.mudkip.moememos.data.api.MemosV1UploadAttachmentSpec
import java.io.EOFException
import java.io.InputStream
import java.util.Base64

internal const val MEMOS_V1_PAGE_SIZE = 200
// The server advertises a 2 MiB maximum. Keep requests smaller because the public EdgeOne
// path has rejected multi-megabyte JSON bodies even after the origin Nginx limit was raised.
internal const val MEMOS_V1_DEFAULT_UPLOAD_CHUNK_SIZE = 256 * 1024
private const val MEMOS_V1_UPLOAD_ATTEMPTS = 3

private val memosObjectIdPattern =
    Regex("^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,34}[a-zA-Z0-9])?$")

/** Returns a client ID accepted by Memos, or null when the local identifier is not portable. */
internal fun stableMemosObjectId(localIdentifier: String): String? =
    localIdentifier.takeIf(memosObjectIdPattern::matches)

internal fun isChunkUploadUnavailable(response: ApiResponse<*>): Boolean =
    response is ApiResponse.Failure.Error && response.statusCode in setOf(
        StatusCode.NotFound,
        StatusCode.MethodNotAllowed,
        StatusCode.NotImplemented,
    )

/** Loads every attachment page; ListAttachments defaults to only 50 without explicit paging. */
internal suspend fun listAllMemosV1Resources(
    loadPage: suspend (pageSize: Int, pageToken: String?) -> ApiResponse<ListResourceResponse>,
): ApiResponse<List<MemosV1Resource>> {
    val resources = mutableListOf<MemosV1Resource>()
    var pageToken: String? = null
    do {
        val response = loadPage(MEMOS_V1_PAGE_SIZE, pageToken)
        if (response !is ApiResponse.Success) {
            return response.mapSuccess { emptyList() }
        }
        resources += response.data.attachments
        pageToken = response.data.nextPageToken?.takeIf(String::isNotEmpty)
    } while (pageToken != null)
    return ApiResponse.Success(resources)
}

/**
 * Implements the Memos 0.31 bounded unary upload protocol. Initialization never carries bytes,
 * and an identical chunk is retried after transport failures, which the server treats idempotently.
 */
internal class MemosV1ChunkUploader(
    private val upload: suspend (MemosV1UploadAttachmentRequest) -> ApiResponse<MemosV1UploadAttachmentResponse>,
) {
    suspend fun upload(
        filename: String,
        type: String,
        totalSize: Long,
        openInputStream: () -> InputStream,
        memo: String?,
        attachmentId: String?,
    ): ApiResponse<MemosV1Resource> {
        if (totalSize < 0L) {
            return ApiResponse.Failure.Exception(IllegalArgumentException("Attachment size must be non-negative"))
        }

        val initialRequest = MemosV1UploadAttachmentRequest(
            spec = MemosV1UploadAttachmentSpec(
                attachment = MemosV1UploadAttachment(
                    filename = filename,
                    type = type,
                    memo = memo,
                ),
                attachmentId = attachmentId,
                totalSize = totalSize.toString(),
            ),
            writeOffset = "0",
        )
        val initial = upload(initialRequest)
        if (initial !is ApiResponse.Success) {
            return initial.mapSuccess {
                throw IllegalStateException("Unreachable success mapping")
            }
        }

        val uploadId = initial.data.uploadId.takeIf(String::isNotBlank)
            ?: return invalidUploadResponse("missing uploadId")
        val initialOffset = initial.data.committedSize.toLongOrNull()
            ?: return invalidUploadResponse("invalid initial committedSize")
        if (initialOffset != 0L || initial.data.maxChunkSize <= 0) {
            return invalidUploadResponse("unexpected initialization state")
        }
        val chunkSize = minOf(initial.data.maxChunkSize, MEMOS_V1_DEFAULT_UPLOAD_CHUNK_SIZE)

        openInputStream().use { input ->
            var offset = 0L
            var firstWrite = true
            while (firstWrite || offset < totalSize) {
                firstWrite = false
                val bytesToRead = minOf(chunkSize.toLong(), totalSize - offset).toInt()
                val bytes = try {
                    input.readExactly(bytesToRead)
                } catch (error: Exception) {
                    return ApiResponse.Failure.Exception(error)
                }
                val end = offset + bytes.size
                val request = MemosV1UploadAttachmentRequest(
                    uploadId = uploadId,
                    writeOffset = offset.toString(),
                    data = Base64.getEncoder().encodeToString(bytes),
                    finishWrite = end == totalSize,
                )
                val response = uploadChunkWithRetry(request)
                if (response !is ApiResponse.Success) {
                    return response.mapSuccess {
                        throw IllegalStateException("Unreachable success mapping")
                    }
                }
                if (response.data.committedSize.toLongOrNull() != end) {
                    return invalidUploadResponse("unexpected committedSize")
                }
                if (request.finishWrite) {
                    return response.data.attachment?.let { ApiResponse.Success(it) }
                        ?: invalidUploadResponse("completed without attachment")
                }
                offset = end
            }
        }
        return invalidUploadResponse("upload ended before finalization")
    }

    private suspend fun uploadChunkWithRetry(
        request: MemosV1UploadAttachmentRequest,
    ): ApiResponse<MemosV1UploadAttachmentResponse> {
        var response: ApiResponse<MemosV1UploadAttachmentResponse>
        repeat(MEMOS_V1_UPLOAD_ATTEMPTS) { attempt ->
            response = upload(request)
            if (response is ApiResponse.Success || !response.isRetryableUploadFailure() || attempt == MEMOS_V1_UPLOAD_ATTEMPTS - 1) {
                return response
            }
        }
        error("Upload retry loop did not return")
    }

    private fun ApiResponse<*>.isRetryableUploadFailure(): Boolean = when (this) {
        is ApiResponse.Failure.Exception -> true
        is ApiResponse.Failure.Error -> statusCode in setOf(
            StatusCode.BadGateway,
            StatusCode.ServiceUnavailable,
            StatusCode.GatewayTimeout,
        )
        is ApiResponse.Success -> false
    }

    private fun invalidUploadResponse(message: String): ApiResponse<MemosV1Resource> =
        ApiResponse.Failure.Exception(IllegalStateException("Invalid attachment upload response: $message"))
}

private fun InputStream.readExactly(size: Int): ByteArray {
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val count = read(result, offset, size - offset)
        if (count < 0) {
            throw EOFException("Attachment ended at $offset bytes; expected $size")
        }
        if (count == 0) {
            val single = read()
            if (single < 0) {
                throw EOFException("Attachment ended at $offset bytes; expected $size")
            }
            result[offset++] = single.toByte()
        } else {
            offset += count
        }
    }
    return result
}
