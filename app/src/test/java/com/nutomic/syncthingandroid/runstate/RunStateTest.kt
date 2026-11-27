package com.nutomic.syncthingandroid.runstate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService.State
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class RunStateTest {

    private val allForce = listOf(
        Constants.BTNSTATE_NO_FORCE_START_STOP,
        Constants.BTNSTATE_FORCE_START,
        Constants.BTNSTATE_FORCE_STOP
    )

    private fun row(service: State, force: Int, completion: Int, expected: RunState) {
        assertWithMessage("$service force=$force completion=$completion")
            .that(RunState.of(service, force, completion))
            .isEqualTo(expected)
    }

    // SPEC:ui-design:R20
    @Test
    fun of_mapsEveryCase_toItsState() {
        for (f in allForce) {
            row(State.ERROR, f, 50, RunState.STOPPED)
            row(State.INIT, f, 50, RunState.STARTING)
            row(State.STARTING, f, 50, RunState.STARTING)
        }
        row(State.ACTIVE, Constants.BTNSTATE_FORCE_START, 50, RunState.RUNNING_ANYWAY)
        row(State.DISABLED, Constants.BTNSTATE_FORCE_START, -1, RunState.RUNNING_ANYWAY)
        for (s in listOf(State.ACTIVE, State.DISABLED)) {
            row(s, Constants.BTNSTATE_FORCE_STOP, 50, RunState.PAUSED_BY_YOU)
        }
        for (c in listOf(0, 99)) {
            row(State.ACTIVE, Constants.BTNSTATE_NO_FORCE_START_STOP, c, RunState.SYNCING)
        }
        for (c in listOf(100, -1)) {
            row(State.ACTIVE, Constants.BTNSTATE_NO_FORCE_START_STOP, c, RunState.RUNNING)
        }
        row(State.DISABLED, Constants.BTNSTATE_NO_FORCE_START_STOP, 50, RunState.PAUSED_BY_RULES)
    }

    // SPEC:ui-design:R20
    @Test
    fun word_isTheR20Word() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = mapOf(
            RunState.RUNNING to "Running",
            RunState.SYNCING to "Syncing",
            RunState.PAUSED_BY_RULES to "Paused by your rules",
            RunState.RUNNING_ANYWAY to "Running anyway",
            RunState.PAUSED_BY_YOU to "Paused by you",
            RunState.STARTING to "Starting",
            RunState.STOPPED to "Stopped"
        )
        assertThat(expected.keys).containsExactlyElementsIn(RunState.values().toList())
        for ((state, word) in expected) {
            assertThat(context.getString(state.word)).isEqualTo(word)
        }
    }

    // SPEC:ui-design:R20
    @Test
    fun glanceWord_shortensOnlyPausedByRules() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (state in RunState.values()) {
            val expected = if (state == RunState.PAUSED_BY_RULES) "Paused" else context.getString(state.word)
            assertThat(context.getString(state.glanceWord)).isEqualTo(expected)
        }
    }
}
