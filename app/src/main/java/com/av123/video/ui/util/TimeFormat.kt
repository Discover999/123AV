package com.av123.video.ui.util

/** 相对时间：刚刚 / x分钟前 / x小时前 / x天前 */
fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return "刚刚"
    val minutes = diff / 60_000L
    return when {
        minutes < 1L -> "刚刚"
        minutes < 60L -> "${minutes}分钟前"
        minutes < 24L * 60 -> "${minutes / 60L}小时前"
        else -> "${minutes / (24L * 60)}天前"
    }
}

/**
 * 观看墙钟时长（ms）口语化格式化：0 显示"0分钟"；
 * 不足 1 分钟四舍五入到 1 分钟；满 1 小时为"x小时y分钟"（余 0 分钟省略分钟部分）。
 * 与统计页"今日观看/近 7 天"等中文统计语境配套，故直接返回中文。
 */
fun formatWatchDurationMs(ms: Long): String {
    if (ms <= 0L) return "0分钟"
    val minutes = (ms + 30_000L) / 60_000L
    return when {
        minutes < 1L -> "1分钟"
        minutes < 60L -> "${minutes}分钟"
        else -> {
            val hours = minutes / 60L
            val remain = minutes % 60L
            if (remain == 0L) "${hours}小时" else "${hours}小时${remain}分钟"
        }
    }
}

/** 毫秒时长格式化为 m:ss 或 h:mm:ss */
fun formatDuration(positionMs: Long): String {
    if (positionMs <= 0L) return "00:00"
    val totalSeconds = positionMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
