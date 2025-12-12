package ru.adan.silmaril.misc

import kotlin.math.sqrt

/**
 * Simple cross-platform Point class to replace java.awt.Point.
 * Works on both Desktop (JVM) and Android.
 */
data class IntPoint(val x: Int, val y: Int) {
    fun distance(other: IntPoint): Double {
        val dx = (x - other.x).toDouble()
        val dy = (y - other.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }
}
