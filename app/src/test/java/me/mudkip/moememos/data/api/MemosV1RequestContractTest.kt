package me.mudkip.moememos.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import java.time.Instant

class MemosV1RequestContractTest {
    private val networkJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun retrofitRoutesExposeRequired031Queries() {
        val update = apiMethod("updateMemo")
        assertEquals(
            "api/v1/memos/{id}",
            requireNotNull(update.getAnnotation(PATCH::class.java)).value,
        )
        assertTrue(update.queryNames().contains("updateMask"))

        assertTrue(apiMethod("createMemo").queryNames().contains("memoId"))
        assertTrue(apiMethod("createResource").queryNames().contains("attachmentId"))
        assertEquals(
            "api/v1/attachments:upload",
            requireNotNull(apiMethod("uploadResource").getAnnotation(POST::class.java)).value,
        )
        assertEquals(
            "api/v1/memos/{id}/comments",
            requireNotNull(apiMethod("createMemoComment").getAnnotation(POST::class.java)).value,
        )
        assertEquals(
            "api/v1/memos/{id}/reactions",
            requireNotNull(apiMethod("upsertMemoReaction").getAnnotation(POST::class.java)).value,
        )
    }

    @Test
    fun updateMaskUsesOnlyPresentFieldsAndProtoNames() {
        assertEquals("", UpdateMemoRequest().updateMask())

        val update = UpdateMemoRequest(
            content = "",
            visibility = MemosVisibility.SPACE,
            state = MemosV1State.NORMAL,
            pinned = false,
            updateTime = Instant.EPOCH,
            attachments = emptyList(),
        )
        assertEquals(
            "content,visibility,state,pinned,update_time,attachments",
            update.updateMask(),
        )
    }

    @Test
    fun chunkUploadUsesProtoJsonStringsBase64AndStableAssociation() {
        val initial = MemosV1UploadAttachmentRequest(
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
        val initialJson = networkJson.parseToJsonElement(
            networkJson.encodeToString(initial),
        ).jsonObject
        val spec = initialJson.getValue("spec").jsonObject
        assertEquals("6", spec.getValue("totalSize").jsonPrimitive.content)
        assertTrue(spec.getValue("totalSize").jsonPrimitive.isString)
        assertEquals("attachment-id", spec.getValue("attachmentId").jsonPrimitive.content)
        assertEquals(
            "memos/note-id",
            spec.getValue("attachment").jsonObject.getValue("memo").jsonPrimitive.content,
        )

        val chunk = MemosV1UploadAttachmentRequest(
            uploadId = "upload-id",
            writeOffset = "3",
            data = "ZGVm",
            finishWrite = true,
        )
        val chunkJson = networkJson.parseToJsonElement(
            networkJson.encodeToString(chunk),
        ).jsonObject
        assertEquals("3", chunkJson.getValue("writeOffset").jsonPrimitive.content)
        assertEquals("ZGVm", chunkJson.getValue("data").jsonPrimitive.content)
        assertEquals("true", chunkJson.getValue("finishWrite").jsonPrimitive.content)
    }

    @Test
    fun memoAttachmentBodyCarriesNamesAndEmptyListMeansClear() {
        val create = MemosV1CreateMemoRequest(
            content = "mixed media",
            visibility = MemosVisibility.SPACE,
            attachments = listOf(MemosV1Resource(name = "attachments/image-id")),
            space = "spaces/team",
        )
        val createJson = networkJson.parseToJsonElement(
            networkJson.encodeToString(create),
        ).jsonObject
        assertEquals(
            "attachments/image-id",
            createJson.getValue("attachments").jsonArray.single().jsonObject
                .getValue("name").jsonPrimitive.content,
        )
        assertEquals("spaces/team", createJson.getValue("space").jsonPrimitive.content)

        val clear = UpdateMemoRequest(attachments = emptyList())
        val clearJson = networkJson.parseToJsonElement(
            networkJson.encodeToString(clear),
        ).jsonObject
        assertEquals("attachments", clear.updateMask())
        assertTrue(clearJson.getValue("attachments").jsonArray.isEmpty())
    }

    @Test
    fun reactionRequestWrapsServerReactionObject() {
        val request = MemosV1UpsertReactionRequest(
            reaction = MemosV1Reaction(reactionType = "👍"),
        )
        val encoded = networkJson.parseToJsonElement(
            networkJson.encodeToString(request),
        ).jsonObject
        assertEquals(
            "👍",
            encoded.getValue("reaction").jsonObject.getValue("reactionType").jsonPrimitive.content,
        )
    }

    private fun apiMethod(name: String) =
        MemosV1Api::class.java.declaredMethods.single { it.name == name }

    private fun java.lang.reflect.Method.queryNames(): List<String> =
        parameterAnnotations.flatMap { annotations ->
            annotations.filterIsInstance<Query>().map(Query::value)
        }
}
