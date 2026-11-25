package com.nutomic.syncthingandroid.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.nutomic.syncthingandroid.theme.mcu.hct.Hct
import com.nutomic.syncthingandroid.theme.mcu.scheme.SchemeTonalSpot
import org.junit.Test

class BrandSchemeTest {

    private fun argb(c: Color) = c.toArgb()

    private fun ratios(s: ColorScheme, st: StatusColors) = listOf(
        Contrast.ratio(argb(s.onSurface), argb(s.surface)),
        Contrast.ratio(argb(s.onSurfaceVariant), argb(s.surface)),
        Contrast.ratio(argb(s.primary), argb(s.surface)),
        Contrast.ratio(argb(st.warning), argb(s.surface)),
        Contrast.ratio(argb(s.error), argb(s.surface)),
        Contrast.ratio(argb(st.success), argb(s.surface)),
    )

    // SPEC:ui-design:R05
    @Test
    fun rolesMatchSpec_withinTolerance() {
        val light = ratios(BrandScheme.light, StatusColors.brand(false))
        val dark = ratios(BrandScheme.dark, StatusColors.brand(true))
        val expectedLight = listOf(15.6, 7.0, 7.0, 3.0, 7.0, 5.1)
        val expectedDark = listOf(14.9, 7.0, 7.0, 11.4, 7.0, 3.6)
        for (i in expectedLight.indices) {
            assertThat(light[i]).isWithin(0.05).of(expectedLight[i])
            assertThat(dark[i]).isWithin(0.05).of(expectedDark[i])
        }
        // onPrimary on primary is not in the spec list; keep it readable too.
        assertThat(Contrast.ratio(argb(BrandScheme.light.onPrimary), argb(BrandScheme.light.primary)))
            .isAtLeast(7.0)
        assertThat(Contrast.ratio(argb(BrandScheme.dark.onPrimary), argb(BrandScheme.dark.primary)))
            .isAtLeast(7.0)
    }

    @Test
    fun constantsMatchGeneratedScheme() {
        for (dark in listOf(false, true)) {
            val generated = BrandSchemeGenerator.generate(dark)
            val actual = if (dark) BrandScheme.dark else BrandScheme.light
            assertThat(actual.roleMap()).isEqualTo(generated.roleMap())
        }
    }

    @Test
    fun generatedScheme_usesBrandSeedAtHalfContrast() {
        // Guards the generator itself: unpinned roles must come straight from SchemeTonalSpot.
        for (dark in listOf(false, true)) {
            val raw = SchemeTonalSpot(Hct.fromInt(0xff0f8ecc.toInt()), dark, 0.5)
            val gen = BrandSchemeGenerator.generate(dark)
            assertThat(gen.primaryContainer.toArgb())
                .isEqualTo(com.nutomic.syncthingandroid.theme.mcu.dynamiccolor.MaterialDynamicColors()
                    .primaryContainer().getArgb(raw))
        }
    }

    @Test
    fun statusColours_apartUnderColourVision() {
        for (dark in listOf(false, true)) {
            val s = if (dark) BrandScheme.dark else BrandScheme.light
            val st = StatusColors.brand(dark)
            val set = listOf(st.running, st.paused, st.warning, st.error, st.success).map { it.toArgb() }
            val names = listOf("running", "paused", "warning", "error", "success")
            for ((label, m) in listOf(
                "deuteranopia" to ColourVisionTestHelper.deuteranopia,
                "protanopia" to ColourVisionTestHelper.protanopia,
            )) {
                val (d, i, j) = ColourVisionTestHelper.worstPair(set, m)
                println("CVD dark=$dark $label worst=${names[i]}/${names[j]} dE=$d")
                assertThat(d).isAtLeast(10.0)
            }
            assertThat(st.running).isEqualTo(s.primary)
        }
    }
}
