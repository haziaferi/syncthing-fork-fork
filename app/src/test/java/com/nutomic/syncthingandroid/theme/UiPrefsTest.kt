package com.nutomic.syncthingandroid.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class UiPrefsTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("ui_prefs_test", Context.MODE_PRIVATE)

    @Test
    fun density_unknownValue_isStandard() {
        prefs.edit().clear().commit()
        assertThat(UiPrefs.density(prefs)).isEqualTo(UiDensity.STANDARD)
        prefs.edit().putString(UiPrefs.KEY_UI_DENSITY, "huge").commit()
        assertThat(UiPrefs.density(prefs)).isEqualTo(UiDensity.STANDARD)
        prefs.edit().putString(UiPrefs.KEY_UI_DENSITY, "compact").commit()
        assertThat(UiPrefs.density(prefs)).isEqualTo(UiDensity.COMPACT)
    }

    @Test
    fun density_wrongType_isStandard() {
        prefs.edit().clear().putInt(UiPrefs.KEY_UI_DENSITY, 3).commit()
        assertThat(UiPrefs.density(prefs)).isEqualTo(UiDensity.STANDARD)
    }

    @Test
    fun wallpaper_wrongType_isOff() {
        prefs.edit().clear().putString(UiPrefs.KEY_USE_WALLPAPER_COLOURS, "true").commit()
        assertThat(UiPrefs.useWallpaper(prefs, 31)).isFalse()
    }

    // SPEC:ui-design:R04
    @Test
    fun wallpaper_belowApi31_isOff() {
        prefs.edit().clear().commit()
        assertThat(UiPrefs.useWallpaper(prefs, 31)).isFalse()
        prefs.edit().putBoolean(UiPrefs.KEY_USE_WALLPAPER_COLOURS, true).commit()
        assertThat(UiPrefs.useWallpaper(prefs, 30)).isFalse()
        assertThat(UiPrefs.useWallpaper(prefs, 31)).isTrue()
    }
}
