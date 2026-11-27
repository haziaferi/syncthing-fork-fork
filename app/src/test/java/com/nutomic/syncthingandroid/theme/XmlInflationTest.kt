package com.nutomic.syncthingandroid.theme

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.widget.TextViewCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.nutomic.syncthingandroid.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upstream XML screens keep their AppCompat widgets under the fork's Material 3 theme until they are
 * rebuilt: Material 3 alone would inflate every `<Button>` as a filled MaterialButton.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class XmlInflationTest {

    class ForkThemedActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_SyncthingFork)
            super.onCreate(savedInstanceState)
        }
    }

    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** Inflates [layout] through the activity's LayoutInflater, so AppCompat's view factory runs. */
    private fun inflate(layout: Int): View {
        val activity = Robolectric.buildActivity(ForkThemedActivity::class.java).setup().get()
        return activity.layoutInflater.inflate(layout, FrameLayout(activity), false)
    }

    private fun checkButtons(layout: Int, count: Int, withStartIcon: Set<Int>) {
        val found = buttons(inflate(layout))
        assertThat(found).hasSize(count)
        for (b in found) {
            val name = b.resources.getResourceEntryName(b.id)
            assertWithMessage("$name class").that(b).isNotInstanceOf(MaterialButton::class.java)
            assertWithMessage("$name class").that(b).isInstanceOf(AppCompatButton::class.java)
            if (b.id in withStartIcon) {
                assertWithMessage("$name drawableStart")
                    .that(TextViewCompat.getCompoundDrawablesRelative(b)[0]).isNotNull()
            }
        }
    }

    // SPEC:ui-design:R02
    @Test
    fun photoShootIntro_keepsAppCompatButtons() {
        checkButtons(
            R.layout.activity_photo_shoot_intro,
            count = 4,
            withStartIcon = setOf(R.id.btnGrantStoragePerm, R.id.btnGrantCameraPerm),
        )
    }

    // SPEC:ui-design:R02
    @Test
    fun folderListItem_keepsAppCompatButtons() {
        checkButtons(R.layout.item_folder_list, count = 2, withStartIcon = setOf(R.id.override, R.id.revert))
    }

    /**
     * The main screen's tab strip sits on colorPrimary. Material 3's default tab colours are
     * colorPrimary (selected, invisible there) and colorOnSurfaceVariant (unselected, faint), so both
     * states and the indicator must use the toolbar text colour, at 7:1 for small text.
     */
    private fun checkMainTabs() {
        val tabs = inflate(R.layout.activity_main)
            .findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabContainer)
        val primary = tabs.context.getColor(R.color.fork_primary)
        val colours = tabs.tabTextColors!!
        val selected = colours.getColorForState(intArrayOf(android.R.attr.state_selected), 0)
        val unselected = colours.getColorForState(intArrayOf(), 0)
        for ((state, colour) in listOf("selected" to selected, "unselected" to unselected)) {
            assertWithMessage("$state tab text on primary")
                .that(Contrast.ratio(colour, primary)).isAtLeast(7.0)
        }
        val a = tabs.context.obtainStyledAttributes(
            null, com.google.android.material.R.styleable.TabLayout,
            com.google.android.material.R.attr.tabStyle, 0,
        )
        val indicator = a.getColor(com.google.android.material.R.styleable.TabLayout_tabIndicatorColor, 0)
        a.recycle()
        assertWithMessage("indicator on primary").that(Contrast.ratio(indicator, primary)).isAtLeast(3.0)
    }

    /** Upstream's AppCompat alert dialogs (discard, delete, restart) under the fork's dialog theme. */
    private fun checkAlertDialog() {
        val activity = Robolectric.buildActivity(ForkThemedActivity::class.java).setup().get()
        val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setMessage("Discard your changes?")
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        val message = dialog.findViewById<android.widget.TextView>(android.R.id.message)!!
        assertWithMessage("message text").that(message.currentTextColor)
            .isEqualTo(activity.getColor(R.color.fork_on_surface))
        for (which in listOf(android.content.DialogInterface.BUTTON_POSITIVE, android.content.DialogInterface.BUTTON_NEGATIVE)) {
            val button = dialog.getButton(which)
            // Material 3's dialog button style needs MaterialButton's theme overlay; on the AppCompatButton
            // the fork inflates, its colour list did not resolve and Android drew it magenta.
            assertWithMessage("button $which text").that(button.currentTextColor)
                .isEqualTo(activity.getColor(R.color.fork_primary))
            assertWithMessage("button $which all caps").that(button.isAllCaps).isFalse()
        }
    }

    // SPEC:ui-design:R10
    @Test
    fun alertDialog_usesForkColours() = checkAlertDialog()

    // SPEC:ui-design:R10
    @Test
    @Config(qualifiers = "night")
    fun alertDialog_usesForkColours_night() = checkAlertDialog()

    // SPEC:ui-design:R36
    @Test
    fun mainTabs_readableOnPrimary() = checkMainTabs()

    // SPEC:ui-design:R36
    @Test
    @Config(qualifiers = "night")
    fun mainTabs_readableOnPrimary_night() = checkMainTabs()

    /**
     * AppCompat reads viewInflaterClass with TypedArray.getString. A class name would be looked up by
     * reflection, which fails in release builds because R8 renames AppCompatViewInflater; null makes
     * AppCompat construct its default inflater directly. The value must override the
     * MaterialComponentsViewInflater that the Material 3 parent themes set.
     */
    @Test
    fun forkThemes_clearViewInflaterClass() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (style in listOf(R.style.Theme_SyncthingFork, R.style.Theme_SyncthingFork_Dialog)) {
            val themed = ContextThemeWrapper(context, style)
            val a = themed.obtainStyledAttributes(androidx.appcompat.R.styleable.AppCompatTheme)
            val name = a.getString(androidx.appcompat.R.styleable.AppCompatTheme_viewInflaterClass)
            a.recycle()
            assertWithMessage(context.resources.getResourceEntryName(style)).that(name).isNull()
        }
    }
}
