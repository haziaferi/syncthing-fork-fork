package com.nutomic.syncthingandroid.runstate

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.util.Consumer
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
    get() = StateButtons.buttonFor(this)

object StateButtons {
    /** The state button's action in [state], or null when there is none (SPEC:ui-design:R25). */
    @JvmStatic
    fun buttonFor(state: RunState): StateAction? = when (state) {
        RunState.RUNNING, RunState.SYNCING -> StateAction.PAUSE
        RunState.PAUSED_BY_RULES -> StateAction.RUN_ANYWAY
        RunState.RUNNING_ANYWAY, RunState.PAUSED_BY_YOU -> StateAction.FOLLOW_RULES
        RunState.STARTING -> null
        RunState.STOPPED -> StateAction.OPEN_STATUS
    }

    /**
     * Stores the action's force choice and asks the run-condition monitor to decide again.
     * Returns true when it did. [StateAction.OPEN_STATUS] stores and sends nothing and returns
     * false: opening Status is the caller's job.
     */
    @JvmStatic
    fun perform(action: StateAction, prefs: SharedPreferences, send: Consumer<Intent>): Boolean {
        val force = action.forceStateAfter ?: return false
        prefs.edit { putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, force) }
        send.accept(Intent(RunConditionMonitor.ACTION_UPDATE_SHOULDRUN_DECISION))
        return true
    }

    /** [perform] with the default preferences and the app's local broadcasts. */
    @JvmStatic
    fun perform(action: StateAction, context: Context): Boolean {
        val broadcasts = LocalBroadcastManager.getInstance(context)
        return perform(action, PreferenceManager.getDefaultSharedPreferences(context)) {
            broadcasts.sendBroadcast(it)
        }
    }
}
