package com.nutomic.syncthingandroid.theme

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.nutomic.syncthingandroid.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class XmlThemeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** BrandScheme role name -> (theme attribute, fork colour resource). */
    private val roles: Map<String, Pair<Int, Int>> = mapOf(
        "primary" to (R.attr.colorPrimary to R.color.fork_primary),
        "onPrimary" to (R.attr.colorOnPrimary to R.color.fork_on_primary),
        "primaryContainer" to (R.attr.colorPrimaryContainer to R.color.fork_primary_container),
        "onPrimaryContainer" to (R.attr.colorOnPrimaryContainer to R.color.fork_on_primary_container),
        "secondary" to (R.attr.colorSecondary to R.color.fork_secondary),
        "onSecondary" to (R.attr.colorOnSecondary to R.color.fork_on_secondary),
        "secondaryContainer" to (R.attr.colorSecondaryContainer to R.color.fork_secondary_container),
        "onSecondaryContainer" to (R.attr.colorOnSecondaryContainer to R.color.fork_on_secondary_container),
        "tertiary" to (R.attr.colorTertiary to R.color.fork_tertiary),
        "onTertiary" to (R.attr.colorOnTertiary to R.color.fork_on_tertiary),
        "background" to (android.R.attr.colorBackground to R.color.fork_background),
        "onBackground" to (R.attr.colorOnBackground to R.color.fork_on_background),
        "surface" to (R.attr.colorSurface to R.color.fork_surface),
        "onSurface" to (R.attr.colorOnSurface to R.color.fork_on_surface),
        "surfaceVariant" to (R.attr.colorSurfaceVariant to R.color.fork_surface_variant),
        "onSurfaceVariant" to (R.attr.colorOnSurfaceVariant to R.color.fork_on_surface_variant),
        "error" to (R.attr.colorError to R.color.fork_error),
        "onError" to (R.attr.colorOnError to R.color.fork_on_error),
        "outline" to (R.attr.colorOutline to R.color.fork_outline),
        "outlineVariant" to (R.attr.colorOutlineVariant to R.color.fork_outline_variant),
        "surfaceContainerLowest" to (R.attr.colorSurfaceContainerLowest to R.color.fork_surface_container_lowest),
        "surfaceContainerLow" to (R.attr.colorSurfaceContainerLow to R.color.fork_surface_container_low),
        "surfaceContainer" to (R.attr.colorSurfaceContainer to R.color.fork_surface_container),
        "surfaceContainerHigh" to (R.attr.colorSurfaceContainerHigh to R.color.fork_surface_container_high),
        "surfaceContainerHighest" to (R.attr.colorSurfaceContainerHighest to R.color.fork_surface_container_highest),
        "inverseSurface" to (R.attr.colorSurfaceInverse to R.color.fork_inverse_surface),
        "inverseOnSurface" to (R.attr.colorOnSurfaceInverse to R.color.fork_inverse_on_surface),
        "inversePrimary" to (R.attr.colorPrimaryInverse to R.color.fork_inverse_primary),
        "tertiaryContainer" to (R.attr.colorTertiaryContainer to R.color.fork_tertiary_container),
        "onTertiaryContainer" to (R.attr.colorOnTertiaryContainer to R.color.fork_on_tertiary_container),
        "errorContainer" to (R.attr.colorErrorContainer to R.color.fork_error_container),
        "onErrorContainer" to (R.attr.colorOnErrorContainer to R.color.fork_on_error_container),
        "surfaceBright" to (R.attr.colorSurfaceBright to R.color.fork_surface_bright),
        "surfaceDim" to (R.attr.colorSurfaceDim to R.color.fork_surface_dim),
        "primaryFixed" to (R.attr.colorPrimaryFixed to R.color.fork_primary_fixed),
        "primaryFixedDim" to (R.attr.colorPrimaryFixedDim to R.color.fork_primary_fixed_dim),
        "onPrimaryFixed" to (R.attr.colorOnPrimaryFixed to R.color.fork_on_primary_fixed),
        "onPrimaryFixedVariant" to (R.attr.colorOnPrimaryFixedVariant to R.color.fork_on_primary_fixed_variant),
        "secondaryFixed" to (R.attr.colorSecondaryFixed to R.color.fork_secondary_fixed),
        "secondaryFixedDim" to (R.attr.colorSecondaryFixedDim to R.color.fork_secondary_fixed_dim),
        "onSecondaryFixed" to (R.attr.colorOnSecondaryFixed to R.color.fork_on_secondary_fixed),
        "onSecondaryFixedVariant" to (R.attr.colorOnSecondaryFixedVariant to R.color.fork_on_secondary_fixed_variant),
        "tertiaryFixed" to (R.attr.colorTertiaryFixed to R.color.fork_tertiary_fixed),
        "tertiaryFixedDim" to (R.attr.colorTertiaryFixedDim to R.color.fork_tertiary_fixed_dim),
        "onTertiaryFixed" to (R.attr.colorOnTertiaryFixed to R.color.fork_on_tertiary_fixed),
        "onTertiaryFixedVariant" to (R.attr.colorOnTertiaryFixedVariant to R.color.fork_on_tertiary_fixed_variant),
    )

    private fun resolve(theme: android.content.res.Resources.Theme, attr: Int): Int {
        val value = TypedValue()
        assertThat(theme.resolveAttribute(attr, value, true)).isTrue()
        return value.data
    }

    private fun checkRoles(dark: Boolean) {
        val expected = BrandScheme.xmlRoles(dark)
        val themed = ContextThemeWrapper(context, R.style.Theme_SyncthingFork)
        val dialog = ContextThemeWrapper(context, R.style.Theme_SyncthingFork_Dialog)
        for ((role, pair) in roles) {
            val (attr, res) = pair
            assertThat(expected).containsKey(role)
            val want = expected.getValue(role)
            assertThat(context.getColor(res)).isEqualTo(want)
            assertThat(resolve(themed.theme, attr)).isEqualTo(want)
            assertThat(resolve(dialog.theme, attr)).isEqualTo(want)
        }
    }

    // SPEC:ui-design:R10
    @Test
    fun forkTheme_resolvesBrandRoles() {
        assertThat(resolve(ContextThemeWrapper(context, R.style.Theme_SyncthingFork).theme, R.attr.colorPrimary))
            .isEqualTo(0xff005b86.toInt())
        assertThat(resolve(ContextThemeWrapper(context, R.style.Theme_SyncthingFork).theme, R.attr.colorSurface))
            .isEqualTo(0xfff6fafd.toInt())
        checkRoles(dark = false)
    }

    // SPEC:ui-design:R10
    @Config(qualifiers = "night")
    @Test
    fun forkTheme_resolvesBrandRolesAtNight() {
        assertThat(resolve(ContextThemeWrapper(context, R.style.Theme_SyncthingFork).theme, R.attr.colorPrimary))
            .isEqualTo(0xff39a8e7.toInt())
        assertThat(resolve(ContextThemeWrapper(context, R.style.Theme_SyncthingFork).theme, R.attr.colorSurface))
            .isEqualTo(0xff0f1416.toInt())
        checkRoles(dark = true)
    }

    // SPEC:ui-design:R10
    @Config(qualifiers = "night")
    @Test
    fun nightToolbarOverlay_textContrastOnPrimaryIsAtLeast7() {
        val base = ContextThemeWrapper(context, R.style.Theme_SyncthingFork)
        val primary = context.getColor(R.color.fork_primary)
        for (style in listOf(R.style.ThemeOverlay_Syncthing_Toolbar, R.style.ThemeOverlay_SyncthingFork_Toolbar)) {
            val overlay = ContextThemeWrapper(base, style)
            val name = context.resources.getResourceEntryName(style)
            for (attr in listOf(android.R.attr.textColorPrimary, R.attr.colorControlNormal)) {
                val a = overlay.theme.obtainStyledAttributes(intArrayOf(attr))
                val colour = a.getColor(0, 0)
                a.recycle()
                assertWithMessage("$name ${context.resources.getResourceEntryName(attr)}")
                    .that(Contrast.ratio(colour, primary)).isAtLeast(7.0)
            }
        }
    }
}
