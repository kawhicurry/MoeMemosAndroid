package me.mudkip.moememos.ui.page.memoinput

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemoVisibility
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceInputUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun activeServiceRecognitionExposesCancelAndPreventsConcurrentRecording() {
        var voiceActions = 0
        compose.setContent {
            MaterialTheme {
                MemoInputBottomBar(
                    currentAccount = Account.Local(),
                    currentVisibility = MemoVisibility.PRIVATE,
                    showSpaceVisibility = false,
                    visibilityMenuExpanded = false,
                    onVisibilityExpandedChange = {},
                    onVisibilitySelected = {},
                    tags = emptyList(),
                    tagMenuExpanded = false,
                    onTagExpandedChange = {},
                    onHashTagClick = {},
                    onTagSelected = {},
                    onToggleTodoItem = {},
                    isRecording = false,
                    isSpeechRecognizing = true,
                    onVoiceInput = { voiceActions += 1 },
                    onToggleRecording = {},
                    onCancelRecording = {},
                    onPickMedia = {},
                    onPickAttachment = {},
                    onTakePhoto = {},
                    onFormat = {},
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.cancel_voice_input))
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.record_audio))
            .assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, voiceActions) }
    }
}
