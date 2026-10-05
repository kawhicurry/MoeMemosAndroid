package me.mudkip.moememos.ui.page.memoinput

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputTest {
    @Test
    fun insertsAtCursorAndPreservesSurroundingText() {
        val result = insertSpeechResult(
            TextFieldValue("morning evening", TextRange(7)),
            "sunny day",
        )

        assertEquals("morning sunny day evening", result.text)
        assertEquals(TextRange(18), result.selection)
    }

    @Test
    fun replacesSelectionAndLeavesBlankResultUntouched() {
        val original = TextFieldValue("old draft", TextRange(0, 3))
        val replaced = insertSpeechResult(original, "new")

        assertEquals("new draft", replaced.text)
        assertEquals(TextRange(3), replaced.selection)
        assertEquals(original, insertSpeechResult(original, "   "))
    }
}
