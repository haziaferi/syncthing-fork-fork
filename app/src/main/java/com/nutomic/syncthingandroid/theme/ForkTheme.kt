package com.nutomic.syncthingandroid.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * The fork's Compose theme: brand or wallpaper colours, status colours and density.
 * Typography and shapes are Material 3's defaults (no size overrides).
 *
 * [allowWallpaper] is false for Compose pieces hosted inside XML screens (the drawer, the Device ID
 * dialog): the XML screens around them keep the brand palette, so these pieces do too.
 */
@Composable
fun ForkTheme(allowWallpaper: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val useWallpaper by rememberPrefState(UiPrefs.KEY_USE_WALLPAPER_COLOURS, false, UiPrefs::wallpaperOf)
    val density by rememberPrefState(UiPrefs.KEY_UI_DENSITY, UiDensity.STANDARD, UiPrefs::densityOf)
    val source = sourceFor(useWallpaper, allowWallpaper, Build.VERSION.SDK_INT)
    // Read so that a configuration change recomposes and the wallpaper seed and contrast are read again.
    LocalConfiguration.current
    val wallpaper = source == ColourSource.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val seed = if (wallpaper) WallpaperScheme.seed(context) else 0
    val contrast = if (wallpaper) WallpaperScheme.contrastLevel(context) else 0.0
    val colorScheme = remember(source, dark, seed, contrast) { schemeFor(context, source, dark) }

    CompositionLocalProvider(
        LocalStatusColors provides StatusColors.brand(dark),
        LocalUiDensity provides density,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            shapes = Shapes(),
            content = content,
        )
    }
}

/** The colour source for one theme root: always the brand palette where wallpaper colours are not allowed. */
internal fun sourceFor(useWallpaper: Boolean, allowWallpaper: Boolean, sdkInt: Int): ColourSource =
    if (allowWallpaper) ThemeChoice.source(useWallpaper, sdkInt) else ColourSource.BRAND

internal fun schemeFor(context: Context, source: ColourSource, dark: Boolean): ColorScheme =
    if (source == ColourSource.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        WallpaperScheme.colorScheme(WallpaperScheme.seed(context), dark, WallpaperScheme.contrastLevel(context))
    } else if (dark) {
        BrandScheme.dark
    } else {
        BrandScheme.light
    }
