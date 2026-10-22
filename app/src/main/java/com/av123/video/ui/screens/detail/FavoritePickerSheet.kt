package com.av123.video.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.av123.video.data.repository.MAX_FAVORITE_GROUP_NAME_LENGTH
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.util.rememberAppHaptics
import kotlinx.coroutines.launch

/**
 * 详情页「收藏到」弹层（只服务未收藏 → 收藏）：
 * - 单选：未分组 / 某个收藏夹，点选只改本地选中态，不立即写入；
 * - 点「完成」才落库：收藏 + 归入所选分组；
 * - 「新建收藏夹」在分组标题行右侧，输入框固定在分组列表顶部；
 *   建完只选中新组，仍要点「完成」才加入；
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
    val scope = rememberCoroutineScope()
    // null = 未分组
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val duplicateText = stringResource(R.string.favorites_group_duplicate)
    val limitText = stringResource(
        R.string.favorites_group_limit_reached,
        MAX_FAVORITE_GROUPS
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.detail_favorite_picker_title),
                style = MaterialTheme.typography.titleMedium
            )

            GroupOptionRow(
                icon = AppIcons.Inbox,
                label = stringResource(R.string.detail_favorite_only),
                selected = selectedGroupId == null,
                onClick = {
                    haptics.tick()
                    selectedGroupId = null
                }
            )

            // 分组多时在面板内部滚动，避免 ModalBottomSheet 被 20 行撑出屏幕
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题行：左侧说明，右侧「新建收藏夹」
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (groups.isEmpty()) R.string.detail_favorite_no_groups_short
                            else R.string.detail_favorite_pick_group
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (!creating && groups.size < MAX_FAVORITE_GROUPS) {
                        TextButton(
                            onClick = {
                                haptics.tick()
                                creating = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.favorites_new_group),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                // 新建项固定在分组列表顶部
                if (creating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = {
                                newName = it.take(MAX_FAVORITE_GROUP_NAME_LENGTH)
                                errorText = null
                            },
                            singleLine = true,
                            isError = errorText != null,
                            supportingText = errorText?.let { message ->
                                { Text(message) }
                            },
                            label = { Text(stringResource(R.string.favorites_group_name_hint)) },
                            modifier = Modifier.weight(1f)
                        )
                        val canSubmit = newName.isNotBlank() && !submitting
                        IconButton(
                            enabled = canSubmit,
                            onClick = {
                                submitting = true
                                scope.launch {
                                    when (val result = createGroup(newName.trim())) {
                                        is GroupEditResult.Success -> {
                                            selectedGroupId = result.group.id
                                            creating = false
                                            newName = ""
                                            errorText = null
                                        }
                                        GroupEditResult.DuplicateName -> errorText = duplicateText
                                        GroupEditResult.LimitReached -> errorText = limitText
                                    }
                                    submitting = false
                                }
                            }
                        ) {
                            Icon(
                                imageVector = AppIcons.Check,
                                contentDescription = stringResource(R.string.favorites_dialog_done),
                                tint = if (canSubmit) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                }
                            )
                        }
                        IconButton(onClick = {
                            creating = false
                            newName = ""
                            errorText = null
                        }) {
                            Icon(AppIcons.Close, contentDescription = null)
                        }
                    }
                }

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
