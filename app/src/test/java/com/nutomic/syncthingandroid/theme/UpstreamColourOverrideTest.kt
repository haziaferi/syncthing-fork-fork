package com.nutomic.syncthingandroid.theme

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import com.nutomic.syncthingandroid.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upstream colours still drawn on screen (list status text, the exit button, share links, the camera
 * intro, the usage-reporting dialog) are remapped onto the fork's tokens by fork-owned overrides in
 * values-v23 and values-night-v23, so they clear R36's floors in both modes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class UpstreamColourOverrideTest {

    class ForkThemedActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_SyncthingFork)
            super.onCreate(savedInstanceState)
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun colour(id: Int) = context.getColor(id)

    private fun checkRemapped() {
        val expected = mapOf(
            "text_blue" to (R.color.text_blue to R.color.fork_primary),
            "text_purple" to (R.color.text_purple to R.color.fork_on_surface_variant),
            "text_red" to (R.color.text_red to R.color.fork_error),
            "text_orange" to (R.color.text_orange to R.color.fork_on_surface),
            "text_green" to (R.color.text_green to R.color.fork_on_surface),
            "accent" to (R.color.accent to R.color.fork_primary),
            "primary" to (R.color.primary to R.color.fork_primary),
        )
        val surface = colour(R.color.fork_surface)
        for ((name, ids) in expected) {
            assertWithMessage(name).that(colour(ids.first)).isEqualTo(colour(ids.second))
            assertWithMessage("$name on surface").that(Contrast.ratio(colour(ids.first), surface)).isAtLeast(7.0)
        }
        assertWithMessage("camera intro text on its background")
            .that(Contrast.ratio(0xffffffff.toInt(), colour(R.color.bg_screen4))).isAtLeast(7.0)
        assertWithMessage("usage-reporting text on its background")
            .that(Contrast.ratio(colour(R.color.fork_on_surface), colour(R.color.light_grey))).isAtLeast(7.0)
    }

    // SPEC:ui-design:R36
    @Test
    fun upstreamColours_remappedToForkTokens() = checkRemapped()

    // SPEC:ui-design:R36
    @Test
    @Config(qualifiers = "night")
    fun upstreamColours_remappedToForkTokens_night() = checkRemapped()

    /** In dark mode the intro's grey buttons were black text on AppCompat's dark grey (3.0:1). */
    // SPEC:ui-design:R36
    @Test
    @Config(qualifiers = "night")
    fun cameraIntroButtons_readableAtNight() {
        val activity = Robolectric.buildActivity(ForkThemedActivity::class.java).setup().get()
        val root = activity.layoutInflater.inflate(R.layout.activity_photo_shoot_intro, FrameLayout(activity), false)
        val button = root.findViewById<Button>(R.id.btnGrantCameraPerm)
        val fill = ViewCompat.getBackgroundTintList(button)!!.defaultColor
        assertWithMessage("label on button").that(Contrast.ratio(button.currentTextColor, fill)).isAtLeast(7.0)
        assertWithMessage("button on intro background")
            .that(Contrast.ratio(fill, colour(R.color.bg_screen4))).isAtLeast(3.0)
    }
}
