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
import androidx.compose.ui.platform.LocalContext

/**
 * The fork's Compose theme: brand or wallpaper colours, status colours and density.
 * Typography and shapes are Material 3's defaults (no size overrides).
 */
@Composable
fun ForkTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val useWallpaper by rememberPrefState(UiPrefs.KEY_USE_WALLPAPER_COLOURS, false)
    val densityKey by rememberPrefState<String?>(UiPrefs.KEY_UI_DENSITY, null)
    val source = ThemeChoice.source(useWallpaper, Build.VERSION.SDK_INT)
    val density = UiDensity.entries.firstOrNull { it.key == densityKey } ?: UiDensity.STANDARD
    val colorScheme = remember(context, source, dark) { schemeFor(context, source, dark) }

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

internal fun schemeFor(context: Context, source: ColourSource, dark: Boolean): ColorScheme =
    if (source == ColourSource.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        WallpaperScheme.colorScheme(WallpaperScheme.seed(context), dark, WallpaperScheme.contrastLevel(context))
    } else if (dark) {
        BrandScheme.dark
    } else {
        BrandScheme.light
    }
