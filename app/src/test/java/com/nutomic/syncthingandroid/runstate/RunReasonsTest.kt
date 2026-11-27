package com.nutomic.syncthingandroid.runstate

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.DeviceConditions
import com.nutomic.syncthingandroid.service.DeviceConditions.Transport
import com.nutomic.syncthingandroid.service.RunConditionEvaluator
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Decision
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Reason
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class RunReasonsTest {

    private val now = 10L * 60 * 60 * 1000 // 10 h since boot
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        // Last run "just now", so the time schedule is not met by default.
        prefs.edit().putLong(Constants.PREF_LAST_RUN_TIME, now).commit()
    }

    private fun device(): DeviceConditions.Builder = DeviceConditions.Builder()
        .charging(true)
        .powerSaving(false)
        .masterSyncEnabled(true)
        .transport(Transport.NO_ACTIVE_NETWORK)

    private fun wifi(): DeviceConditions = device().transport(Transport.WIFI)
        .wifiConnected(true).wifiValidated(true)
        .wifiInfoAvailable(true).wifiSsid("\"Home\"").build()

    private fun decide(d: DeviceConditions): Decision =
        RunConditionEvaluator.decide(prefs, d, now, false)

    private fun decision(reason: Reason, minutes: Int = 0) =
        Decision(false, listOf(reason), false, minutes)

    private class Row(val reason: Reason, val short: String, val long: String, val letsItRun: Boolean)

    private val table = listOf(
        Row(Reason.FORCE_START, "You chose Run anyway", "Running because you chose Run anyway. Your rules apply again when you choose Follow rules.", true),
        Row(Reason.FORCE_STOP, "You chose Pause", "Paused because you chose Pause. Your rules apply again when you choose Follow rules.", false),
        Row(Reason.NOT_WITHIN_TIME_FRAME, "Waiting for the next scheduled run", "You run Syncthing on a schedule. The last run was 5 minutes ago.", false),
        Row(Reason.NOT_CHARGING, "Not charging", "You run Syncthing only while the phone is charging.", false),
        Row(Reason.NOT_ON_BATTERY_POWER, "Charging", "You run Syncthing only on battery power.", false),
        Row(Reason.NOT_WHILE_POWER_SAVING, "Battery saver is on", "You pause Syncthing while battery saver is on.", false),
        Row(Reason.NOT_WHILE_AUTO_SYNC_DATA_DISABLED, "Android auto-sync is off", "You follow Android's auto-sync setting, and it is off.", false),
        Row(Reason.MOBILE_DATA_DISALLOWED, "On mobile data", "Mobile data is connected, and you haven't allowed it.", false),
        Row(Reason.ON_MOBILE_DATA, "On mobile data, which you allowed", "Running on mobile data, which you allowed.", true),
        Row(Reason.NOT_ON_MOBILE_DATA, "No mobile data", "You allowed mobile data, but none is connected.", false),
        Row(Reason.ON_ROAMING_NONROAMING_MOBILE_DATA, "Roaming allowed", "You allowed mobile data while roaming too.", true),
        Row(Reason.ON_NONROAMING_MOBILE_DATA, "Not roaming", "The phone isn't roaming.", true),
        Row(Reason.NOT_NONROAMING_MOBILE_DATA, "Roaming", "The phone is roaming, and you allowed mobile data only when not roaming.", false),
        Row(Reason.WIFI_DISALLOWED, "Wi-Fi not allowed", "You haven't allowed Wi-Fi or Ethernet.", false),
        Row(Reason.ON_WIFI, "On Wi-Fi", "Running on Wi-Fi or Ethernet, which you allowed.", true),
        Row(Reason.NOT_ON_WIFI, "No Wi-Fi", "You allowed Wi-Fi, but none is connected.", false),
        Row(Reason.WIFI_WITHOUT_INTERNET, "Wi-Fi has no internet; mobile data not allowed", "This Wi-Fi has no internet, and you haven't allowed mobile data, which Android would use instead.", false),
        Row(Reason.ON_METERED_NONMETERED_WIFI, "Metered Wi-Fi allowed", "You allowed metered Wi-Fi too.", true),
        Row(Reason.ON_NONMETERED_WIFI, "Wi-Fi is unmetered", "This Wi-Fi is unmetered.", true),
        Row(Reason.NOT_NONMETERED_WIFI, "Wi-Fi is metered", "This Wi-Fi is metered, and you allowed unmetered Wi-Fi only.", false),
        Row(Reason.ON_WHITELISTED_WIFI, "On an allowed Wi-Fi network", "This Wi-Fi network is allowed.", true),
        Row(Reason.NOT_ON_WHITELISTED_WIFI, "Not one of your Wi-Fi networks", "This Wi-Fi isn't one of your selected Wi-Fi networks.", false),
        Row(Reason.LOCATION_UNAVAILABLE, "Can't read the Wi-Fi name", "You run Syncthing on selected Wi-Fi networks only, and Android hides the Wi-Fi name while location is off.", false),
        Row(Reason.ON_FLIGHT_MODE, "Flight mode", "Running in flight mode, which you allowed.", true),
        Row(Reason.MONITOR_NOT_LOADED, "Run conditions not loaded", "Run conditions didn't load. Restart the app; if this comes back, report it.", false)
    )

    // SPEC:ui-design:R21
    @Test
    fun words_matchR21() {
        assertThat(table.map { it.reason }).containsExactlyElementsIn(Reason.values().toList())
        for (row in table) {
            val decision = decision(row.reason, 5)
            assertWithMessage("short of ${row.reason}")
                .that(RunReasons.shortText(context.resources, row.reason)).isEqualTo(row.short)
            assertWithMessage("long of ${row.reason}")
                .that(RunReasons.longText(context.resources, row.reason, decision)).isEqualTo(row.long)
            assertWithMessage("letsItRun of ${row.reason}")
                .that(RunReasons.words(row.reason).letsItRun).isEqualTo(row.letsItRun)
        }
    }

    // SPEC:ui-design:R21
    @Test
    fun shortForms_atMost48Characters() {
        for (reason in Reason.values()) {
            assertWithMessage("short of $reason")
                .that(RunReasons.shortText(context.resources, reason).length).isAtMost(48)
        }
    }

    // SPEC:ui-design:R21
    @Test
    fun longForms_nameTheRealButtonLabels() {
        val res = context.resources
        val forceStart = RunReasons.longText(res, Reason.FORCE_START, decision(Reason.FORCE_START))
        val forceStop = RunReasons.longText(res, Reason.FORCE_STOP, decision(Reason.FORCE_STOP))
        assertThat(forceStart).contains(res.getString(StateAction.RUN_ANYWAY.label))
        assertThat(forceStart).contains(res.getString(StateAction.FOLLOW_RULES.label))
        assertThat(forceStop).contains(res.getString(StateAction.PAUSE.label))
        assertThat(forceStop).contains(res.getString(StateAction.FOLLOW_RULES.label))
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_whileRunningOnWifi_isTheAllowedReasons() {
        val d = decide(wifi())
        assertThat(d.shouldRun).isTrue()
        val shown = RunReasons.shown(d, RunState.RUNNING)
        assertThat(shown).containsExactly(Reason.ON_WIFI, Reason.ON_NONMETERED_WIFI).inOrder()
        assertThat(shown).doesNotContain(Reason.MOBILE_DATA_DISALLOWED)
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_runningOnSelectedWifi_includesAllowedNetwork() {
        prefs.edit()
            .putBoolean(Constants.PREF_USE_WIFI_SSID_WHITELIST, true)
            .putStringSet(Constants.PREF_WIFI_SSID_WHITELIST, setOf("\"Home\""))
            .commit()
        val d = decide(wifi())
        assertThat(d.shouldRun).isTrue()
        assertThat(RunReasons.shown(d, RunState.RUNNING))
            .containsExactly(Reason.ON_WIFI, Reason.ON_NONMETERED_WIFI, Reason.ON_WHITELISTED_WIFI)
            .inOrder()
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_runningOnEthernet_isOnWifiOnly() {
        val d = decide(device().transport(Transport.ETHERNET).ethernetConnected(true).build())
        assertThat(d.shouldRun).isTrue()
        assertThat(RunReasons.shown(d, RunState.RUNNING)).containsExactly(Reason.ON_WIFI)
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_runningAfterRoamingBlocked_isTheWifiBranchOnly() {
        prefs.edit().putBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, true).commit()
        val d = decide(
            device().transport(Transport.MOBILE).mobileConnected(true).roaming(true)
                .wifiConnected(true).wifiValidated(true)
                .wifiInfoAvailable(true).wifiSsid("\"Home\"").build()
        )
        assertThat(d.shouldRun).isTrue()
        val shown = RunReasons.shown(d, RunState.RUNNING)
        assertThat(shown).doesNotContain(Reason.ON_MOBILE_DATA)
        assertThat(shown).containsExactly(Reason.ON_WIFI, Reason.ON_NONMETERED_WIFI).inOrder()
    }

    @Test
    fun shown_onMobileDataNotAllowed_saysOnMobileData() {
        prefs.edit().putBoolean(Constants.PREF_RUN_ON_WIFI, false).commit()
        val d = decide(device().transport(Transport.MOBILE).mobileConnected(true).build())
        assertThat(d.shouldRun).isFalse()
        val shown = RunReasons.shown(d, RunState.PAUSED_BY_RULES)
        assertThat(shown).containsExactly(Reason.MOBILE_DATA_DISALLOWED, Reason.WIFI_DISALLOWED).inOrder()
        assertThat(shown.map { RunReasons.shortText(context.resources, it) })
            .containsExactly("On mobile data", "Wi-Fi not allowed").inOrder()
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_pausedOnWifi_dropsMobileDataDisallowed() {
        prefs.edit().putBoolean(Constants.PREF_RUN_ON_METERED_WIFI, false).commit()
        val d = decide(
            device().transport(Transport.WIFI).wifiConnected(true).wifiValidated(true)
                .wifiMetered(true).wifiInfoAvailable(true).wifiSsid("\"Home\"").build()
        )
        assertThat(d.shouldRun).isFalse()
        assertThat(RunReasons.shown(d, RunState.PAUSED_BY_RULES))
            .containsExactly(Reason.NOT_NONMETERED_WIFI)
    }

    @Test
    fun shown_flightModeAllowed_isFlightModeOnly() {
        prefs.edit().putBoolean(Constants.PREF_RUN_IN_FLIGHT_MODE, true).commit()
        val d = decide(device().build())
        assertThat(d.shouldRun).isTrue()
        assertThat(RunReasons.shown(d, RunState.RUNNING)).containsExactly(Reason.ON_FLIGHT_MODE)
    }

    @Test
    fun shown_forcedStop_isYouChosePause() {
        prefs.edit()
            .putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_FORCE_STOP).commit()
        val d = decide(wifi())
        assertThat(RunReasons.shown(d, RunState.PAUSED_BY_YOU)).containsExactly(Reason.FORCE_STOP)
        assertThat(RunReasons.shortText(context.resources, Reason.FORCE_STOP)).isEqualTo("You chose Pause")
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_pausedByYou_isOnlyYouChosePause() {
        val d = decide(wifi())
        assertThat(d.shouldRun).isTrue()
        assertThat(RunReasons.shown(d, RunState.PAUSED_BY_YOU)).containsExactly(Reason.FORCE_STOP)
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_runningAnyway_isOnlyYouChoseRunAnyway() {
        val d = decide(device().build())
        assertThat(d.shouldRun).isFalse()
        assertThat(RunReasons.shown(d, RunState.RUNNING_ANYWAY)).containsExactly(Reason.FORCE_START)
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_startingOrStopped_isEmpty() {
        val run = decide(wifi())
        val stop = decide(device().build())
        assertThat(run.shouldRun).isTrue()
        assertThat(stop.shouldRun).isFalse()
        for (state in listOf(RunState.STARTING, RunState.STOPPED)) {
            for (d in listOf(run, stop)) {
                assertWithMessage("$state, shouldRun ${d.shouldRun}")
                    .that(RunReasons.shown(d, state)).isEmpty()
            }
        }
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_runningWithStaleStopDecision_isEmpty() {
        val d = decide(device().build())
        assertThat(d.shouldRun).isFalse()
        assertThat(RunReasons.shown(d, RunState.RUNNING)).isEmpty()
        assertThat(RunReasons.shown(d, RunState.SYNCING)).isEmpty()
    }

    // SPEC:ui-design:R21
    @Test
    fun shown_pausedByRulesWithStaleRunDecision_isEmpty() {
        val d = decide(wifi())
        assertThat(d.shouldRun).isTrue()
        assertThat(RunReasons.shown(d, RunState.PAUSED_BY_RULES)).isEmpty()
    }

    // SPEC:run-conditions:R10
    @Test
    fun shown_wifiWithoutInternet_isTheR07Reason() {
        val d = decide(
            device().transport(Transport.MOBILE).mobileConnected(true)
                .wifiConnected(true).wifiValidated(false)
                .wifiInfoAvailable(true).wifiSsid("\"Home\"").build()
        )
        assertThat(d.shouldRun).isFalse()
        assertThat(RunReasons.shown(d, RunState.PAUSED_BY_RULES))
            .containsExactly(Reason.WIFI_WITHOUT_INTERNET)
        assertThat(RunReasons.shortText(context.resources, Reason.WIFI_WITHOUT_INTERNET))
            .isEqualTo("Wi-Fi has no internet; mobile data not allowed")
    }

    @Test
    fun longText_timeScheduleUnderAMinute() {
        val d = decision(Reason.NOT_WITHIN_TIME_FRAME, 0)
        assertThat(RunReasons.longText(context.resources, Reason.NOT_WITHIN_TIME_FRAME, d))
            .isEqualTo("You run Syncthing on a schedule. The last run was less than a minute ago.")
    }

    @Test
    fun actions_onlyForLocationAndMonitor() {
        val res = context.resources
        for (reason in Reason.values()) {
            val action = RunReasons.words(reason).action
            when (reason) {
                Reason.LOCATION_UNAVAILABLE -> assertThat(res.getString(action!!)).isEqualTo("Turn on location")
                Reason.MONITOR_NOT_LOADED -> assertThat(res.getString(action!!)).isEqualTo("Restart app")
                else -> assertWithMessage("action of $reason").that(action).isNull()
            }
        }
    }
}
