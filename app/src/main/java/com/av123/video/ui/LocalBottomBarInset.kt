package com.av123.video

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 悬浮导航栏开启时，一级页列表需要在底部额外留出的高度，
 * 让最后一个条目能滚到胶囊上方；贴底导航栏时为 0。
 */
val LocalBottomBarInset = staticCompositionLocalOf<Dp> { 0.dp }

/**
 * 封面预览弹窗是否打开。弹窗是独立窗口，跨窗口采样不到页面内容，
 * 外壳据此在弹窗期间整体模糊本窗口内容，磨砂由弹窗的半透明遮罩透出。
 * 由各外壳提供状态，页面只负责写入。
 */
val LocalPreviewOpen = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}
