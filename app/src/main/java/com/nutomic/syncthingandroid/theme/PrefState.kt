package com.nutomic.syncthingandroid.theme

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The value of [key] in the app's default SharedPreferences (the file the Settings screens write),
 * as state that follows later changes.
 */
@Composable
fun <T> rememberPrefState(key: String, default: T): State<T> {
    val context = LocalContext.current.applicationContext
    val flow = remember(context, key) {
        prefFlow(PreferenceManager.getDefaultSharedPreferences(context), key, default)
    }
    return flow.collectAsState(initial = readPref(PreferenceManager.getDefaultSharedPreferences(context), key, default))
}

@Suppress("UNCHECKED_CAST")
private fun <T> readPref(prefs: SharedPreferences, key: String, default: T): T =
    if (prefs.contains(key)) (prefs.all[key] as? T) ?: default else default

private fun <T> prefFlow(prefs: SharedPreferences, key: String, default: T): Flow<T> = callbackFlow {
    // SharedPreferences holds listeners weakly; this reference lives as long as the flow is collected.
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, changed ->
        if (changed == key || changed == null) trySend(readPref(p, key, default))
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    trySend(readPref(prefs, key, default))
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()
