package com.av123.video.ui.screens.channels

import com.av123.video.ui.icons.AppIcons

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.av123.video.LocalAppContainer
import com.av123.video.data.repository.FollowedActress
import com.av123.video.R
import com.av123.video.data.model.ActressProfile
import com.av123.video.data.model.FilterGroup
import com.av123.video.data.model.FilterOption
import com.av123.video.data.model.IndexEntry
import com.av123.video.data.model.Video
import com.av123.video.data.repository.CategoryPageState
import com.av123.video.data.repository.VideoRepository
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.ErrorState
import com.av123.video.ui.components.LoadingState
import com.av123.video.ui.components.appErrorText
import com.av123.video.ui.components.PaginationBar
import com.av123.video.ui.components.rememberTogglePopScale
import com.av123.video.ui.components.VideoGridCard
import com.av123.video.ui.components.VideoPreviewDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 视频流页顶部筛选栏（对齐网页 form.filters 的结构与交互）：
 * 左侧可横滑的维度胶囊（类型/年份/演员）+ 有非默认选中时出现的“重置”入口，
 * 中间竖分隔线，右侧固定排序（对应网页 .fspace 占位 + 菜单 is-right；
 * 排序选项同样动态解析，并非写死）。
 * 选择任意选项立即回到第 1 页重新加载，与网页 requestSubmit 行为一致。
 */
@Composable
internal fun VideoFilterBar(
    groups: List<FilterGroup>,
    selections: Map<String, String>,
    enabled: Boolean,
    onSelect: (key: String, value: String) -> Unit,
    onReset: () -> Unit
) {
    if (groups.isEmpty()) return
    val rightGroup = groups.last()
    val leftGroups = groups.dropLast(1)
    val hasActive = groups.any { g ->
        selections[g.key] != (g.options.firstOrNull()?.value ?: "")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 0.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leftGroups.forEach { group ->
                FilterDropdown(
                    group = group,
                    selectedValue = selections[group.key] ?: group.selectedValue,
                    enabled = enabled,
                    onSelect = onSelect
                )
            }
            if (hasActive) {
                IconButton(
                    onClick = onReset,
                    enabled = enabled,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.FilterAltOff,
                        contentDescription = stringResource(R.string.channel_filters_reset),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        // 分隔竖线：把左侧筛选维度与右侧固定排序视觉上分开（对应网页 .fspace），
        // 放在横滑区域之外，滚动时保持固定；仅当存在左侧维度时显示
        if (leftGroups.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        FilterDropdown(
            group = rightGroup,
            selectedValue = selections[rightGroup.key] ?: rightGroup.selectedValue,
            enabled = enabled,
            onSelect = onSelect,
            modifier = Modifier.padding(start = 8.dp, end = 16.dp)
        )
    }
}

/**
 * 单个筛选维度的胶囊按钮 + 下拉菜单：
 * 默认态中性底色、显示“维度: 当前值”；选了非默认项时 primaryContainer 高亮。
 * [FilterGroup.columns] > 1 时菜单按网页样式多列排布（年份），选中项带勾。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterDropdown(
    group: FilterGroup,
    selectedValue: String,
    enabled: Boolean,
    onSelect: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = group.options.firstOrNull { it.value == selectedValue }?.label
        ?: group.selectedLabel
    val active = selectedValue != (group.options.firstOrNull()?.value ?: "")

    Box(modifier = modifier) {
        Surface(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            shape = CircleShape,
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Icon(
                    imageVector = AppIcons.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = if (group.columns > 1) {
                Modifier.width(264.dp)
            } else {
                Modifier.widthIn(min = 168.dp)
            }
        ) {
            if (group.columns > 1) {
                // 多列菜单：按 --sel-cols 切行，末行不足补空位，保持列宽一致
                group.options.chunked(group.columns).forEach { rowOptions ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (i in 0 until group.columns) {
                            val option = rowOptions.getOrNull(i)
                            if (option == null) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                FilterOptionItem(
                                    option = option,
                                    selected = option.value == selectedValue,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        expanded = false
                                        onSelect(group.key, option.value)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                group.options.forEach { option ->
                    FilterOptionItem(
                        option = option,
                        selected = option.value == selectedValue,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            expanded = false
                            onSelect(group.key, option.value)
                        }
                    )
                }
            }
        }
    }
}

/** 下拉菜单里的一个选项：文案 + 选中勾，多列布局下也可等高复用 */
@Composable
internal fun FilterOptionItem(
    option: FilterOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
            )
        }
    }
}

