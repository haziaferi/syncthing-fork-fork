package com.nutomic.syncthingandroid.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours for sync and folder state; running and paused follow the scheme, the rest are fixed. */
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
