package com.nutomic.syncthingandroid.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.nutomic.syncthingandroid.theme.mcu.dynamiccolor.DynamicColor
import com.nutomic.syncthingandroid.theme.mcu.dynamiccolor.MaterialDynamicColors
import com.nutomic.syncthingandroid.theme.mcu.hct.Hct
import com.nutomic.syncthingandroid.theme.mcu.scheme.SchemeTonalSpot

/**
 * Material You scheme built from the wallpaper's accent colour at a chosen contrast level,
 * with the vendored Material Color Utilities (the platform's own scheme is fixed at level 0).
 */
@RequiresApi(31)
object WallpaperScheme {

    /** The wallpaper accent, `system_accent1_500`, as ARGB. */
    fun seed(context: Context): Int = context.getColor(android.R.color.system_accent1_500)

    /** 1.0 when the system contrast setting is high (API 34+), otherwise 0.5. */
    fun contrastLevel(context: Context): Double {
        if (Build.VERSION.SDK_INT >= 34) {
            val manager = context.getSystemService(UiModeManager::class.java)
            if (manager != null && manager.contrast >= 1.0f) return 1.0
        }
        return 0.5
    }

    fun colorScheme(seed: Int, dark: Boolean, contrastLevel: Double): ColorScheme {
        val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, contrastLevel)
        val c = MaterialDynamicColors()
        fun DynamicColor.col() = Color(getArgb(scheme))
        // Every role is given, so the light and dark factories differ in nothing; one call serves both.
        return lightColorScheme(
            primary = c.primary().col(),
            onPrimary = c.onPrimary().col(),
            primaryContainer = c.primaryContainer().col(),
            onPrimaryContainer = c.onPrimaryContainer().col(),
            inversePrimary = c.inversePrimary().col(),
            secondary = c.secondary().col(),
            onSecondary = c.onSecondary().col(),
            secondaryContainer = c.secondaryContainer().col(),
            onSecondaryContainer = c.onSecondaryContainer().col(),
            tertiary = c.tertiary().col(),
            onTertiary = c.onTertiary().col(),
            tertiaryContainer = c.tertiaryContainer().col(),
            onTertiaryContainer = c.onTertiaryContainer().col(),
            background = c.background().col(),
            onBackground = c.onBackground().col(),
            surface = c.surface().col(),
            onSurface = c.onSurface().col(),
            surfaceVariant = c.surfaceVariant().col(),
            onSurfaceVariant = c.onSurfaceVariant().col(),
            surfaceTint = c.surfaceTint().col(),
            inverseSurface = c.inverseSurface().col(),
            inverseOnSurface = c.inverseOnSurface().col(),
            error = c.error().col(),
            onError = c.onError().col(),
            errorContainer = c.errorContainer().col(),
            onErrorContainer = c.onErrorContainer().col(),
            outline = c.outline().col(),
            outlineVariant = c.outlineVariant().col(),
            scrim = c.scrim().col(),
            surfaceBright = c.surfaceBright().col(),
            surfaceDim = c.surfaceDim().col(),
            surfaceContainer = c.surfaceContainer().col(),
            surfaceContainerHigh = c.surfaceContainerHigh().col(),
            surfaceContainerHighest = c.surfaceContainerHighest().col(),
            surfaceContainerLow = c.surfaceContainerLow().col(),
            surfaceContainerLowest = c.surfaceContainerLowest().col(),
            primaryFixed = c.primaryFixed().col(),
            primaryFixedDim = c.primaryFixedDim().col(),
            onPrimaryFixed = c.onPrimaryFixed().col(),
            onPrimaryFixedVariant = c.onPrimaryFixedVariant().col(),
            secondaryFixed = c.secondaryFixed().col(),
            secondaryFixedDim = c.secondaryFixedDim().col(),
            onSecondaryFixed = c.onSecondaryFixed().col(),
            onSecondaryFixedVariant = c.onSecondaryFixedVariant().col(),
            tertiaryFixed = c.tertiaryFixed().col(),
            tertiaryFixedDim = c.tertiaryFixedDim().col(),
            onTertiaryFixed = c.onTertiaryFixed().col(),
            onTertiaryFixedVariant = c.onTertiaryFixedVariant().col(),
        )
    }
}
