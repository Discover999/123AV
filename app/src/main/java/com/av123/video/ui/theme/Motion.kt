package com.av123.video.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/**
 * 全局动效 token：只保留三档节奏与两套弹簧，避免各页面各写一套时长。
 *
 * - 页面 push/pop：横滑 300ms、收尾 250ms
 * - 原地换内容（Tab、筛选、同一容器）：淡入淡出 220ms
 * - 浮层/气泡：160ms
 * - 按压：低回弹弹簧；颜色/尺寸形变：不回弹弹簧
 */
object Motion {
    const val PAGE_ENTER_MS = 300
    const val PAGE_EXIT_MS = 250
    const val FADE_THROUGH_MS = 220
    const val OVERLAY_MS = 160

    /** 下拉刷新落定：只轻微压暗，不再闪黑 */
    const val REFRESH_MS = 240
    const val REFRESH_DIM = 0.6f

    /** 骨架扫光一个周期 */
    const val SKELETON_MS = 1200

    /** 快放双箭头的循环周期 */
    const val PULSE_MS = 700

    val PageEnter: TweenSpec<Float> = tween(PAGE_ENTER_MS, easing = FastOutSlowInEasing)
    val PageExit: TweenSpec<Float> = tween(PAGE_EXIT_MS, easing = FastOutSlowInEasing)
    val FadeThrough: TweenSpec<Float> = tween(FADE_THROUGH_MS, easing = FastOutSlowInEasing)
    val Overlay: TweenSpec<Float> = tween(OVERLAY_MS, easing = FastOutSlowInEasing)
    val Refresh: TweenSpec<Float> = tween(REFRESH_MS, easing = FastOutSlowInEasing)

    /** 按压反馈：轻微回弹、快速落定 */
    val PressSpring: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 700f)

    /** 颜色/尺寸形变：不回弹 */
    val MorphSpring: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 500f)
    val MorphColorSpring: SpringSpec<Color> = spring(dampingRatio = 0.9f, stiffness = 500f)

    /** 图标“弹一下”的反馈（收藏/关注）：保留一次明显回弹 */
    val PopSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = 500f
    )

    fun <T> pageEnter(): TweenSpec<T> = tween(PAGE_ENTER_MS, easing = FastOutSlowInEasing)
    fun <T> pageExit(): TweenSpec<T> = tween(PAGE_EXIT_MS, easing = FastOutSlowInEasing)
    fun <T> fadeThrough(): TweenSpec<T> = tween(FADE_THROUGH_MS, easing = FastOutSlowInEasing)
    fun <T> morphSpring(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 500f)
}
