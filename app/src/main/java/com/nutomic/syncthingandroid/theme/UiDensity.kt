package com.nutomic.syncthingandroid.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Row and heading spacing; Compact halves the row padding (SPEC:ui-design:R09). */
enum class UiDensity(val key: String) {
    STANDARD("standard"),
    COMPACT("compact");

    /** The space above the text, which equals the space below it; lines below 1 count as 1. */
    fun rowPadding(lines: Int): Dp {
        val standardHalf = when {
            lines <= 1 -> 12
            lines == 2 -> 13
            lines <= 5 -> 14
            else -> 16
        }
        return (if (this == STANDARD) standardHalf else standardHalf / 2).dp
    }

    val headingSpaceAbove: Dp get() = if (this == STANDARD) 20.dp else 12.dp
    val headingSpaceBelow: Dp get() = if (this == STANDARD) 4.dp else 2.dp
    val horizontalMargin: Dp get() = 16.dp
}

val LocalUiDensity = staticCompositionLocalOf { UiDensity.STANDARD }
