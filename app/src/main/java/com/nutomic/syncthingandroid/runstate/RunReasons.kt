package com.nutomic.syncthingandroid.runstate

import android.content.res.Resources
import androidx.annotation.StringRes
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Decision
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Reason

/**
 * The words for one run-decision reason (spec ui-design R21). [letsItRun] says whether the reason
 * is one that lets Syncthing run; [action] is the button label of a reason the user can act on.
 */
data class ReasonWords(
    @StringRes val short: Int,
    @StringRes val long: Int,
    val letsItRun: Boolean,
    @StringRes val action: Int? = null
)

object RunReasons {

    fun words(reason: Reason): ReasonWords = when (reason) {
        Reason.FORCE_START -> ReasonWords(R.string.fork_reason_force_start_short, R.string.fork_reason_force_start_long, true)
        Reason.FORCE_STOP -> ReasonWords(R.string.fork_reason_force_stop_short, R.string.fork_reason_force_stop_long, false)
        Reason.NOT_WITHIN_TIME_FRAME -> ReasonWords(R.string.fork_reason_not_within_time_frame_short, R.string.fork_reason_not_within_time_frame_long, false)
        Reason.NOT_CHARGING -> ReasonWords(R.string.fork_reason_not_charging_short, R.string.fork_reason_not_charging_long, false)
        Reason.NOT_ON_BATTERY_POWER -> ReasonWords(R.string.fork_reason_not_on_battery_power_short, R.string.fork_reason_not_on_battery_power_long, false)
        Reason.NOT_WHILE_POWER_SAVING -> ReasonWords(R.string.fork_reason_not_while_power_saving_short, R.string.fork_reason_not_while_power_saving_long, false)
        Reason.NOT_WHILE_AUTO_SYNC_DATA_DISABLED -> ReasonWords(R.string.fork_reason_not_while_auto_sync_data_disabled_short, R.string.fork_reason_not_while_auto_sync_data_disabled_long, false)
        Reason.MOBILE_DATA_DISALLOWED -> ReasonWords(R.string.fork_reason_mobile_data_disallowed_short, R.string.fork_reason_mobile_data_disallowed_long, false)
        Reason.ON_MOBILE_DATA -> ReasonWords(R.string.fork_reason_on_mobile_data_short, R.string.fork_reason_on_mobile_data_long, true)
        Reason.NOT_ON_MOBILE_DATA -> ReasonWords(R.string.fork_reason_not_on_mobile_data_short, R.string.fork_reason_not_on_mobile_data_long, false)
        Reason.ON_ROAMING_NONROAMING_MOBILE_DATA -> ReasonWords(R.string.fork_reason_on_roaming_nonroaming_mobile_data_short, R.string.fork_reason_on_roaming_nonroaming_mobile_data_long, true)
        Reason.ON_NONROAMING_MOBILE_DATA -> ReasonWords(R.string.fork_reason_on_nonroaming_mobile_data_short, R.string.fork_reason_on_nonroaming_mobile_data_long, true)
        Reason.NOT_NONROAMING_MOBILE_DATA -> ReasonWords(R.string.fork_reason_not_nonroaming_mobile_data_short, R.string.fork_reason_not_nonroaming_mobile_data_long, false)
        Reason.WIFI_DISALLOWED -> ReasonWords(R.string.fork_reason_wifi_disallowed_short, R.string.fork_reason_wifi_disallowed_long, false)
        Reason.ON_WIFI -> ReasonWords(R.string.fork_reason_on_wifi_short, R.string.fork_reason_on_wifi_long, true)
        Reason.NOT_ON_WIFI -> ReasonWords(R.string.fork_reason_not_on_wifi_short, R.string.fork_reason_not_on_wifi_long, false)
        Reason.WIFI_WITHOUT_INTERNET -> ReasonWords(R.string.fork_reason_wifi_without_internet_short, R.string.fork_reason_wifi_without_internet_long, false)
        Reason.ON_METERED_NONMETERED_WIFI -> ReasonWords(R.string.fork_reason_on_metered_nonmetered_wifi_short, R.string.fork_reason_on_metered_nonmetered_wifi_long, true)
        Reason.ON_NONMETERED_WIFI -> ReasonWords(R.string.fork_reason_on_nonmetered_wifi_short, R.string.fork_reason_on_nonmetered_wifi_long, true)
        Reason.NOT_NONMETERED_WIFI -> ReasonWords(R.string.fork_reason_not_nonmetered_wifi_short, R.string.fork_reason_not_nonmetered_wifi_long, false)
        Reason.ON_WHITELISTED_WIFI -> ReasonWords(R.string.fork_reason_on_whitelisted_wifi_short, R.string.fork_reason_on_whitelisted_wifi_long, true)
        Reason.NOT_ON_WHITELISTED_WIFI -> ReasonWords(R.string.fork_reason_not_on_whitelisted_wifi_short, R.string.fork_reason_not_on_whitelisted_wifi_long, false)
        Reason.LOCATION_UNAVAILABLE -> ReasonWords(
            R.string.fork_reason_location_unavailable_short, R.string.fork_reason_location_unavailable_long,
            false, R.string.fork_action_turn_on_location
        )
        Reason.ON_FLIGHT_MODE -> ReasonWords(R.string.fork_reason_on_flight_mode_short, R.string.fork_reason_on_flight_mode_long, true)
        Reason.MONITOR_NOT_LOADED -> ReasonWords(
            R.string.fork_reason_monitor_not_loaded_short, R.string.fork_reason_monitor_not_loaded_long,
            false, R.string.fork_action_restart_app
        )
    }

