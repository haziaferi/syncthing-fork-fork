package com.nutomic.syncthingandroid.runstate

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.RunConditionMonitor

/**
 * What the state button does when pressed (SPEC:ui-design:R25). [forceStateAfter] is the force
 * choice it stores, or null when pressing it stores nothing.
 */
enum class StateAction(@StringRes val label: Int, val forceStateAfter: Int?) {
    PAUSE(R.string.fork_action_pause, Constants.BTNSTATE_FORCE_STOP),
    RUN_ANYWAY(R.string.fork_action_run_anyway, Constants.BTNSTATE_FORCE_START),
    FOLLOW_RULES(R.string.fork_action_follow_rules, Constants.BTNSTATE_NO_FORCE_START_STOP),
    OPEN_STATUS(R.string.fork_action_open, null)
}

/** The state button's action in this state, or null when there is none (SPEC:ui-design:R25). */
val RunState.button: StateAction?
    get() = when (this) {
        RunState.RUNNING, RunState.SYNCING -> StateAction.PAUSE
        RunState.PAUSED_BY_RULES -> StateAction.RUN_ANYWAY
        RunState.RUNNING_ANYWAY, RunState.PAUSED_BY_YOU -> StateAction.FOLLOW_RULES
        RunState.STARTING -> null
        RunState.STOPPED -> StateAction.OPEN_STATUS
    }

object StateButtons {
    /**
     * Stores the action's force choice and asks the run-condition monitor to decide again.
     * [StateAction.OPEN_STATUS] stores and sends nothing: opening Status is the surface's job.
     */
    fun perform(action: StateAction, prefs: SharedPreferences, send: (Intent) -> Unit) {
        val force = action.forceStateAfter ?: return
        prefs.edit().putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, force).commit()
        send(Intent(RunConditionMonitor.ACTION_UPDATE_SHOULDRUN_DECISION))
    }
}

fun StateButtons.perform(action: StateAction, context: Context) {
    perform(
        action,
        PreferenceManager.getDefaultSharedPreferences(context),
        LocalBroadcastManager.getInstance(context)::sendBroadcast
    )
}
