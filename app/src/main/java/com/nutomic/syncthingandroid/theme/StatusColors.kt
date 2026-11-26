package com.nutomic.syncthingandroid.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours for sync and folder state, from the brand palette in light or dark: running, paused and
 * error are its primary, muted (onSurfaceVariant) and error roles; warning and success are fixed
 * (R05). They stay brand in wallpaper mode too.
 */
data class StatusColors(
    val running: Color,
    val paused: Color,
    val warning: Color,
    val error: Color,
    val success: Color,
) {
    companion object {
        fun brand(dark: Boolean): StatusColors {
            val scheme = if (dark) BrandScheme.dark else BrandScheme.light
            return StatusColors(
                running = scheme.primary,
                paused = scheme.onSurfaceVariant,
                warning = if (dark) Color(0xffffc107) else Color(0xffb78a00),
                error = scheme.error,
                success = if (dark) Color(0xff2e7d32) else Color(0xff247a3c),
            )
        }
    }
}

val LocalStatusColors = staticCompositionLocalOf { StatusColors.brand(false) }
