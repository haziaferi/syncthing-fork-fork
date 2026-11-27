package com.nutomic.syncthingandroid.theme

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiDensityTest {

    // SPEC:ui-design:R09
    @Test
    fun rowPadding_matchesTable() {
        val expected = mapOf(1 to (12 to 6), 2 to (13 to 6), 3 to (14 to 7), 5 to (14 to 7), 6 to (16 to 8), 9 to (16 to 8))
        for ((lines, pair) in expected) {
            assertThat(UiDensity.STANDARD.rowPadding(lines)).isEqualTo(pair.first.dp)
            assertThat(UiDensity.COMPACT.rowPadding(lines)).isEqualTo(pair.second.dp)
        }
    }

    @Test
    fun rowPadding_belowOneLine_isOneLine() {
        assertThat(UiDensity.STANDARD.rowPadding(0)).isEqualTo(12.dp)
        assertThat(UiDensity.COMPACT.rowPadding(-3)).isEqualTo(6.dp)
    }

    // SPEC:ui-design:R09
    @Test
    fun heading_matchesTable() {
        assertThat(UiDensity.STANDARD.headingSpaceAbove).isEqualTo(20.dp)
        assertThat(UiDensity.STANDARD.headingSpaceBelow).isEqualTo(4.dp)
        assertThat(UiDensity.COMPACT.headingSpaceAbove).isEqualTo(12.dp)
        assertThat(UiDensity.COMPACT.headingSpaceBelow).isEqualTo(2.dp)
        assertThat(UiDensity.STANDARD.horizontalMargin).isEqualTo(16.dp)
        assertThat(UiDensity.COMPACT.horizontalMargin).isEqualTo(16.dp)
    }
}
