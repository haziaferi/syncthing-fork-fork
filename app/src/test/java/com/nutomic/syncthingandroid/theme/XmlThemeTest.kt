package com.nutomic.syncthingandroid.theme

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.nutomic.syncthingandroid.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class XmlThemeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun resolve(attr: Int): Int {
        val theme = ContextThemeWrapper(context, R.style.Theme_SyncthingFork).theme
        val value = TypedValue()
        assertThat(theme.resolveAttribute(attr, value, true)).isTrue()
        return value.data
    }

    // SPEC:ui-design:R10
    @Test
    fun forkTheme_resolvesBrandRoles() {
        assertThat(resolve(R.attr.colorPrimary)).isEqualTo(0xff005b86.toInt())
        assertThat(resolve(R.attr.colorSurface)).isEqualTo(0xfff6fafd.toInt())
    }

    // SPEC:ui-design:R10
    @Config(qualifiers = "night")
    @Test
    fun forkTheme_resolvesBrandRolesAtNight() {
        assertThat(resolve(R.attr.colorPrimary)).isEqualTo(0xff39a8e7.toInt())
        assertThat(resolve(R.attr.colorSurface)).isEqualTo(0xff0f1416.toInt())
    }
}
