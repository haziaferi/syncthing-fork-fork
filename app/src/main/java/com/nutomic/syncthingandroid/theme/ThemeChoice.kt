package com.nutomic.syncthingandroid.theme

import android.os.Build

enum class ColourSource { BRAND, WALLPAPER }

/** Picks the colour source: the brand scheme unless wallpaper colours are on and the OS (Android 12+) has them. */
object ThemeChoice {
    fun source(useWallpaper: Boolean, sdkInt: Int): ColourSource =
        if (useWallpaper && sdkInt >= Build.VERSION_CODES.S) ColourSource.WALLPAPER else ColourSource.BRAND
}
