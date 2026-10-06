package com.av123.video.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.repository.SearchState
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.ErrorState
import com.av123.video.ui.components.PaginationBar
import com.av123.video.ui.components.appErrorText
import com.av123.video.ui.components.VideoGridCard

/**
 * 搜索页：顶部为返回键 + 搜索输入框（回车/搜索按钮提交），
 * 下方两列网格展示结果，交互与首页分类列表一致。
 *
 * 搜索请求地址：{baseUrl}/cn/search?keyword=<关键词>
 */

/** 搜索关键词最大字数 */
private const val SEARCH_MAX_LENGTH = 15

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: SearchViewModel = viewModel {
        SearchViewModel(container.videoRepository, container.searchHistoryRepository)
    }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    // 输入框内容用 rememberSaveable：进入详情页再返回时保留已输入关键词
    var input by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // “清空搜索历史”确认弹窗
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }

    // 进入页面自动聚焦并唤起键盘
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun submitSearch(keyword: String = input) {
        val kw = keyword.trim()
        if (kw.isNotEmpty() && !ui.isLoading) {
            keyboardController?.hide()
            viewModel.search(kw)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.search_back)
                        )
                    }
                },
                title = {
                    // 全圆角填充式搜索框，颜色取自 colorScheme（动态取色/品牌粉兜底）
                    TextField(
                        value = input,
                        onValueChange = { newText ->
                            // 关键词最多 15 个字，超出截断（粘贴长文本时保留前 15 字）
                            input = newText.take(SEARCH_MAX_LENGTH)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_hint),
                                maxLines = 1
                            )
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (input.isNotEmpty()) {
                                IconButton(onClick = {
                                    // 清空输入框的同时清空搜索结果，回到初始引导页
                                    input = ""
                                    viewModel.clear()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.search_clear)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() })
                    )
                },
                actions = {
                    IconButton(
                        onClick = { submitSearch() },
                        enabled = !ui.isLoading && input.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.search_action),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                // 首次搜索：居中加载
                ui.isLoading && ui.videos.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                // 搜索失败且无内容可展示
                ui.error != null && ui.videos.isEmpty() ->
                    ErrorState(
                        message = appErrorText(ui.error!!),
                        onRetry = { viewModel.search(ui.keyword, ui.page) },
                        modifier = Modifier.fillMaxSize()
                    )

                // 尚未发起搜索：有历史展示搜索历史（输入中按关键词过滤），无历史展示引导提示
                ui.keyword.isBlank() -> {
                    val query = input.trim()
                    val visibleHistory =
                        if (query.isEmpty()) history
                        else history.filter { it.contains(query, ignoreCase = true) }
                    when {
                        visibleHistory.isNotEmpty() ->
                            SearchHistoryPanel(
                                keywords = visibleHistory,
                                onKeywordClick = { keyword ->
                                    // 点击历史词直接回填并发起搜索
                                    input = keyword
                                    submitSearch(keyword)
                                },
                                onDeleteKeyword = { keyword -> viewModel.deleteHistory(keyword) },
                                onClearAll = { showClearHistoryDialog = true }
                            )

                        // 无历史且输入框为空：居中引导；输入中但无匹配则留白
                        query.isEmpty() ->
                            EmptyState(
                                icon = Icons.Filled.Search,
                                title = stringResource(R.string.search_initial_title),
                                subtitle = stringResource(R.string.search_initial_subtitle),
                                modifier = Modifier.fillMaxSize()
                            )
                    }
                }

                // 搜索完成但无结果
                ui.videos.isEmpty() ->
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.search_empty_title),
                        subtitle = stringResource(R.string.search_empty_subtitle, ui.keyword),
                        modifier = Modifier.fillMaxSize()
                    )

                // 结果网格
                else -> SearchResults(
                    ui = ui,
                    onVideoClick = onVideoClick,
                    onPageSelected = { page -> viewModel.search(ui.keyword, page) }
                )
            }

            // 已有结果时再次搜索：顶部细进度条，避免整屏闪烁
            if (ui.isLoading && ui.videos.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // 清空搜索历史确认弹窗（与收藏页清空同一套交互）
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.search_history_clear_all)) },
            text = { Text(stringResource(R.string.search_history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                }) { Text(stringResource(R.string.search_history_clear_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.search_history_clear_no))
                }
            }
        )
    }
}

/**
 * 搜索历史面板：标题行（“搜索历史” + 清空按钮）+ 自动换行的历史词 chips。
 * 点击 chip 直接以该词发起搜索；chip 尾部 X 仅删除单条历史。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistoryPanel(
    keywords: List<String>,
    onKeywordClick: (String) -> Unit,
    onDeleteKeyword: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.search_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onClearAll) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(R.string.search_history_clear_all),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            keywords.forEach { keyword ->
                AssistChip(
                    onClick = { onKeywordClick(keyword) },
                    label = {
                        Text(
                            text = keyword,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(
                                R.string.search_history_delete,
                                keyword
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { onDeleteKeyword(keyword) }
                                .padding(5.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    ui: SearchState,
    onVideoClick: (String) -> Unit,
    onPageSelected: (Int) -> Unit
) {
    val gridState = rememberLazyGridState()
    // 翻页后回到列表顶部
    LaunchedEffect(ui.page) { gridState.scrollToItem(0) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            // 结果总数取自页面头部 .pagehead__count（如“960个视频”）；
            // 页面未声明时回退为当前页条数
            val count = ui.totalCount ?: ui.videos.size
            Text(
                text = stringResource(R.string.search_result_count, ui.keyword, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        items(ui.videos, key = { it.id }) { video ->
            VideoGridCard(video = video, onClick = { onVideoClick(video.id) })
        }
        // 仅当网页页脚解析到 .pager 结构（检测到多页）时显示换页组件
        ui.pagination?.let { pager ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                PaginationBar(
                    currentPage = pager.currentPage,
                    totalPages = pager.totalPages,
                    enabled = !ui.isLoading,
                    onPageSelected = onPageSelected
                )
            }
        }
    }
}
