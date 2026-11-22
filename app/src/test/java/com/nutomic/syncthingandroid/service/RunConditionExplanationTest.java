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
