package com.nutomic.syncthingandroid.service;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class StopDelayTest {

    private final StopDelay delay = new StopDelay();

    // SPEC:R09
    @Test
    public void shortDrop_staysMet() {
        assertThat(delay.effective("", true, 0)).isTrue();
        assertThat(delay.effective("", false, 1_000)).isTrue();
        assertThat(delay.effective("", false, 5_000)).isTrue();
        assertThat(delay.effective("", true, 6_000)).isTrue();
        assertThat(delay.nextCheckAt(6_000)).isEqualTo(-1L);
    }

    // SPEC:R09
    @Test
    public void longDrop_stopsAfterTenSeconds() {
        assertThat(delay.effective("", true, 0)).isTrue();
        assertThat(delay.effective("", false, 1_000)).isTrue();
        assertThat(delay.nextCheckAt(1_000)).isEqualTo(11_000L);
        assertThat(delay.effective("", false, 10_999)).isTrue();
        assertThat(delay.effective("", false, 11_000)).isFalse();
        assertThat(delay.nextCheckAt(11_000)).isEqualTo(-1L);
        assertThat(delay.effective("", false, 30_000)).isFalse();
    }

    // SPEC:R09
    @Test
    public void firstResultUnmet_noHold() {
        assertThat(delay.effective("folder:a", false, 0)).isFalse();
        assertThat(delay.nextCheckAt(0)).isEqualTo(-1L);
    }

    // SPEC:R09
    @Test
    public void twoDrops_countFromFirst() {
        delay.effective("", true, 0);
        assertThat(delay.effective("", false, 1_000)).isTrue();
        assertThat(delay.effective("", true, 2_000)).isTrue();
        assertThat(delay.effective("", false, 3_000)).isTrue();
        assertThat(delay.effective("", false, 12_999)).isTrue();
        assertThat(delay.effective("", false, 13_000)).isFalse();
    }

    // SPEC:R09
    @Test
    public void keysIndependent() {
        delay.effective("folder:a", true, 0);
        assertThat(delay.effective("folder:a", false, 1_000)).isTrue();
        assertThat(delay.effective("", false, 1_000)).isFalse();
        assertThat(delay.nextCheckAt(1_000)).isEqualTo(11_000L);
    }

    @Test
    public void clear_dropsHolds() {
        delay.effective("", true, 0);
        delay.effective("", false, 1_000);
        delay.clear();
        assertThat(delay.nextCheckAt(1_000)).isEqualTo(-1L);
        assertThat(delay.effective("", false, 2_000)).isFalse();
    }

    // SPEC:R09
    @Test
    public void expiredHoldNotChecked_notReportedAgain() {
        // A folder held when Syncthing stops is not checked again; its end must not stay "due".
        delay.effective("folder:a", true, 0);
        delay.effective("folder:a", false, 1_000);
        assertThat(delay.nextCheckAt(5_000)).isEqualTo(11_000L);
        assertThat(delay.nextCheckAt(11_000)).isEqualTo(-1L);
        assertThat(delay.nextCheckAt(60_000)).isEqualTo(-1L);
        assertThat(delay.effective("folder:a", false, 60_000)).isFalse();
    }
}
