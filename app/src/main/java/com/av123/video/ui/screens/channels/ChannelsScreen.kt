package com.av123.video.ui.screens.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.model.NavChannelGroup
import com.av123.video.data.net.AppError
import com.av123.video.data.repository.VideoRepository
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.ErrorState
import com.av123.video.ui.components.LoadingState
import com.av123.video.ui.components.appErrorText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ChannelsUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val groups: List<NavChannelGroup> = emptyList()
)

class ChannelsViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    val uiState: StateFlow<ChannelsUiState> = combine(
        repository.feed,
        repository.isRefreshing,
        repository.error
    ) { feed, refreshing, error ->
        ChannelsUiState(
            isLoading = refreshing && feed == null,
            error = error,
            groups = feed?.navGroups.orEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ChannelsUiState(isLoading = true)
    )

    fun refresh() {
        repository.refresh(force = true)
    }
}

/**
 * “分栏”页：展示首页顶部导航解析出的全部栏目。
 * 第一个分组为 title=null 的快捷入口（新/热门/最近），其余为下拉菜单组
 * （趋势/类型/集合/业余/国内），点击栏目跳转对应的视频列表页。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChannelsScreen(
    onChannelClick: (title: String, path: String) -> Unit,
    scrollToTopTick: Int = 0
) {
    val container = LocalAppContainer.current
    val viewModel: ChannelsViewModel = viewModel { ChannelsViewModel(container.videoRepository) }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.channels_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()

        when {
            ui.isLoading -> LoadingState(modifier)
            ui.groups.isEmpty() && ui.error != null ->
                ErrorState(message = appErrorText(ui.error!!), onRetry = viewModel::refresh, modifier = modifier)
            ui.groups.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Category,
                title = stringResource(R.string.channels_empty_title),
                subtitle = stringResource(R.string.channels_empty_subtitle),
                modifier = modifier
            )
            else -> {
                val listState = rememberLazyListState()
                // 重复点击底部 Tab：回到顶部
                LaunchedEffect(scrollToTopTick) {
                    if (scrollToTopTick > 0) listState.scrollToItem(0)
                }
                LazyColumn(
                    state = listState,
                    modifier = modifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(ui.groups) { group ->
                        ChannelGroupSection(group = group, onChannelClick = onChannelClick)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelGroupSection(
    group: NavChannelGroup,
    onChannelClick: (title: String, path: String) -> Unit
) {
    // title 为 null 是顶部直链组（快捷入口），用强调色块区别于普通下拉组
    val quick = group.title == null
    Column {
        Text(
            text = group.title ?: stringResource(R.string.channels_quick),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 4
        ) {
            group.channels.forEach { channel ->
                ChannelChip(
                    text = channel.name,
                    emphasized = quick,
                    onClick = { onChannelClick(channel.name, channel.path) }
                )
            }
        }
    }
}

@Composable
private fun ChannelChip(
    text: String,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (emphasized) colorScheme.primaryContainer
                else colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) colorScheme.onPrimaryContainer
            else colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
