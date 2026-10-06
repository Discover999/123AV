package com.av123.video.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/** 双栏布局展开的最小屏幕宽度阈值（dp）：840 及以上走左列表 + 右详情双栏 */
const val EXPANDED_MIN_WIDTH_DP = 840

/** 双栏布局左栏（列表侧）固定宽度（dp） */
const val EXPANDED_LIST_PANE_WIDTH_DP = 420

/**
 * 当前是否为展开屏（平板横屏/折叠屏展开态等）。
 * 以屏幕可用宽度 [EXPANDED_MIN_WIDTH_DP] 为界，配置变化（折叠/展开/旋转）时自动重组。
 */
@Composable
@ReadOnlyComposable
fun rememberIsExpandedScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp >= EXPANDED_MIN_WIDTH_DP
