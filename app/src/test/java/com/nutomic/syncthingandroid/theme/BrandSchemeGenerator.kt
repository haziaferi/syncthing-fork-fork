package com.nutomic.syncthingandroid.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * Generates BrandScheme's constants: SchemeTonalSpot from #0f8ecc at contrast level 0.5, then the
 * R05 roles pinned. Prints the Kotlin lines to paste into BrandScheme.kt (or writes them to the file
 * named by the first argument).
 */
object BrandSchemeGenerator {

    const val SEED = 0xff0f8ecc.toInt()
    const val CONTRAST = 0.5

    fun generate(dark: Boolean): ColorScheme {
        val base = WallpaperScheme.colorScheme(SEED, dark, CONTRAST)
        fun c(light: Long, dk: Long) = Color((if (dark) dk else light).toInt())
        val surface = c(0xfff6fafd, 0xff0f1416)
        val onSurface = c(0xff0b2230, 0xffdfe8ee)
        return base.copy(
            surface = surface,
            background = surface,
            onSurface = onSurface,
            onBackground = onSurface,
            primary = c(0xff005b86, 0xff39a8e7),
            onPrimary = c(0xffffffff, 0xff0f1416),
            onSurfaceVariant = c(0xff485861, 0xff8ea2ae),
            error = c(0xffb00014, 0xffff746a),
        )
    }

    fun render(): String = buildString {
        for (dark in listOf(false, true)) {
            appendLine(if (dark) "    val dark: ColorScheme = darkColorScheme(" else "    val light: ColorScheme = lightColorScheme(")
            for ((name, argb) in generate(dark).roleMap()) {
                appendLine("        $name = Color(0x%08x),".format(argb))
            }
            appendLine("    )")
            appendLine()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val text = render()
        if (args.isNotEmpty()) File(args[0]).writeText(text) else print(text)
    }
}
