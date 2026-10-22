package com.av123.video.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 统一触感分级：
 * - [tick] 轻反馈：选中、切页、展开、开关、普通按钮
 * - [confirm] 有后果的提交：删除、清理、重试、起播、分享
 *
 * 页内导航（点卡片进详情、返回上一页）不振动，导航本身已有转场反馈。
 */
@Stable
class AppHaptics internal constructor(private val feedback: HapticFeedback) {
    fun tick() {
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun confirm() {
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { AppHaptics(feedback) }
}
