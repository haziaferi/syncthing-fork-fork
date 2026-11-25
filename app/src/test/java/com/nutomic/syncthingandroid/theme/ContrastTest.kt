package com.nutomic.syncthingandroid.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContrastTest {

    @Test
    fun ratio_blackOnWhite_is21() {
        assertThat(Contrast.ratio(0xff000000.toInt(), 0xffffffff.toInt())).isWithin(0.001).of(21.0)
        assertThat(Contrast.ratio(0xffffffff.toInt(), 0xff000000.toInt())).isWithin(0.001).of(21.0)
    }

    @Test
    fun ratio_matchesKnownPair() {
        assertThat(Contrast.ratio(0xff0b2230.toInt(), 0xfff6fafd.toInt())).isWithin(0.01).of(15.56)
    }
}
