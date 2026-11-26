package com.nutomic.syncthingandroid.theme

import android.content.SharedPreferences
import android.os.Build

/** Keys and readers for the two UI preferences; they are user settings, so ConfigArchive carries them. */
object UiPrefs {
    const val KEY_USE_WALLPAPER_COLOURS = "use_wallpaper_colours"
    const val KEY_UI_DENSITY = "ui_density"

    /** The density a stored value names; an unknown, missing or wrong-typed value is Standard. */
    fun densityOf(value: Any?): UiDensity =
        UiDensity.entries.firstOrNull { it.key == value as? String } ?: UiDensity.STANDARD

    /** Whether a stored value turns wallpaper colours on: only a Boolean true does. */
    fun wallpaperOf(value: Any?): Boolean = value == true

    fun density(prefs: SharedPreferences): UiDensity = densityOf(prefs.all[KEY_UI_DENSITY])

    fun useWallpaper(prefs: SharedPreferences, sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.S && wallpaperOf(prefs.all[KEY_USE_WALLPAPER_COLOURS])
}
