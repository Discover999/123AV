package com.av123.video.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.av123.video.R
import com.av123.video.data.repository.FavoriteGroup
import com.av123.video.data.repository.GroupEditResult
import com.av123.video.data.repository.MAX_FAVORITE_GROUPS
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.screens.favorites.GroupNameDialog
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.util.rememberAppHaptics

/**
 * 详情页「收藏到」弹层（只服务未收藏 → 收藏）：
 * - 单选：未分组 / 某个收藏夹，点选只改本地选中态，不立即写入；
 * - 点「完成」才落库：收藏 + 归入所选分组；
 * - 可就地新建分组，建完自动选中它；
 * - 不提供「取消收藏」——已收藏时再点收藏按钮会直接取消。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritePickerSheet(
    groups: List<FavoriteGroup>,
    createGroup: suspend (String) -> GroupEditResult,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberAppHaptics()
    // null = 未分组
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }
    var showCreateGroup by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.detail_favorite_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                // 就地新建收藏夹：建完只选中，点「完成」才真正加入；到上限不再显示入口
                if (groups.size < MAX_FAVORITE_GROUPS) {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            showCreateGroup = true
                        }
                    ) {
                        Icon(
                            imageVector = AppIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.detail_favorite_add_group),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            GroupOptionRow(
                icon = AppIcons.Inbox,
                label = stringResource(R.string.detail_favorite_only),
                selected = selectedGroupId == null,
                onClick = {
                    haptics.tick()
                    selectedGroupId = null
                }
            )

            if (groups.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.detail_favorite_pick_group),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                )
                groups.forEach { group ->
                    GroupOptionRow(
                        icon = AppIcons.Bookmarks,
                        label = group.name,
                        selected = selectedGroupId == group.id,
                        onClick = {
                            haptics.tick()
                            selectedGroupId = group.id
                        }
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.detail_favorite_no_groups),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    haptics.confirm()
                    onConfirm(selectedGroupId)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.detail_favorite_done))
            }
        }
    }

    if (showCreateGroup) {
        GroupNameDialog(
            title = stringResource(R.string.favorites_new_group),
            initialName = "",
            onDismiss = { showCreateGroup = false },
            onSubmit = { name ->
                createGroup(name).also { result ->
                    if (result is GroupEditResult.Success) {
                        showCreateGroup = false
                        selectedGroupId = result.group.id
                    }
                }
            }
        )
    }
}

/** 单选行：左侧语义图标常显，选中只由右侧一个 Check 表示 */
@Composable
private fun GroupOptionRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
