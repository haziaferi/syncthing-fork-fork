package com.nutomic.syncthingandroid.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.nutomic.syncthingandroid.theme.mcu.dynamiccolor.MaterialDynamicColors
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
        val expectedLight = listOf(15.6, 7.0, 9.22, 3.0, 7.0, 5.1)
        val expectedDark = listOf(14.9, 7.0, 10.31, 11.4, 7.0, 3.6)
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

    /** Dialogs sit on surfaceContainerHigh; their text buttons are primary, small text, so 7:1 (R36). */
    // SPEC:ui-design:R05
    @Test
    fun primaryOnDialogBackground_atLeast7() {
        for ((mode, s) in listOf("light" to BrandScheme.light, "dark" to BrandScheme.dark)) {
            assertWithMessage("$mode primary on surfaceContainerHigh")
                .that(Contrast.ratio(argb(s.primary), argb(s.surfaceContainerHigh))).isAtLeast(7.0)
        }
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
            val c = MaterialDynamicColors()
            val direct = listOf(
                Triple("primaryContainer", gen.primaryContainer, c.primaryContainer()),
                Triple("surfaceTint", gen.surfaceTint, c.surfaceTint()),
                Triple("inversePrimary", gen.inversePrimary, c.inversePrimary()),
                Triple("surfaceContainerHigh", gen.surfaceContainerHigh, c.surfaceContainerHigh()),
                Triple("outlineVariant", gen.outlineVariant, c.outlineVariant()),
                Triple("onSecondaryFixedVariant", gen.onSecondaryFixedVariant, c.onSecondaryFixedVariant()),
            )
            for ((name, colour, dynamic) in direct) {
                assertWithMessage("$name dark=$dark").that(colour.toArgb()).isEqualTo(dynamic.getArgb(raw))
            }
        }
    }

    // SPEC:ui-design:R05
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
                assertWithMessage("CVD dark=$dark $label worst=${names[i]}/${names[j]}").that(d).isAtLeast(10.0)
            }
            assertThat(st.running).isEqualTo(s.primary)
        }
    }
}
