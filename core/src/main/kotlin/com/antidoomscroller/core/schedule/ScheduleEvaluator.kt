package com.antidoomscroller.core.schedule

import com.antidoomscroller.core.model.BreakWindow
import com.antidoomscroller.core.model.ScheduleSettings
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Decides whether a scheduled break is running right now.
 *
 * Breaks pause feed blocking only. The adult filter is never schedulable - loosening that always
 * goes through the cooldown and the challenge.
 */
object ScheduleEvaluator {

    fun activeWindow(settings: ScheduleSettings, packageName: String, now: LocalDateTime): BreakWindow? {
        if (!settings.enabled) return null
        return settings.windows.firstOrNull { window ->
            window.enabled &&
                (window.packages.isEmpty() || packageName in window.packages) &&
                isActive(window, now)
        }
    }

    fun isBreakActive(settings: ScheduleSettings, packageName: String, now: LocalDateTime): Boolean =
        activeWindow(settings, packageName, now) != null

    /** Handles windows that wrap past midnight: 22:00-00:30 on Friday runs into Saturday morning. */
    fun isActive(window: BreakWindow, now: LocalDateTime): Boolean {
        val minuteOfDay = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = now.dayOfWeek.minus(1).value
        return if (!window.wrapsMidnight) {
            today in window.days && minuteOfDay >= window.startMinuteOfDay && minuteOfDay < window.endMinuteOfDay
        } else {
            (today in window.days && minuteOfDay >= window.startMinuteOfDay) ||
                (yesterday in window.days && minuteOfDay < window.endMinuteOfDay)
        }
    }

    /** Minutes until the running break ends, or null when no break is running. */
    fun minutesRemaining(window: BreakWindow, now: LocalDateTime): Int {
        val minuteOfDay = now.hour * 60 + now.minute
        val end = window.endMinuteOfDay
        return if (end > minuteOfDay) end - minuteOfDay else (MINUTES_PER_DAY - minuteOfDay) + end
    }

    fun formatWindow(window: BreakWindow): String {
        val days = window.days.sorted().joinToString(", ") { dayLabel(it) }
        return "$days ${formatMinute(window.startMinuteOfDay)}-${formatMinute(window.endMinuteOfDay)}"
    }

    fun formatMinute(minuteOfDay: Int): String {
        val normalised = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val hours = normalised / 60
        val minutes = normalised % 60
        return buildString {
            append(hours.toString().padStart(2, '0'))
            append(':')
            append(minutes.toString().padStart(2, '0'))
        }
    }

    fun dayLabel(isoDay: Int): String = when (DayOfWeek.of(isoDay)) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
