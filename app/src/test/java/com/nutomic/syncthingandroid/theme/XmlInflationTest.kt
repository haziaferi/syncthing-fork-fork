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
