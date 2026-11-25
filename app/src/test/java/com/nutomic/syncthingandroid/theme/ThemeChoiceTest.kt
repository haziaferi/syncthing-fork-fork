package com.nutomic.syncthingandroid.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeChoiceTest {

    // SPEC:ui-design:R04
    @Test
    fun source_defaultIsBrand() {
        assertThat(ThemeChoice.source(useWallpaper = false, sdkInt = 30)).isEqualTo(ColourSource.BRAND)
        assertThat(ThemeChoice.source(useWallpaper = false, sdkInt = 31)).isEqualTo(ColourSource.BRAND)
        assertThat(ThemeChoice.source(useWallpaper = true, sdkInt = 30)).isEqualTo(ColourSource.BRAND)
        assertThat(ThemeChoice.source(useWallpaper = true, sdkInt = 31)).isEqualTo(ColourSource.WALLPAPER)
    }
}
