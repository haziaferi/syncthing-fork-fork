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
fun <T> rememberPrefState(key: String, default: T, convert: (Any?) -> T?): State<T> {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val flow = remember(prefs, key) { prefFlow(prefs, key, default, convert) }
    val initial = remember(prefs, key) { readPref(prefs, key, default, convert) }
    return flow.collectAsState(initial = initial)
}

/** The stored value converted by [convert]; [default] when the key is missing or holds another type. */
internal fun <T> readPref(prefs: SharedPreferences, key: String, default: T, convert: (Any?) -> T?): T =
    prefs.all[key]?.let(convert) ?: default

private fun <T> prefFlow(prefs: SharedPreferences, key: String, default: T, convert: (Any?) -> T?): Flow<T> = callbackFlow {
    // SharedPreferences holds listeners weakly; this reference lives as long as the flow is collected.
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, changed ->
        if (changed == key || changed == null) trySend(readPref(p, key, default, convert))
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    trySend(readPref(prefs, key, default, convert))
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()
