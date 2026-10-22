package com.av123.video.ui.screens.favorites

import com.av123.video.ui.icons.AppIcons

import com.av123.video.ui.theme.AppShapes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.model.FavoriteRecord
import com.av123.video.data.repository.FavoriteGroup
import com.av123.video.data.repository.MAX_FAVORITE_GROUPS
import com.av123.video.data.repository.MAX_FAVORITE_GROUP_NAME_LENGTH
import com.av123.video.data.repository.GroupEditResult
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.VideoGridCard
import kotlinx.coroutines.launch

/**
 * “加入收藏夹”单选选择器（主流做法）：
 * 文件夹逐行列出，点选高亮但不关闭；可就地新建收藏夹；底部“完成”统一确认。
 * 已在所选文件夹内的视频不会重复添加，撤销也只移除本次新增的关系。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderPickerSheet(
    groups: List<FavoriteGroup>,
    memberships: Map<String, Set<Long>>,
    selectedVideoIds: Set<String>,
    createGroup: suspend (String) -> GroupEditResult,
    onDismiss: () -> Unit,
    onConfirm: (group: FavoriteGroup, toAdd: List<String>) -> Unit
) {
    // 预选：所有选中视频同属且唯一共有的收藏夹
    val preselected = remember(groups, selectedVideoIds, memberships) {
        commonSingleGroup(selectedVideoIds.map { memberships[it].orEmpty() })
    }
    var selectedGroupId by remember { mutableStateOf(preselected) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.favorites_picker_title, selectedVideoIds.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
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
                            AppIcons.Check,
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
            } else if (groups.size < MAX_FAVORITE_GROUPS) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.Medium)
                        .clickable { creating = true }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        AppIcons.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.favorites_new_group),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_no_groups_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            groups.forEach { group ->
                val selected = selectedGroupId == group.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.Medium)
                        .clickable { selectedGroupId = group.id }
                        .padding(vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.background(MaterialTheme.colorScheme.primary)
                                } else {
                                    Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                AppIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                }
            }
            Button(
                onClick = {
                    val group = groups.firstOrNull { it.id == selectedGroupId } ?: return@Button
                    val toAdd = selectedVideoIds
                        .filterNot { memberships[it]?.contains(group.id) == true }
                    onConfirm(group, toAdd)
                },
                enabled = selectedGroupId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.favorites_picker_done))
            }
        }
    }
}

/** 文件夹操作表：重命名 / 删除收藏夹 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderActionsSheet(
    group: FavoriteGroup,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
            )
            SheetActionRow(
                icon = AppIcons.DriveFileRenameOutline,
                label = stringResource(R.string.favorites_rename_group),
                onClick = onRename
            )
            SheetActionRow(
                icon = AppIcons.Delete,
                label = stringResource(R.string.favorites_delete_group),
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

/** 操作表内的可点行 */
@Composable
internal fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

/** 新建/重命名收藏夹命名弹窗；重名时就地提示且不关闭 */
@Composable
internal fun GroupNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> GroupEditResult
) {
    var name by remember { mutableStateOf(initialName) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val duplicateText = stringResource(R.string.favorites_group_duplicate)
    val limitText = stringResource(
        R.string.favorites_group_limit_reached,
        MAX_FAVORITE_GROUPS
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(MAX_FAVORITE_GROUP_NAME_LENGTH)
                    errorText = null
                },
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { message ->
                    { Text(message) }
                },
                label = { Text(stringResource(R.string.favorites_group_name_hint)) }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !submitting,
                onClick = {
                    submitting = true
                    scope.launch {
                        val result = onSubmit(name.trim())
                        errorText = when (result) {
                            is GroupEditResult.Success -> null
                            GroupEditResult.DuplicateName -> duplicateText
                            GroupEditResult.LimitReached -> limitText
                        }
                        submitting = false
                    }
                }
            ) { Text(stringResource(R.string.favorites_dialog_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
