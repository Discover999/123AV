package com.av123.video.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.prefs.VideoLayout
import com.av123.video.ui.icons.AppIconSize
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.util.rememberAppHaptics
import kotlinx.coroutines.launch

/**
 * 顶栏「网格 ↔ 单列」切换：一处改偏好，四个网格页同步。
 * 图标表示点击后要去的形态（网格态显示列表图标），与首页搜索键同一档 primary。
 */
@Composable
fun VideoLayoutToggle(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val settings by container.appSettingsState.collectAsStateWithLifecycle()
    val layout = settings.settings.videoLayout
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()

    IconButton(
        onClick = {
            haptics.tick()
            scope.launch {
                container.settingsStore.setVideoLayout(
                    if (layout == VideoLayout.LIST) VideoLayout.GRID else VideoLayout.LIST
                )
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (layout == VideoLayout.LIST) AppIcons.GridView else AppIcons.ViewAgendaOutlined,
            contentDescription = stringResource(
                if (layout == VideoLayout.LIST) R.string.video_layout_to_grid
                else R.string.video_layout_to_list
            ),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AppIconSize.AppBar)
        )
    }
}
