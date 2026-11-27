package com.nutomic.syncthingandroid.theme

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Colour-vision simulation and CIEDE2000, ported from the palette tool's colorspace.simulate_cvd and
 * delta_e_2000.
 *
 * Method: single-plane projections after Vienot, Brettel and Mollon (1999), applied in linear sRGB,
 * clamped, encoded back to sRGB and quantised to 8 bits (the tool works on hex colours, so the
 * quantisation is part of the method). The threshold of 10 in BrandSchemeTest is calibrated for this
 * method (Okabe-Ito's worst pair is 11.1 under it). Do not swap in another model (for example
 * Machado et al. 2009) without recalibrating the threshold: it gives different numbers.
 */
object ColourVisionTestHelper {

    val deuteranopia = arrayOf(
        doubleArrayOf(0.331066083, 0.668933917, 0.0),
        doubleArrayOf(0.331066112, 0.668933888, 0.0),
        doubleArrayOf(-0.027727502, 0.027727502, 1.0),
    )
    val protanopia = arrayOf(
        doubleArrayOf(0.170556992, 0.829443014, 0.0),
        doubleArrayOf(0.170556991, 0.829443008, 0.0),
        doubleArrayOf(-0.004517144, 0.004517144, 1.0),
    )

    private fun toLinear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun encode(l: Double): Int {
        val c = l.coerceIn(0.0, 1.0)
        val v = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
        return Math.round(v * 255.0).toInt()
    }

    /** The simulated colour as an 8-bit RGB int (alpha ignored). */
    fun simulate(argb: Int, m: Array<DoubleArray>): Int {
        val lin = doubleArrayOf(
            toLinear((argb shr 16) and 0xff),
            toLinear((argb shr 8) and 0xff),
            toLinear(argb and 0xff),
        )
        val out = IntArray(3) { i -> encode(m[i][0] * lin[0] + m[i][1] * lin[1] + m[i][2] * lin[2]) }
        return (out[0] shl 16) or (out[1] shl 8) or out[2]
    }

    /** CIE Lab (D65) from an 8-bit sRGB int. */
    fun lab(rgb: Int): DoubleArray {
        val lin = doubleArrayOf(
            toLinear((rgb shr 16) and 0xff),
            toLinear((rgb shr 8) and 0xff),
            toLinear(rgb and 0xff),
        )
        val x = (0.4124564 * lin[0] + 0.3575761 * lin[1] + 0.1804375 * lin[2]) / 0.95047
        val y = 0.2126729 * lin[0] + 0.7151522 * lin[1] + 0.0721750 * lin[2]
        val z = (0.0193339 * lin[0] + 0.1191920 * lin[1] + 0.9503041 * lin[2]) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    private fun rad(d: Double) = Math.toRadians(d)

    fun deltaE2000(a: DoubleArray, b: DoubleArray): Double {
        val (l1, a1, b1) = a
        val (l2, a2, b2) = b
        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val cBar = (c1 + c2) / 2
        val g = 0.5 * (1 - sqrt(cBar.pow(7) / (cBar.pow(7) + 25.0.pow(7))))
        val a1p = (1 + g) * a1
        val a2p = (1 + g) * a2
        val c1p = sqrt(a1p * a1p + b1 * b1)
        val c2p = sqrt(a2p * a2p + b2 * b2)
        fun hue(bb: Double, aa: Double): Double {
            if (bb == 0.0 && aa == 0.0) return 0.0
            val h = Math.toDegrees(atan2(bb, aa))
            return if (h < 0) h + 360 else h
        }
        val h1p = hue(b1, a1p)
        val h2p = hue(b2, a2p)
        val dLp = l2 - l1
        val dCp = c2p - c1p
        val dhp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180 -> h2p - h1p
            h2p - h1p > 180 -> h2p - h1p - 360
            else -> h2p - h1p + 360
        }
        val dHp = 2 * sqrt(c1p * c2p) * sin(rad(dhp / 2))
        val lBarP = (l1 + l2) / 2
        val cBarP = (c1p + c2p) / 2
        val hBarP = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 - 0.17 * cos(rad(hBarP - 30)) + 0.24 * cos(rad(2 * hBarP)) +
            0.32 * cos(rad(3 * hBarP + 6)) - 0.20 * cos(rad(4 * hBarP - 63))
        val dTheta = 30 * exp(-(((hBarP - 275) / 25).pow(2)))
        val rc = 2 * sqrt(cBarP.pow(7) / (cBarP.pow(7) + 25.0.pow(7)))
        val sl = 1 + 0.015 * (lBarP - 50).pow(2) / sqrt(20 + (lBarP - 50).pow(2))
        val sc = 1 + 0.045 * cBarP
        val sh = 1 + 0.015 * cBarP * t
        val rt = -sin(rad(2 * dTheta)) * rc
        return sqrt(
            (dLp / sl).pow(2) + (dCp / sc).pow(2) + (dHp / sh).pow(2) +
                rt * (dCp / sc) * (dHp / sh),
        )
    }

    /** Smallest pairwise CIEDE2000 among [colours] after simulating with [m]; returns (value, i, j). */
    fun worstPair(colours: List<Int>, m: Array<DoubleArray>): Triple<Double, Int, Int> {
        val labs = colours.map { lab(simulate(it, m)) }
        var worst = Triple(Double.MAX_VALUE, -1, -1)
        for (i in labs.indices) for (j in i + 1 until labs.size) {
            val d = deltaE2000(labs[i], labs[j])
            if (d < worst.first) worst = Triple(d, i, j)
        }
        return worst
    }
}
