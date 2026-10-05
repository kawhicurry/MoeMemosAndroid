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
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
    override val sizeMode: SizeMode = SizeMode.Exact

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
    val layout = quickCaptureLayoutSpec(LocalSize.current.height.value)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(
                horizontal = layout.horizontalPaddingDp.dp,
                vertical = layout.verticalPaddingDp.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.quick_capture_widget_title),
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = layout.titleFontSizeSp.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(layout.titleActionsGapDp.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickCaptureButton(
                context = context,
                mode = MainActivity.CAPTURE_MODE_TEXT,
                icon = R.drawable.ic_shortcut_add,
                label = context.getString(R.string.compose),
                layout = layout,
                modifier = GlanceModifier.defaultWeight(),
            )
            QuickCaptureButton(
                context = context,
                mode = MainActivity.CAPTURE_MODE_VOICE,
                icon = R.drawable.ic_shortcut_voice,
                label = context.getString(R.string.voice_input),
                layout = layout,
                modifier = GlanceModifier.defaultWeight(),
            )
            QuickCaptureButton(
                context = context,
                mode = MainActivity.CAPTURE_MODE_CAMERA,
                icon = R.drawable.ic_shortcut_camera,
                label = context.getString(R.string.take_photo),
                layout = layout,
                modifier = GlanceModifier.defaultWeight(),
            )
            QuickCaptureButton(
                context = context,
                mode = MainActivity.CAPTURE_MODE_MEDIA,
                icon = R.drawable.ic_shortcut_media,
                label = context.getString(R.string.add_media),
                layout = layout,
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
    layout: QuickCaptureLayoutSpec,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier
            .clickable(actionStartActivity(createQuickCaptureIntent(context, mode)))
            .padding(
                horizontal = layout.buttonHorizontalPaddingDp.dp,
                vertical = layout.buttonVerticalPaddingDp.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = label,
            modifier = GlanceModifier.size(layout.iconSizeDp.dp),
        )
        Spacer(GlanceModifier.height(layout.iconLabelGapDp.dp))
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = layout.labelFontSizeSp.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

internal enum class QuickCaptureLayoutVariant {
    COMPACT,
    REGULAR,
}

internal data class QuickCaptureLayoutSpec(
    val variant: QuickCaptureLayoutVariant,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val titleFontSizeSp: Int,
    val titleActionsGapDp: Int,
    val buttonHorizontalPaddingDp: Int,
    val buttonVerticalPaddingDp: Int,
    val iconSizeDp: Int,
    val iconLabelGapDp: Int,
    val labelFontSizeSp: Int,
    /** Conservative title + action row + outer-padding height used by unit tests. */
    val estimatedHeightDp: Int,
)

private const val REGULAR_LAYOUT_MIN_HEIGHT_DP = 116f

internal fun quickCaptureLayoutSpec(heightDp: Float): QuickCaptureLayoutSpec =
    if (heightDp < REGULAR_LAYOUT_MIN_HEIGHT_DP) {
        QuickCaptureLayoutSpec(
            variant = QuickCaptureLayoutVariant.COMPACT,
            horizontalPaddingDp = 8,
            verticalPaddingDp = 6,
            titleFontSizeSp = 14,
            titleActionsGapDp = 2,
            buttonHorizontalPaddingDp = 2,
            buttonVerticalPaddingDp = 2,
            iconSizeDp = 28,
            iconLabelGapDp = 2,
            labelFontSizeSp = 10,
            estimatedHeightDp = 77,
        )
    } else {
        QuickCaptureLayoutSpec(
            variant = QuickCaptureLayoutVariant.REGULAR,
            horizontalPaddingDp = 14,
            verticalPaddingDp = 10,
            titleFontSizeSp = 16,
            titleActionsGapDp = 8,
            buttonHorizontalPaddingDp = 4,
            buttonVerticalPaddingDp = 4,
            iconSizeDp = 34,
            iconLabelGapDp = 4,
            labelFontSizeSp = 11,
            estimatedHeightDp = 106,
        )
    }

internal fun createQuickCaptureIntent(context: Context, mode: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_QUICK_CAPTURE
        putExtra(MainActivity.EXTRA_CAPTURE_MODE, mode)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
