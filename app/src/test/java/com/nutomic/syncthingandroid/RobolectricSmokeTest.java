package com.nutomic.syncthingandroid;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class RobolectricSmokeTest {

    @Test
    public void appResourcesResolve() {
        Context context = ApplicationProvider.getApplicationContext();
        assertThat(context.getString(R.string.reason_force_start)).isNotEmpty();
    }
}
