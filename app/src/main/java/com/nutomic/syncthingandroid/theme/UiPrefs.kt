package com.nutomic.syncthingandroid.theme

import android.content.SharedPreferences
import android.os.Build

/** Keys and readers for the two UI preferences; they are user settings, so ConfigArchive carries them. */
object UiPrefs {
    const val KEY_USE_WALLPAPER_COLOURS = "use_wallpaper_colours"
    const val KEY_UI_DENSITY = "ui_density"

    fun density(prefs: SharedPreferences): UiDensity {
        val key = prefs.all[KEY_UI_DENSITY] as? String
        return UiDensity.entries.firstOrNull { it.key == key } ?: UiDensity.STANDARD
    }

    fun useWallpaper(prefs: SharedPreferences, sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.S && (prefs.all[KEY_USE_WALLPAPER_COLOURS] as? Boolean ?: false)
}
