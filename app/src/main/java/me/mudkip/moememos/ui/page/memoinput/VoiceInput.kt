package me.mudkip.moememos.ui.page.memoinput

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Inserts recognized speech at the current selection without destroying text. */
internal fun insertSpeechResult(
    value: TextFieldValue,
    recognizedText: String,
): TextFieldValue {
    val spoken = recognizedText.trim()
    if (spoken.isEmpty()) return value

    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(start, value.text.length)
    val before = value.text.substring(0, start)
    val after = value.text.substring(end)
    val prefix = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
    val suffix = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
    val insertion = prefix + spoken + suffix
    val updated = before + insertion + after
    // When the cursor was immediately before an existing separator, place it
    // after that separator so continued dictation does not split the sentence.
    val trailingSeparator = if (prefix.isNotEmpty() && suffix.isEmpty() && after.firstOrNull()?.isWhitespace() == true) 1 else 0
    return TextFieldValue(
        text = updated,
        selection = TextRange(before.length + insertion.length + trailingSeparator),
    )
}
