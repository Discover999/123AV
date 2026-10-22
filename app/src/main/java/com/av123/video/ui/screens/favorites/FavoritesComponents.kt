package com.av123.video.ui.screens.favorites

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.av123.video.R
import com.av123.video.data.repository.FavoriteGroup
import com.av123.video.ui.components.VideoLayoutToggle
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.util.rememberAppHaptics

/**
 * 收藏页顶栏：
 * - 总览：标题「我的收藏(N)」，无操作图标（新建入口在总览的虚线卡）；
 * - 内容：返回总览 + 范围标题 + 搜索 + 布局切换 + 溢出（收藏夹操作 / 清空全部）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesTopBar(
    destination: FavoritesDestination,
    totalCount: Int,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onGroupMenu: (FavoriteGroup) -> Unit,
    resolveGroup: (Long) -> FavoriteGroup?,
    onSearchClick: (() -> Unit)? = null
) {
    val haptics = rememberAppHaptics()
    TopAppBar(
        title = {
            when (destination) {
                FavoritesDestination.Overview ->
                    Text(
                        if (totalCount > 0) {
                            stringResource(R.string.favorites_title_count, totalCount)
                        } else {
                            stringResource(R.string.favorites_title)
                        }
                    )

                is FavoritesDestination.Content ->
                    when (val filter = destination.filter) {
                        FavoritesFilter.All ->
                            Text(stringResource(R.string.favorites_all_videos))
                        FavoritesFilter.Ungrouped ->
                            Text(stringResource(R.string.favorites_ungrouped_label))
                        is FavoritesFilter.Group ->
                            Text(resolveGroup(filter.groupId)?.name.orEmpty())
                    }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    haptics.tick()
                    onBack()
                }
            ) {
                Icon(
                    AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.favorites_back)
                )
            }
        },
        actions = {
            if (onSearchClick != null) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        onSearchClick()
                    }
                ) {
                    Icon(
                        AppIcons.Search,
                        contentDescription = stringResource(R.string.favorites_search)
                    )
                }
            }
            if (destination is FavoritesDestination.Content) {
                // 网格 / 单列切换（与首页、频道、搜索共用一份偏好）
                VideoLayoutToggle()
                when (val filter = destination.filter) {
                    is FavoritesFilter.Group -> IconButton(
                        onClick = {
                            haptics.tick()
                            resolveGroup(filter.groupId)?.let(onGroupMenu)
                        }
                    ) {
                        Icon(
                            AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.favorites_folder_actions)
                        )
                    }

                    FavoritesFilter.All -> if (totalCount > 0) {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                onClear()
                            }
                        ) {
                            Icon(
                                AppIcons.DeleteSweep,
                                contentDescription = stringResource(R.string.favorites_clear)
                            )
                        }
                    }

                    FavoritesFilter.Ungrouped -> Unit
                }
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}
