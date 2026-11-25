package com.nutomic.syncthingandroid.service;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Decision;
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Reason;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/** Pins the explanation text exactly as the pre-refactor code built it. */
@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class RunConditionExplanationTest {

    private Resources res;

    @Before
    public void setUp() {
        res = ApplicationProvider.getApplicationContext().getResources();
    }

    private static Decision decision(int minutes, Reason... reasons) {
        return new Decision(false, Arrays.asList(reasons), false, minutes);
    }

    /** Reasons the pre-refactor code prefixed with "\n" (taken from RunConditionMonitor at 71fca13c). */
    private static final java.util.Set<String> NEWLINE_BEFORE = new java.util.HashSet<>(Arrays.asList(
            "reason_location_unavailable", "reason_not_nonmetered_wifi",
            "reason_not_nonroaming_mobile_data", "reason_not_on_whitelisted_wifi",
            "reason_not_on_wifi", "reason_wifi_without_internet", "reason_on_flight_mode", "reason_on_metered_nonmetered_wifi",
            "reason_on_nonmetered_wifi", "reason_on_nonroaming_mobile_data",
            "reason_on_roaming_nonroaming_mobile_data", "reason_on_whitelisted_wifi",
            "reason_on_wifi", "reason_wifi_disallowed"));

    /** Reasons the pre-refactor code added without a newline. */
    private static final java.util.Set<String> NO_NEWLINE = new java.util.HashSet<>(Arrays.asList(
            "reason_force_start", "reason_force_stop", "reason_mobile_data_disallowed",
            "reason_not_charging", "reason_not_on_battery_power", "reason_not_on_mobile_data",
            "reason_not_while_auto_sync_data_disabled", "reason_not_while_power_saving",
            "reason_on_mobile_data"));

    @Test
    public void everyReason_hasTheLegacyLineBreak() {
        int checked = 0;
        for (Reason reason : Reason.values()) {
            if (reason == Reason.NOT_WITHIN_TIME_FRAME) {
                continue;  // Formatted with minutes; covered by timeFrame_usesPluralsAndZeroMinuteString.
            }
            String name = res.getResourceEntryName(reason.stringRes);
            assertThat(NEWLINE_BEFORE.contains(name) || NO_NEWLINE.contains(name)).isTrue();
            String expected = (NEWLINE_BEFORE.contains(name) ? "\n" : "") + res.getString(reason.stringRes);
            assertThat(RunConditionMonitor.formatExplanation(res, decision(0, reason))).isEqualTo(expected);
            checked++;
        }
        assertThat(checked).isEqualTo(NEWLINE_BEFORE.size() + NO_NEWLINE.size());
    }

    @Test
    public void forceStart_isExactlyForceStartString() {
        assertThat(RunConditionMonitor.formatExplanation(res, decision(0, Reason.FORCE_START)))
                .isEqualTo(res.getString(R.string.reason_force_start));
    }

    @Test
    public void timeFrame_usesPluralsAndZeroMinuteString() {
        String zero = String.format(res.getString(R.string.reason_not_within_time_frame_2),
                res.getString(R.string.reason_not_within_time_frame_0_min));
        assertThat(RunConditionMonitor.formatExplanation(res, decision(0, Reason.NOT_WITHIN_TIME_FRAME)))
                .isEqualTo(zero);

        String five = String.format(res.getString(R.string.reason_not_within_time_frame_2),
                String.format(res.getQuantityString(R.plurals.reason_not_within_time_frame_minutes, 5), 5));
        assertThat(RunConditionMonitor.formatExplanation(res, decision(5, Reason.NOT_WITHIN_TIME_FRAME)))
                .isEqualTo(five);
    }

    @Test
    public void mobileThenWifiReasons_newlinesMatchLegacy() {
        String expected = res.getString(R.string.reason_mobile_data_disallowed)
                + "\n" + res.getString(R.string.reason_on_wifi)
                + "\n" + res.getString(R.string.reason_on_nonmetered_wifi)
                + "\n" + res.getString(R.string.reason_on_whitelisted_wifi);
        assertThat(RunConditionMonitor.formatExplanation(res, decision(0,
                Reason.MOBILE_DATA_DISALLOWED, Reason.ON_WIFI, Reason.ON_NONMETERED_WIFI,
                Reason.ON_WHITELISTED_WIFI)))
                .isEqualTo(expected);
    }

    @Test
    public void flightMode_appendsWithNewline() {
        String expected = res.getString(R.string.reason_mobile_data_disallowed)
                + "\n" + res.getString(R.string.reason_not_on_wifi)
                + "\n" + res.getString(R.string.reason_on_flight_mode);
        assertThat(RunConditionMonitor.formatExplanation(res, decision(0,
                Reason.MOBILE_DATA_DISALLOWED, Reason.NOT_ON_WIFI, Reason.ON_FLIGHT_MODE)))
                .isEqualTo(expected);
    }
}
