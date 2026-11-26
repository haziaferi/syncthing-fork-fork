package com.nutomic.syncthingandroid.theme

import android.app.UiModeManager
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class WallpaperSchemeTest {

    private val seeds = listOf(0xff0f8ecc.toInt(), 0xffb3261e.toInt(), 0xff808080.toInt(), 0xff2e7d32.toInt())

    // SPEC:ui-design:R04
    @Test
    fun levelHalf_reachesSevenToOne() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            val s = WallpaperScheme.colorScheme(seed, dark, 0.5)
            val surface = s.surface.toArgb()
            val tag = "seed=${Integer.toHexString(seed)} dark=$dark"
            assertWithMessage("onSurfaceVariant $tag").that(Contrast.ratio(s.onSurfaceVariant.toArgb(), surface)).isAtLeast(7.0)
            assertWithMessage("primary $tag").that(Contrast.ratio(s.primary.toArgb(), surface)).isAtLeast(7.0)
            assertWithMessage("error $tag").that(Contrast.ratio(s.error.toArgb(), surface)).isAtLeast(7.0)
            assertWithMessage("onSurface $tag").that(Contrast.ratio(s.onSurface.toArgb(), surface)).isAtLeast(11.0)
        }
    }

    @Test
    fun warningIcon_staysThreeToOne() {
        for (seed in seeds) for (dark in listOf(false, true)) for (level in listOf(0.5, 1.0)) {
            val s = WallpaperScheme.colorScheme(seed, dark, level)
            val w = StatusColors.brand(dark).warning.toArgb()
            assertWithMessage("warning seed=${Integer.toHexString(seed)} dark=$dark level=$level")
                .that(Contrast.ratio(w, s.surface.toArgb()))
                .isAtLeast(3.0)
        }
    }

    private fun levelFor(contrast: Float): Double {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(UiModeManager::class.java)).setContrast(contrast)
        return WallpaperScheme.contrastLevel(context)
    }

    @Test
    @Config(sdk = [34])
    fun contrastLevel_followsSystemHighOnly() {
        assertThat(levelFor(1.0f)).isEqualTo(1.0)
        assertThat(levelFor(0.5f)).isEqualTo(0.5)
        assertThat(levelFor(0f)).isEqualTo(0.5)
    }

    // SPEC:ui-design:R04
    @Test
    @Config(sdk = [33])
    fun contrastLevel_beforeApi34_isHalf() {
        // Android 12 and 13 have no system contrast setting, so the level is always 0.5.
        assertThat(WallpaperScheme.contrastLevel(ApplicationProvider.getApplicationContext())).isEqualTo(0.5)
    }

    @Test
    fun colorScheme_sameInputs_reuseTheScheme() {
        val first = WallpaperScheme.colorScheme(seeds[0], false, 0.5)
        assertThat(WallpaperScheme.colorScheme(seeds[0], false, 0.5)).isSameInstanceAs(first)
        assertThat(WallpaperScheme.colorScheme(seeds[0], true, 0.5)).isNotSameInstanceAs(first)
        assertThat(WallpaperScheme.colorScheme(seeds[0], false, 1.0)).isNotSameInstanceAs(first)
        assertThat(WallpaperScheme.colorScheme(seeds[1], false, 0.5)).isNotSameInstanceAs(first)
    }
}
