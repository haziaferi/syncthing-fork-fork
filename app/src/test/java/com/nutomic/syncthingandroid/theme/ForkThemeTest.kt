package com.nutomic.syncthingandroid.theme

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    // SPEC:ui-design:R04
    @Test
    @Config(sdk = [31])
    fun sourceFor_xmlHosted_staysBrand() {
        assertThat(sourceFor(useWallpaper = true, allowWallpaper = false, sdkInt = 31)).isEqualTo(ColourSource.BRAND)
        assertThat(sourceFor(useWallpaper = true, allowWallpaper = true, sdkInt = 31)).isEqualTo(ColourSource.WALLPAPER)
        assertThat(sourceFor(useWallpaper = false, allowWallpaper = true, sdkInt = 31)).isEqualTo(ColourSource.BRAND)
        assertThat(sourceFor(useWallpaper = true, allowWallpaper = true, sdkInt = 30)).isEqualTo(ColourSource.BRAND)
    }

    /** Composes ForkTheme in an activity and returns the scheme and status colours it provides. */
    private fun composeTheme(allowWallpaper: Boolean): Pair<ColorScheme, StatusColors> {
        var seen: Pair<ColorScheme, StatusColors>? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            ForkTheme(allowWallpaper = allowWallpaper) {
                seen = MaterialTheme.colorScheme to LocalStatusColors.current
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        return checkNotNull(seen)
    }

    // SPEC:ui-design:R04
    @Test
    @Config(sdk = [31])
    fun forkTheme_switchOn_wallpaperOnlyWhereAllowed() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(UiPrefs.KEY_USE_WALLPAPER_COLOURS, true).commit()
        try {
            val (xmlHosted, xmlStatus) = composeTheme(allowWallpaper = false)
            assertThat(xmlHosted).isEqualTo(BrandScheme.light)
            assertThat(xmlStatus).isEqualTo(StatusColors.brand(false))
            val (compose, _) = composeTheme(allowWallpaper = true)
            assertThat(compose).isNotEqualTo(BrandScheme.light)
            assertThat(compose).isEqualTo(schemeFor(context, ColourSource.WALLPAPER, false))
        } finally {
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        }
    }

    @Test
    fun readPref_wrongType_isDefault() {
        val prefs = context.getSharedPreferences("pref_state_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("k", "x").commit()
        assertThat(readPref(prefs, "k", false) { it as? Boolean }).isFalse()
        assertThat(readPref(prefs, "k", "d") { it as? String }).isEqualTo("x")
        assertThat(readPref(prefs, "missing", 7) { it as? Int }).isEqualTo(7)
    }
}
