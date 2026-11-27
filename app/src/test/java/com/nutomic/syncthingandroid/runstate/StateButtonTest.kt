package com.nutomic.syncthingandroid.runstate

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.RunConditionMonitor
import com.nutomic.syncthingandroid.service.SyncthingService.State
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class StateButtonTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private val sent = mutableListOf<Intent>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("state-button-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        sent.clear()
    }

    private fun force() = prefs.getInt(
        Constants.PREF_BTNSTATE_FORCE_START_STOP,
        Constants.BTNSTATE_NO_FORCE_START_STOP
    )

    private fun setForce(value: Int) {
        prefs.edit().putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, value).commit()
    }

    // SPEC:ui-design:R25
    @Test
    fun button_followsTheR25Table() {
        val expected = mapOf(
            RunState.RUNNING to StateAction.PAUSE,
            RunState.SYNCING to StateAction.PAUSE,
            RunState.PAUSED_BY_RULES to StateAction.RUN_ANYWAY,
            RunState.RUNNING_ANYWAY to StateAction.FOLLOW_RULES,
            RunState.PAUSED_BY_YOU to StateAction.FOLLOW_RULES,
            RunState.STARTING to null,
            RunState.STOPPED to StateAction.OPEN_STATUS
        )
        assertThat(expected.keys).containsExactlyElementsIn(RunState.values().toList())
        for ((state, action) in expected) {
            assertWithMessage(state.name).that(state.button).isEqualTo(action)
        }
    }

    // SPEC:ui-design:R25
    @Test
    fun label_isTheR25Word() {
        val words = mapOf(
            StateAction.PAUSE to "Pause",
            StateAction.RUN_ANYWAY to "Run anyway",
            StateAction.FOLLOW_RULES to "Follow rules",
            StateAction.OPEN_STATUS to "Open"
        )
        assertThat(words.keys).containsExactlyElementsIn(StateAction.values().toList())
        for ((action, word) in words) {
            assertWithMessage(action.name).that(context.getString(action.label)).isEqualTo(word)
        }
    }

    /** A start the state implies: the service state, the stored force and the sync completion. */
    private data class Start(val state: RunState, val service: State, val force: Int, val completion: Int)

    // SPEC:ui-design:R25
    @Test
    fun oneTap_reachesTheResult_andFollowRulesReturnsControl() {
        val starts = listOf(
            Start(RunState.RUNNING, State.ACTIVE, Constants.BTNSTATE_NO_FORCE_START_STOP, 100),
            Start(RunState.SYNCING, State.ACTIVE, Constants.BTNSTATE_NO_FORCE_START_STOP, 50),
            Start(RunState.PAUSED_BY_RULES, State.DISABLED, Constants.BTNSTATE_NO_FORCE_START_STOP, 100),
            Start(RunState.RUNNING_ANYWAY, State.ACTIVE, Constants.BTNSTATE_FORCE_START, 100),
            Start(RunState.PAUSED_BY_YOU, State.DISABLED, Constants.BTNSTATE_FORCE_STOP, 100)
        )
        for (start in starts) {
            setForce(start.force)
            sent.clear()
            assertWithMessage("start ${start.state}")
                .that(RunState.of(start.service, force(), start.completion))
                .isEqualTo(start.state)
            val action = start.state.button!!
            assertThat(action).isNotEqualTo(StateAction.OPEN_STATUS)

            StateButtons.perform(action, prefs) { sent.add(it) }

            val after = RunState.of(start.service, force(), 100)
            when (action) {
                StateAction.PAUSE -> assertThat(after).isEqualTo(RunState.PAUSED_BY_YOU)
                StateAction.RUN_ANYWAY -> assertThat(after).isEqualTo(RunState.RUNNING_ANYWAY)
                else -> assertThat(force()).isEqualTo(Constants.BTNSTATE_NO_FORCE_START_STOP)
            }
            assertWithMessage("intents after ${start.state}").that(sent).hasSize(1)
            assertThat(sent[0].action)
                .isEqualTo(RunConditionMonitor.ACTION_UPDATE_SHOULDRUN_DECISION)
        }
        for (forced in listOf(RunState.PAUSED_BY_YOU, RunState.RUNNING_ANYWAY)) {
            setForce(if (forced == RunState.PAUSED_BY_YOU) Constants.BTNSTATE_FORCE_STOP else Constants.BTNSTATE_FORCE_START)
            assertThat(forced.button).isEqualTo(StateAction.FOLLOW_RULES)
            StateButtons.perform(StateAction.FOLLOW_RULES, prefs) { }
            assertWithMessage("after follow rules from $forced")
                .that(force()).isEqualTo(Constants.BTNSTATE_NO_FORCE_START_STOP)
        }
    }

    @Test
    fun openStatus_changesNothing() {
        setForce(Constants.BTNSTATE_FORCE_STOP)
        StateButtons.perform(StateAction.OPEN_STATUS, prefs) { sent.add(it) }
        assertThat(force()).isEqualTo(Constants.BTNSTATE_FORCE_STOP)
        assertThat(sent).isEmpty()
    }
}
