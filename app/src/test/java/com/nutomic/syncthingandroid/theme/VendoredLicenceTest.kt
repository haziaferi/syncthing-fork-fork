package com.nutomic.syncthingandroid.theme

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.nutomic.syncthingandroid.R
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The open-source licences screen is built from Gradle dependencies, so code copied into the source
 * tree (Material Color Utilities in theme/mcu) needs its own entry in app/aboutlibraries/libraries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class VendoredLicenceTest {

    @Test
    fun licencesScreen_listsMaterialColorUtilities() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val libraries = root.getJSONArray("libraries")
        val entry = (0 until libraries.length()).map { libraries.getJSONObject(it) }
            .firstOrNull { it.getString("uniqueId") == "com.nutomic.syncthingandroid.theme.mcu" }
        assertThat(entry).isNotNull()
        assertThat(entry!!.getString("name")).isEqualTo("Material Color Utilities")
        assertThat(entry.getJSONArray("licenses").getString(0)).isEqualTo("Apache-2.0")
        assertThat(root.getJSONObject("licenses").has("Apache-2.0")).isTrue()
    }
}
