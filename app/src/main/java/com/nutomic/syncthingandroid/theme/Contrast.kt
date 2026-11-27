package com.nutomic.syncthingandroid.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x relative-luminance contrast. The one implementation; tests and checks use this. */
object Contrast {

    /** Contrast ratio between two ARGB colours (alpha ignored), 1.0 to 21.0, order does not matter. */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        val r = channel((argb shr 16) and 0xff)
        val g = channel((argb shr 8) and 0xff)
        val b = channel(argb and 0xff)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
