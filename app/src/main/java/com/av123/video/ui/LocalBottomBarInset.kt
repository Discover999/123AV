package com.av123.video

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 悬浮导航栏开启时，一级页列表需要在底部额外留出的高度，
 * 让最后一个条目能滚到胶囊上方；贴底导航栏时为 0。
 */
val LocalBottomBarInset = staticCompositionLocalOf<Dp> { 0.dp }
