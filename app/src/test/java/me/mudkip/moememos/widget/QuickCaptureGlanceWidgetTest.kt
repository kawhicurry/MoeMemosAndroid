package me.mudkip.moememos.widget

import androidx.glance.appwidget.SizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCaptureGlanceWidgetTest {
    @Test
    fun widgetRecomposesForItsExactLauncherSize() {
        assertEquals(SizeMode.Exact, QuickCaptureGlanceWidget().sizeMode)
    }

    @Test
    fun minimumResizeHeightUsesCompactLayoutThatFits() {
        val layout = quickCaptureLayoutSpec(heightDp = 80f)

        assertEquals(QuickCaptureLayoutVariant.COMPACT, layout.variant)
        assertTrue(layout.estimatedHeightDp <= 80)
    }

    @Test
    fun twoRowLauncherHeightUsesRegularLayoutThatFits() {
        val layout = quickCaptureLayoutSpec(heightDp = 144f)

        assertEquals(QuickCaptureLayoutVariant.REGULAR, layout.variant)
        assertTrue(layout.estimatedHeightDp <= 144)
    }

    @Test
    fun layoutSwitchesOnlyWhenRegularContentFits() {
        assertEquals(
            QuickCaptureLayoutVariant.COMPACT,
            quickCaptureLayoutSpec(heightDp = 115f).variant,
        )
        assertEquals(
            QuickCaptureLayoutVariant.REGULAR,
            quickCaptureLayoutSpec(heightDp = 116f).variant,
        )
    }
}
