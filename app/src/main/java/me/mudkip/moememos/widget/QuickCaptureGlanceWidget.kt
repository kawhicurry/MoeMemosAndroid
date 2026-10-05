package me.mudkip.moememos.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import me.mudkip.moememos.MainActivity
import me.mudkip.moememos.R

/** A launcher-sized capture surface for the current local or remote account. */
class QuickCaptureGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        provideContent {
            GlanceTheme {
                QuickCaptureContent(context)
            }
        }
    }
}

@Composable
private fun QuickCaptureContent(context: Context) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(14.dp),
    ) {
        Text(
            text = context.getString(R.string.quick_capture_widget_title),
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(10.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickCaptureButton(
                context = context,
                mode = MainActivity.CAPTURE_MODE_TEXT,
                icon = R.drawable.ic_shortcut_add,
                label = context.getString(R.string.compose),
                modifier = GlanceModifier.defaultWeight(),
            )
            QuickCaptureButton(
                context = context,
                mode = MainActivity.CAPTURE_MODE_VOICE,
                icon = R.drawable.ic_shortcut_voice,
                label = context.getString(R.string.voice_input),
                modifier = GlanceModifier.defaultWeight(),
            )
            QuickCaptureButton(
                context = context,
                mode = MainActivity.CAPTURE_MODE_CAMERA,
                icon = R.drawable.ic_shortcut_camera,
                label = context.getString(R.string.take_photo),
                modifier = GlanceModifier.defaultWeight(),
            )
            QuickCaptureButton(
                context = context,
                mode = MainActivity.CAPTURE_MODE_MEDIA,
                icon = R.drawable.ic_shortcut_media,
                label = context.getString(R.string.add_media),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun QuickCaptureButton(
    context: Context,
    mode: String,
    icon: Int,
    label: String,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier
            .clickable(actionStartActivity(createQuickCaptureIntent(context, mode)))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = label,
            modifier = GlanceModifier.size(34.dp),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

internal fun createQuickCaptureIntent(context: Context, mode: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_QUICK_CAPTURE
        putExtra(MainActivity.EXTRA_CAPTURE_MODE, mode)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
