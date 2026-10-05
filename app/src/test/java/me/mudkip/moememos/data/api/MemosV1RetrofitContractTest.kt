package me.mudkip.moememos.data.api

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant

class MemosV1RetrofitContractTest {
    @Test
    fun updateAndChunkUploadProduceMemos031HttpRequests() = runTest {
        val captured = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                captured += request
                val responseJson = if (request.url.encodedPath.endsWith(":upload")) {
                    """{"uploadId":"upload-1","committedSize":"0","maxChunkSize":2097152}"""
                } else {
                    """{"name":"memos/note-id","content":"changed","tags":["tag"]}"""
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
        val api = Retrofit.Builder()
            .baseUrl("https://example.test/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()
            .create(MemosV1Api::class.java)

        val update = UpdateMemoRequest(content = "changed", updateTime = Instant.EPOCH)
        val updateResponse = api.updateMemo("note-id", update.updateMask(), update)
        assertTrue(updateResponse is ApiResponse.Success)

        val uploadResponse = api.uploadResource(
            MemosV1UploadAttachmentRequest(
                spec = MemosV1UploadAttachmentSpec(
                    attachment = MemosV1UploadAttachment(
                        filename = "clip.mp4",
                        type = "video/mp4",
                        memo = "memos/note-id",
                    ),
                    attachmentId = "attachment-id",
                    totalSize = "6",
                ),
                writeOffset = "0",
            )
        )
        assertTrue(uploadResponse is ApiResponse.Success)

        val updateRequest = captured[0]
        assertEquals("PATCH", updateRequest.method)
        assertEquals("/api/v1/memos/note-id", updateRequest.url.encodedPath)
        assertEquals("content,update_time", updateRequest.url.queryParameter("updateMask"))
        assertEquals(
            """{"content":"changed","updateTime":"1970-01-01T00:00:00Z"}""",
            updateRequest.bodyUtf8(),
        )

        val uploadRequest = captured[1]
        assertEquals("POST", uploadRequest.method)
        assertEquals("/api/v1/attachments:upload", uploadRequest.url.encodedPath)
        val uploadBody = uploadRequest.bodyUtf8()
        assertTrue(uploadBody.contains("\"attachmentId\":\"attachment-id\""))
        assertTrue(uploadBody.contains("\"totalSize\":\"6\""))
        assertTrue(uploadBody.contains("\"memo\":\"memos/note-id\""))
    }

    private fun Request.bodyUtf8(): String = Buffer().also { buffer ->
        requireNotNull(body).writeTo(buffer)
    }.readUtf8()
}
