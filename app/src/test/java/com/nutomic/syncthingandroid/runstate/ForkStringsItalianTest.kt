package com.nutomic.syncthingandroid.runstate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Decision
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Reason
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [37])
class ForkStringsItalianTest {

    private fun names(path: String, tag: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map {
            nodes.item(it).attributes.getNamedItem("name").nodeValue
        }.toSet()
    }

    // SPEC:ui-design:R41
    @Test
    fun italian_hasEveryForkKey() {
        val english = "src/main/res/values/strings_fork.xml"
        val italian = "src/main/res/values-it/strings_fork.xml"
        assertThat(File(italian).exists()).isTrue()
        assertThat(names(italian, "string")).isEqualTo(names(english, "string"))
        assertThat(names(italian, "plurals")).isEqualTo(names(english, "plurals"))
    }

    // SPEC:ui-design:R21
    @Test
    @Config(qualifiers = "it")
    fun italianShortForms_atMost48Characters() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Proves the Italian resources are in use, not the English ones.
        assertThat(RunReasons.shortText(context.resources, Reason.NOT_ON_WIFI)).isEqualTo("Nessun Wi-Fi")
        for (reason in Reason.values()) {
            val text = RunReasons.shortText(context.resources, reason)
            assertWithMessage("$reason: $text").that(text.length).isAtMost(48)
        }
    }

    // SPEC:ui-design:R21
    @Test
    @Config(qualifiers = "it")
    fun italianLongForms_nameTheRealButtonLabels() {
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        val decision = Decision(false, listOf(Reason.FORCE_STOP), false, 0)
        val forceStart = RunReasons.longText(res, Reason.FORCE_START, decision)
        val forceStop = RunReasons.longText(res, Reason.FORCE_STOP, decision)
        assertThat(forceStart).contains(res.getString(StateAction.RUN_ANYWAY.label))
        assertThat(forceStart).contains(res.getString(StateAction.FOLLOW_RULES.label))
        assertThat(forceStop).contains(res.getString(StateAction.PAUSE.label))
        assertThat(forceStop).contains(res.getString(StateAction.FOLLOW_RULES.label))
    }

    @Test
    @Config(qualifiers = "it")
    fun italianWords_areTheReviewedText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(context.getString(RunState.PAUSED_BY_RULES.word)).isEqualTo("In pausa per le tue regole")
        assertThat(context.getString(StateAction.RUN_ANYWAY.label)).isEqualTo("Esegui comunque")
    }
}
