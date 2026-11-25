package com.nutomic.syncthingandroid.service;

import static com.google.common.truth.Truth.assertThat;

import com.nutomic.syncthingandroid.service.DeviceConditions.Transport;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class NetworkSnapshotTest {

    private static NetworkState wifi(boolean isDefault, boolean validated, boolean metered, String ssid) {
        return new NetworkState(Transport.WIFI, isDefault, validated, metered, false, true, ssid);
    }

    private static NetworkState mobile(boolean isDefault, boolean metered, boolean roaming) {
        return new NetworkState(Transport.MOBILE, isDefault, true, metered, roaming, false, null);
    }

    private static NetworkState ethernet(boolean isDefault) {
        return new NetworkState(Transport.ETHERNET, isDefault, true, false, false, false, null);
    }

    private static DeviceConditions fill(NetworkState... networks) {
        List<NetworkState> list = Arrays.asList(networks);
        DeviceConditions.Builder b = new DeviceConditions.Builder();
        NetworkSnapshot.fill(b, list);
        return b.build();
    }

    @Test
    public void fill_noNetworks_flightMode() {
        DeviceConditions.Builder b = new DeviceConditions.Builder();
        NetworkSnapshot.fill(b, Collections.<NetworkState>emptyList());
        DeviceConditions d = b.build();
        assertThat(d.transport).isEqualTo(Transport.NO_ACTIVE_NETWORK);
        assertThat(d.isConnected()).isFalse();
        assertThat(d.wifiConnected).isFalse();
        assertThat(d.mobileConnected).isFalse();
        assertThat(d.ethernetConnected).isFalse();
    }

    // SPEC:R05
    @Test
    public void fill_vpnDefaultOverWifi_usesWifi() {
        DeviceConditions d = fill(wifi(false, true, false, "\"Home\""));
        assertThat(d.transport).isEqualTo(Transport.WIFI);
        assertThat(d.wifiConnected).isTrue();
        assertThat(d.wifiValidated).isTrue();
        assertThat(d.wifiInfoAvailable).isTrue();
        assertThat(d.wifiSsid).isEqualTo("\"Home\"");
    }

    @Test
    public void fill_vpnDefault_picksByOrder() {
        assertThat(fill(wifi(false, true, false, "a"), mobile(false, true, false)).transport)
                .isEqualTo(Transport.WIFI);
        assertThat(fill(wifi(false, false, false, "a"), mobile(false, true, false)).transport)
                .isEqualTo(Transport.MOBILE);
        assertThat(fill(wifi(false, true, false, "a"), ethernet(false)).transport)
                .isEqualTo(Transport.ETHERNET);
        assertThat(fill(wifi(false, false, false, "a")).transport).isEqualTo(Transport.WIFI);
    }

    @Test
    public void fill_defaultMobileWithUnvalidatedWifi_mobileDefault() {
        DeviceConditions d = fill(wifi(false, false, false, "a"), mobile(true, true, false));
        assertThat(d.transport).isEqualTo(Transport.MOBILE);
        assertThat(d.wifiConnected).isTrue();
        assertThat(d.wifiValidated).isFalse();
        assertThat(d.mobileConnected).isTrue();
    }

    // SPEC:R08
    @Test
    public void fill_metersAndRoamingFromOwnNetwork() {
        DeviceConditions d = fill(wifi(false, true, false, "a"), mobile(true, true, true));
        assertThat(d.wifiMetered).isFalse();
        assertThat(d.roaming).isTrue();

        DeviceConditions metered = fill(wifi(true, true, true, "a"));
        assertThat(metered.wifiMetered).isTrue();
        assertThat(metered.roaming).isFalse();
    }

    @Test
    public void fill_twoWifi_prefersValidated() {
        DeviceConditions d = fill(wifi(false, false, true, "\"Lab\""), wifi(true, true, false, "\"Home\""));
        assertThat(d.wifiSsid).isEqualTo("\"Home\"");
        assertThat(d.wifiValidated).isTrue();
        assertThat(d.wifiMetered).isFalse();
    }
}