    private val forced = setOf(Reason.FORCE_START, Reason.FORCE_STOP)

    /** Wi-Fi details that say nothing on Ethernet; "On Wi-Fi" stays, as it says "Wi-Fi or Ethernet". */
    private val wifiOnly = setOf(Reason.ON_NONMETERED_WIFI, Reason.ON_METERED_NONMETERED_WIFI, Reason.ON_WHITELISTED_WIFI)

    /**
     * The reasons the explanation shows in [state], in the decision's order (SPEC:ui-design:R21).
     * A forced state shows only the user's choice. Running shows the branch that let Syncthing
     * run; paused by rules shows the reasons that stop it. A decision that disagrees with the
     * state is stale (for example during the stop delay) and shows nothing.
     */
    fun shown(decision: Decision, state: RunState): List<Reason> = when (state) {
        RunState.PAUSED_BY_YOU -> listOf(Reason.FORCE_STOP)
        RunState.RUNNING_ANYWAY -> listOf(Reason.FORCE_START)
        RunState.STARTING, RunState.STOPPED -> emptyList()
        RunState.RUNNING, RunState.SYNCING -> if (decision.shouldRun) letItRun(decision) else emptyList()
        RunState.PAUSED_BY_RULES -> if (decision.shouldRun) emptyList() else stopIt(decision)
    }

    /** The trailing run of reasons that let it run: the branch of the decision that succeeded. */
    private fun letItRun(decision: Decision): List<Reason> =
        decision.reasons.takeLastWhile { words(it).letsItRun }.filter {
            it !in forced &&
                (it != Reason.ON_WHITELISTED_WIFI || decision.wifiListInUse) &&
                (it !in wifiOnly || !decision.onEthernet)
        }

    /**
     * The reasons that stop it. "Mobile data not allowed" shows only while the phone is on mobile
     * data, and not with "Wi-Fi has no internet", which already says so.
     */
    private fun stopIt(decision: Decision): List<Reason> {
        val wifiWithoutInternet = Reason.WIFI_WITHOUT_INTERNET in decision.reasons
        return decision.reasons.filter {
            !words(it).letsItRun && it !in forced &&
                (it != Reason.MOBILE_DATA_DISALLOWED || (decision.onMobileData && !wifiWithoutInternet))
        }
    }

    fun shortText(res: Resources, reason: Reason): String = res.getString(words(reason).short)

    fun longText(res: Resources, reason: Reason, decision: Decision): String {
        val long = words(reason).long
        if (reason != Reason.NOT_WITHIN_TIME_FRAME) return res.getString(long)
        return res.getString(long, duration(res, decision.minutesSinceLastRun))
    }

    /**
     * A gap since the last run, in the largest whole unit: minutes under an hour, hours under a
     * day, then days (sleep intervals reach 21 days). Rounded down, so it never overstates the gap.
     */
    private fun duration(res: Resources, minutes: Int): String = when {
        minutes <= 0 -> res.getString(R.string.fork_duration_under_a_minute)
        minutes < MINUTES_PER_HOUR -> res.getQuantityString(R.plurals.fork_duration_minutes, minutes, minutes)
        minutes < MINUTES_PER_DAY -> (minutes / MINUTES_PER_HOUR).let {
            res.getQuantityString(R.plurals.fork_duration_hours, it, it)
        }
        else -> (minutes / MINUTES_PER_DAY).let { res.getQuantityString(R.plurals.fork_duration_days, it, it) }
    }

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
}
