package com.antidoomscroller.core.util

/** Human-readable durations for countdowns, with no dependency on Android resources. */
object Durations {

    fun format(millis: Long): String {
        if (millis <= 0) return "0m"
        val totalMinutes = millis / 60_000
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return buildString {
            if (days > 0) append(days).append("d ")
            if (days > 0 || hours > 0) append(hours).append("h ")
            append(minutes).append("m")
        }.trim()
    }

    fun formatPrecise(millis: Long): String {
        if (millis <= 0) return "00:00:00"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds / 60) % 60
        val seconds = totalSeconds % 60
        return listOf(hours, minutes, seconds).joinToString(":") { it.toString().padStart(2, '0') }
    }
}
