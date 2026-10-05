package me.mudkip.moememos.data.model

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

data class ShareContent(
    val text: String = "",
    val attachments: List<Uri> = emptyList(),
) {
    /** Compatibility name for callers compiled against the former image-only model. */
    val images: List<Uri>
        get() = attachments

    companion object {
        fun parseIntent(intent: Intent): ShareContent {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val attachments = LinkedHashSet<Uri>()

            when (intent.action) {
                Intent.ACTION_SEND -> {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                        attachments.add(it)
                    }
                }

                Intent.ACTION_SEND_MULTIPLE -> {
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                        attachments.addAll(it.filterIsInstance<Uri>())
                    }
                }
            }

            intent.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let(attachments::add)
                }
            }

            return ShareContent(text ?: "", attachments.toList())
        }
    }
}
