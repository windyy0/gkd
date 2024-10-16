package com.nnnn.myg.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.collections.hashMapOf

fun formatTimeAgo(timestamp: Long): String {
    val currentTime = System.currentTimeMillis()
    val timeDifference = currentTime - timestamp

    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDifference)
    val hours = TimeUnit.MILLISECONDS.toHours(timeDifference)
    val days = TimeUnit.MILLISECONDS.toDays(timeDifference)
    val weeks = days / 7
    val months = (days / 30)
    val years = (days / 365)
    return when {
        years > 0 -> "${years}年前"
        months > 0 -> "${months}月前"
        weeks > 0 -> "${weeks}周前"
        days > 0 -> "${days}天前"
        hours > 0 -> "${hours}小时前"
        minutes > 0 -> "${minutes}分钟前"
        else -> "刚刚"
    }
}

private val formatDateMap by lazy { hashMapOf<String, SimpleDateFormat>() }

fun Long.format(formatStr: String): String {
    var df = formatDateMap[formatStr]
    if (df == null) {
        df = SimpleDateFormat(formatStr, Locale.getDefault())
        formatDateMap[formatStr] = df
    }
    return df.format(this)
}

data class ThrottleTimer(
    private val interval: Long = 500L,
    private var value: Long = 0L
) {
    fun expired(): Boolean {
        val t = System.currentTimeMillis()
        if (t - value > interval) {
            value = t
            return true
        }
        return false
    }
}

private val defaultThrottleTimer by lazy { ThrottleTimer() }

fun throttle(
    timer: ThrottleTimer = defaultThrottleTimer,
    fn: (() -> Unit),
): (() -> Unit) {
    return {
        if (timer.expired()) {
            fn.invoke()
        }
    }
}

fun <T> throttle(
    timer: ThrottleTimer = defaultThrottleTimer,
    fn: ((T) -> Unit),
): ((T) -> Unit) {
    return {
        if (timer.expired()) {
            fn.invoke(it)
        }
    }
}
