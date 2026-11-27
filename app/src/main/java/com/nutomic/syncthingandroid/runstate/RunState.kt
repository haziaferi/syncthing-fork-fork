package com.nutomic.syncthingandroid.runstate

import androidx.annotation.StringRes
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService

/** The one state the app shows for Syncthing's run status, and its word (SPEC:ui-design:R20). */
enum class RunState(@StringRes val word: Int, @StringRes val glanceWord: Int) {
    RUNNING(R.string.fork_state_running, R.string.fork_state_running),
    SYNCING(R.string.fork_state_syncing, R.string.fork_state_syncing),
    PAUSED_BY_RULES(R.string.fork_state_paused_by_rules, R.string.fork_state_paused_glance),
    RUNNING_ANYWAY(R.string.fork_state_running_anyway, R.string.fork_state_running_anyway),
    PAUSED_BY_YOU(R.string.fork_state_paused_by_you, R.string.fork_state_paused_by_you),
    STARTING(R.string.fork_state_starting, R.string.fork_state_starting),
    STOPPED(R.string.fork_state_stopped, R.string.fork_state_stopped);

    companion object {
        /**
         * [syncCompletion] means what NotificationHandler gives it: -1 no device connected,
         * 100 up to date, 0 to 99 syncing.
         */
        @JvmStatic
        fun of(service: SyncthingService.State, forceButtonState: Int, syncCompletion: Int): RunState {
            when (service) {
                SyncthingService.State.ERROR -> return STOPPED
                SyncthingService.State.INIT, SyncthingService.State.STARTING -> return STARTING
                else -> Unit
            }
            return when (forceButtonState) {
                Constants.BTNSTATE_FORCE_START -> RUNNING_ANYWAY
                Constants.BTNSTATE_FORCE_STOP -> PAUSED_BY_YOU
                else -> when {
                    service == SyncthingService.State.DISABLED -> PAUSED_BY_RULES
                    syncCompletion in 0..99 -> SYNCING
                    else -> RUNNING
                }
            }
        }
    }
}
