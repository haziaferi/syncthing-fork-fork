package com.nutomic.syncthingandroid.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours for sync and folder state. The five status colours are semantic and fixed (the brand
 * palette's primary, muted and error roles, plus fixed warning and success, R05), so they stay
 * apart for colour-blind users whatever the wallpaper; wallpaper mode does not change them.
 *
 * Per R06, text may be drawn in a status colour only where `Contrast.ratio(colour, surface) >= 7.0`;
 * otherwise the colour goes on the icon and the word stays in the body-text colour.
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
