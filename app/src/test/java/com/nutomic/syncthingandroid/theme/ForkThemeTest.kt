package com.nutomic.syncthingandroid.theme

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class ForkThemeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // SPEC:ui-design:R04
    @Test
    fun schemeFor_brandAndWallpaper() {
        assertThat(schemeFor(context, ColourSource.BRAND, false)).isEqualTo(BrandScheme.light)
        assertThat(schemeFor(context, ColourSource.BRAND, true)).isEqualTo(BrandScheme.dark)
        for (dark in listOf(false, true)) {
            val s = schemeFor(context, ColourSource.WALLPAPER, dark)
            assertThat(Contrast.ratio(s.onSurfaceVariant.toArgb(), s.surface.toArgb())).isAtLeast(7.0)
        }
    }

    // SPEC:ui-design:R04
    @Test
    @Config(sdk = [31])
    fun schemeFor_wallpaperAtApi31() {
        val s = schemeFor(context, ColourSource.WALLPAPER, false)
        assertThat(Contrast.ratio(s.onSurfaceVariant.toArgb(), s.surface.toArgb())).isAtLeast(7.0)
    }
}
