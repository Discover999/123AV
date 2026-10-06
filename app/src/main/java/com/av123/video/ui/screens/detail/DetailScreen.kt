package com.av123.video.ui.screens.detail

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.av123.video.LocalAppContainer
import com.av123.video.ui.components.player.VideoPlayer
import com.av123.video.R
import com.av123.video.data.model.Episode
import com.av123.video.data.model.InfoChip
import com.av123.video.data.model.Video
import com.av123.video.data.model.VideoInfoRow
import com.av123.video.data.prefs.AppSettings
import com.av123.video.data.prefs.ResizeModeOption
import com.av123.video.ui.components.ErrorState
import com.av123.video.ui.components.LoadingState
import com.av123.video.ui.components.VideoGridCard
import com.av123.video.ui.components.appErrorText
import com.av123.video.ui.components.rememberTogglePopScale
import com.av123.video.ui.components.shimmerPlaceholder
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    videoId: String,
    onBack: () -> Unit,
    onChannelClick: (title: String, path: String) -> Unit,
    /** 点击相关推荐卡片：紧凑态由导航层 push 新详情，展开态（平板/折叠屏）交右栏 */
    onVideoClick: (String) -> Unit = {},
    /** 展开态（双栏）：右栏详情用关闭语义（X）而非返回箭头 */
    paneCloseButton: Boolean = false,
    /** 全屏状态上抛：双栏外壳据此隐藏左栏/底栏让右栏铺满；紧凑态为 null */
    onFullscreenStateChange: ((Boolean) -> Unit)? = null
) {
    val container = LocalAppContainer.current
    val detailViewModel: DetailViewModel = viewModel(key = videoId) {
        DetailViewModel(
            container.videoRepository,
            container.historyRepository,
            container.favoriteRepository,
            videoId
        )
    }
    val video by detailViewModel.video.collectAsStateWithLifecycle()
    val isLoading by detailViewModel.isLoading.collectAsStateWithLifecycle()
    // 无缓存时的详情加载错误：整屏错误态 + 重试（有缓存时走全局 Snackbar）
    val loadError by detailViewModel.loadError.collectAsStateWithLifecycle()
    // 当前视频是否已收藏（收藏数据在本机持久化，返回详情页时即时反映）
    val isFavorite by detailViewModel.isFavorite.collectAsStateWithLifecycle()
    // WebView 拦截到的真实播放地址（m3u8 + Referer 等请求头）
    val playback by detailViewModel.playback.collectAsStateWithLifecycle()
    // 播放地址解析是否失败（超时/播放页不可达），失败后封面展示重试入口
    val resolveFailed by detailViewModel.resolveFailed.collectAsStateWithLifecycle()
    // 拦截 WebView 代次：重试时 +1，驱动隐藏 WebView 销毁重建
    val captureEpoch by detailViewModel.captureEpoch.collectAsStateWithLifecycle()
    // 上次播放位置：起播后续播
    val resumePositionMs by detailViewModel.resumePositionMs.collectAsStateWithLifecycle()
    // 当前选中的选集下标：驱动芯片高亮与抓流目标页切换
    val selectedEpisodeIndex by detailViewModel.selectedEpisodeIndex.collectAsStateWithLifecycle()
    // 详情页相关推荐（无头 WebView 渲染后解析，空列表时整区隐藏）
    val relatedVideos by detailViewModel.related.collectAsStateWithLifecycle()
    val relatedLoading by detailViewModel.relatedLoading.collectAsStateWithLifecycle()
    val canLoadMoreRelated by detailViewModel.canLoadMoreRelated
        .collectAsStateWithLifecycle()
    // 画中画开关：控制全屏回桌面时是否自动进入小窗
    val appSettings by container.settingsStore.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    // 用户点击播放后才交给播放器播放，不自动播放
    var playbackStarted by remember(videoId) { mutableStateOf(false) }
    // 全屏状态：播放器组件内部同步系统栏与 Activity 方向
    var isFullscreen by remember(videoId) { mutableStateOf(false) }

    // 双栏外壳需要跟随全屏隐藏左栏/底栏：每次全屏态变化（含切集重建）都上抛
    LaunchedEffect(isFullscreen, onFullscreenStateChange) {
        onFullscreenStateChange?.invoke(isFullscreen)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 分享链接：优先列表解析到的完整地址，否则用站点根 + 详情相对路径兜底，
    // 两者都缺（异常缓存）时不显示分享按钮
    val shareUrl = remember(video, container.siteBaseUrl) {
        val v = video ?: return@remember null
        when {
            v.sourcePageUrl.isNotBlank() -> v.sourcePageUrl
            v.detailPath.isNotBlank() -> when {
                v.detailPath.startsWith("http") -> v.detailPath
                v.detailPath.startsWith("/") ->
                    container.siteBaseUrl.trimEnd('/') + v.detailPath
                else -> container.siteBaseUrl.trimEnd('/') + "/" + v.detailPath
            }
            else -> null
        }
    }
    val resolvingText = stringResource(R.string.detail_resolving)
    val favoriteAddedText = stringResource(R.string.detail_favorite_added)
    val favoriteRemovedText = stringResource(R.string.detail_favorite_removed)
    val autoNextText = stringResource(R.string.detail_auto_next)

    // 收藏按钮：写入/取消本机收藏，并用 Snackbar 反馈当前状态
    val onToggleFavorite: () -> Unit = {
        val nowFavorite = detailViewModel.toggleFavorite()
        scope.launch {
            snackbarHostState.showSnackbar(
                if (nowFavorite) favoriteAddedText else favoriteRemovedText
            )
        }
    }

    Scaffold(
        // 全屏时隐藏 TopAppBar。始终走同一个 Scaffold 分支：全屏切换只改播放区
        // 尺寸、不替换组合树，避免 AndroidView(PlayerView) 销毁重建产生黑帧
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Text(
                            text = video?.detailCodeFromInfoRows().orEmpty(),
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            // 双栏右栏：关闭详情回到空态占位；紧凑态：返回上一页
                            if (paneCloseButton) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.detail_pane_close)
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.detail_back)
                                )
                            }
                        }
                    },
                    actions = {
                        // 系统分享：标题 + 详情页链接（纯文本，交给系统选择器）
                        shareUrl?.let { url ->
                            val shareDescription = stringResource(R.string.detail_share)
                            val shareContext = LocalContext.current
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${video?.title.orEmpty()}\n$url"
                                    )
                                }
                                runCatching {
                                    shareContext.startActivity(
                                        Intent.createChooser(intent, shareDescription)
                                    )
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = shareDescription
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when {
            // 有数据（含首页缓存条目）就立即渲染内容：封面/标题先出来，
            // 详情字段在网络请求返回后同位置补全，避免整屏骨架/黑屏等待
            video != null -> Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                DetailContent(
                    video = video!!,
                    playbackUrl = playback?.url.orEmpty(),
                    playbackHeaders = playback?.headers.orEmpty(),
                    playbackStarted = playbackStarted,
                    resolveFailed = resolveFailed,
                    isFullscreen = isFullscreen,
                    isFavorite = isFavorite,
                    selectedEpisodeIndex = selectedEpisodeIndex,
                    onSelectEpisode = detailViewModel::selectEpisode,
                    onToggleFavorite = onToggleFavorite,
                    onFullscreenChange = { isFullscreen = it },
                    onPlay = {
                        // 已拦截到 m3u8：切换为播放器并开始播放；
                        // 解析失败：重建拦截 WebView 重试；
                        // 尚未解析到：提示等待（无头 WebView 正在播放页抓流）
                        when {
                            playback != null -> playbackStarted = true
                            resolveFailed -> detailViewModel.retryResolve()
                            else -> scope.launch { snackbarHostState.showSnackbar(resolvingText) }
                        }
                    },
                    onRetry = { detailViewModel.retryResolve() },
                    onChipClick = { chip -> onChannelClick(chip.text, chip.path) },
                    relatedVideos = relatedVideos,
                    relatedLoading = relatedLoading,
                    canLoadMoreRelated = canLoadMoreRelated,
                    onLoadMoreRelated = detailViewModel::loadMoreRelated,
                    onVideoClick = onVideoClick,
                    onPlaybackReady = detailViewModel::onPlaybackReady,
                    onPlaybackError = detailViewModel::onPlaybackError,
                    startPositionMs = resumePositionMs,
                    onPlaybackProgress = detailViewModel::savePlaybackProgress,
                    onPlayingElapsed = detailViewModel::onPlayingElapsed,
                    pipEnabled = appSettings.pipEnabled,
                    resizeMode = appSettings.videoResizeMode,
                    onResizeModeChange = { mode ->
                        scope.launch { container.settingsStore.setVideoResizeMode(mode) }
                    },
                    onEpisodeEnded = {
                        // 自动连播（可在"我的-播放设置"关闭）：存在下一集时切换，
                        // 末集或开关关闭时不动作，播放器停留/显示重播
                        val nextIndex = selectedEpisodeIndex + 1
                        if (appSettings.autoPlayNext &&
                            nextIndex < (video?.episodes?.size ?: 0)
                        ) {
                            detailViewModel.selectEpisode(nextIndex)
                            scope.launch {
                                snackbarHostState.showSnackbar(autoNextText)
                            }
                        }
                    }
                )
                // 详情数据就绪后延迟 500ms 再创建拦截 WebView：
                // 首次创建 WebView 会初始化 Chromium 内核（主线程数百 ms），
                // 错开页面首帧/封面图加载，避免进详情页时卡顿。
                // 已有播放地址（缓存命中或本轮已抓到）时不再抓流：
                // 缓存命中无需重复解析；刚抓到后也立即销毁隐藏页，省掉其后续分片流量
                if (!isLoading && playback == null) {
                    var captureReady by remember(video!!.id) { mutableStateOf(false) }
                    LaunchedEffect(video!!.id) {
                        kotlinx.coroutines.delay(CAPTURE_WEBVIEW_DELAY_MS.milliseconds)
                        captureReady = true
                    }
                    if (captureReady) {
                        // 无头隐藏 WebView：加载播放器 iframe 页，拦截真实 m3u8 地址。
                        // captureEpoch 变化（重试/缓存失效后重抓）时 DisposableEffect 重建 WebView
                        // 选中非首集时直连该集 iframe 播放页（首集传 null，
                        // 沿用 embedUrl + 超时回退详情页整页的默认路径）
                        val episodeTargetUrl = video!!.episodes
                            .getOrNull(selectedEpisodeIndex)
                            ?.url
                            ?.takeIf { selectedEpisodeIndex > 0 }
                        M3U8CaptureWebView(
                            video = video!!,
                            epoch = captureEpoch,
                            onCaptured = detailViewModel::onPlaybackCaptured,
                            onFailed = { detailViewModel.onResolveFailed() },
                            targetUrl = episodeTargetUrl
                        )
                    }
                }
                // 相关推荐：详情就绪后另起一个无头 WebView 真渲染详情页，
                // 提取前端动态渲染的 .card 推荐区；拿到结果后相关区显示、本组件卸载
                if (relatedLoading && video!!.sourcePageUrl.isNotBlank()) {
                    RelatedExtractWebView(
                        pageUrl = video!!.sourcePageUrl,
                        onExtracted = detailViewModel::onRelatedHtml,
                        onFinished = detailViewModel::onRelatedFinished
                    )
                }
            }
            isLoading -> LoadingState(Modifier.padding(padding).fillMaxSize())
            // 网络/解析失败且无缓存：错误态可重试，不再误报“内容不存在或已下线”
            loadError != null -> ErrorState(
                message = appErrorText(loadError!!),
                onRetry = detailViewModel::loadDetail,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
            else -> Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.detail_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Video.detailCodeFromInfoRows(): String = infoRows
    .firstOrNull { row ->
        row.label.contains("代码", ignoreCase = true) ||
            row.label.contains("代碼", ignoreCase = true) ||
            row.label.contains("code", ignoreCase = true)
    }
    ?.value
    ?.trim()
    .orEmpty()

@Composable
private fun DetailContent(
    video: Video,
    playbackUrl: String,
    playbackHeaders: Map<String, String>,
    playbackStarted: Boolean,
    resolveFailed: Boolean,
    isFullscreen: Boolean,
    isFavorite: Boolean,
    selectedEpisodeIndex: Int,
    onSelectEpisode: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onChipClick: (InfoChip) -> Unit,
    relatedVideos: List<Video>,
    relatedLoading: Boolean,
    canLoadMoreRelated: Boolean,
    onLoadMoreRelated: () -> Unit,
    onVideoClick: (String) -> Unit,
    onPlaybackReady: () -> Unit,
    onPlaybackError: () -> Unit,
    startPositionMs: Long,
    onPlaybackProgress: (Int, Long, Long) -> Unit,
    onPlayingElapsed: (Long) -> Unit = {},
    pipEnabled: Boolean = true,
    resizeMode: ResizeModeOption = ResizeModeOption.FIT,
    onResizeModeChange: (ResizeModeOption) -> Unit = {},
    onEpisodeEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 列表状态提升在全屏分支之外：进入全屏 LazyColumn 退出组合后，
    // 回到竖屏仍保持原滚动位置
    val listState = rememberLazyListState()
    Column(modifier = modifier.fillMaxSize()) {
        // 播放区永远是第一个子节点：全屏切换只改尺寸、不换组合树，
        // 保证 AndroidView(PlayerView) 调用位置稳定，避免 SurfaceView 重建黑帧；
        // 播放区也不放进 LazyColumn item，避免在懒加载容器内做全屏尺寸突变导致半屏。
        // 尺寸由外部显式给定，PlayerArea 内部不再叠加。
        PlayerArea(
            video = video,
            playbackUrl = playbackUrl,
            playbackHeaders = playbackHeaders,
            playbackStarted = playbackStarted,
            resolveFailed = resolveFailed,
            isFullscreen = isFullscreen,
            selectedEpisodeIndex = selectedEpisodeIndex,
            onFullscreenChange = onFullscreenChange,
            onPlayClick = onPlay,
            onRetry = onRetry,
            onPlaybackReady = onPlaybackReady,
            onPlaybackError = onPlaybackError,
            startPositionMs = startPositionMs,
            onPlaybackProgress = onPlaybackProgress,
            onPlayingElapsed = onPlayingElapsed,
            pipEnabled = pipEnabled,
            resizeMode = resizeMode,
            onResizeModeChange = onResizeModeChange,
            onEpisodeEnded = onEpisodeEnded,
            modifier = if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            }
        )

        if (!isFullscreen) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState
            ) {
                item {
                    DetailBody(
                        video = video,
                        isFavorite = isFavorite,
                        selectedEpisodeIndex = selectedEpisodeIndex,
                        onSelectEpisode = onSelectEpisode,
                        onToggleFavorite = onToggleFavorite,
                        onChipClick = onChipClick,
                        relatedVideos = relatedVideos,
                        relatedLoading = relatedLoading,
                        canLoadMoreRelated = canLoadMoreRelated,
                        onLoadMoreRelated = onLoadMoreRelated,
                        onVideoClick = onVideoClick
                    )
                }
            }
        }
    }
}

/**
 * 播放区下方的详情正文（标题/元信息/收藏/简介/信息行/剧集），
 * 作为 LazyColumn 的单个 item 随页面滚动。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(
    video: Video,
    isFavorite: Boolean,
    selectedEpisodeIndex: Int,
    onSelectEpisode: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onChipClick: (InfoChip) -> Unit,
    relatedVideos: List<Video>,
    relatedLoading: Boolean,
    canLoadMoreRelated: Boolean,
    onLoadMoreRelated: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        ExpandableTitle(text = video.title)
        Spacer(Modifier.height(10.dp))

        // 元信息：编号 / 时长 / 观看数 / 发布时间 / 年份 / 分类
        Text(
            text = buildList {
                if (video.code.isNotBlank()) add(video.code)
                if (video.durationText.isNotBlank()) add(video.durationText)
                if (video.viewsDisplay.isNotBlank()) add(video.viewsDisplay)
                if (video.publishedAgo.isNotBlank()) add(video.publishedAgo)
                if (video.year > 0) add(video.year.toString())
                add(video.category)
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // 整行收藏按钮：播放由封面中央圆形按钮承担，这里只保留收藏操作。
        // 已收藏时切换为 primaryContainer 底 + 实心爱心；底色平滑过渡、爱心弹出、文案淡入
        val favoriteContainer by animateColorAsState(
            targetValue = if (isFavorite) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            animationSpec = spring(),
            label = "favoriteButtonContainer"
        )
        val favoriteContent by animateColorAsState(
            targetValue = if (isFavorite) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            animationSpec = spring(),
            label = "favoriteButtonContent"
        )
        val favoriteHeartScale = rememberTogglePopScale(active = isFavorite)
        Button(
            onClick = onToggleFavorite,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = favoriteContainer,
                contentColor = favoriteContent
            )
        ) {
            Icon(
                imageVector = if (isFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Filled.FavoriteBorder
                },
                contentDescription = null,
                modifier = Modifier.graphicsLayer {
                    scaleX = favoriteHeartScale
                    scaleY = favoriteHeartScale
                }
            )
            Spacer(Modifier.size(8.dp))
            AnimatedContent(
                targetState = isFavorite,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(120))
                },
                label = "favoriteLabel"
            ) { favorited ->
                Text(
                    stringResource(
                        if (favorited) R.string.detail_favorited
                        else R.string.detail_favorite
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 选集紧贴收藏按钮下方：横向滑动的圆角按钮组，
        // 选中集主题色填充；放在按钮区便于播放前快速选源
        if (video.episodes.size > 1) {
            EpisodeSelector(
                episodes = video.episodes,
                selectedIndex = selectedEpisodeIndex,
                onSelect = onSelectEpisode
            )
            Spacer(Modifier.height(20.dp))
        }

        if (video.synopsis.isNotBlank()) {
            Text(
                text = stringResource(R.string.detail_synopsis),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            ExpandableSynopsis(text = video.synopsis)
            Spacer(Modifier.height(20.dp))
        }

        if (video.infoRows.isNotEmpty()) {
            Text(
                text = stringResource(R.string.detail_info),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(10.dp))
            // 详情信息逐行排列：代码/发布日期等为纯文本，
            // 类型/演员/制作商/系列/类别/标签为可点击胶囊，点击进入对应视频流；
            // 缺失的行网页中不存在，自然不渲染
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                video.infoRows.forEach { row ->
                    DetailInfoRow(row = row, onChipClick = onChipClick)
                }
            }
        }

        // 相关推荐：非懒加载 2 列网格（整体作为 LazyColumn 的单个 item，
        // 禁止 LazyVerticalGrid 嵌套造成同方向滚动冲突）；
        // 提取中显示骨架，空结果整区不渲染
        if (relatedVideos.isNotEmpty() || relatedLoading) {
            Spacer(Modifier.height(24.dp))
            RelatedSection(
                videos = relatedVideos,
                loading = relatedLoading,
                canLoadMore = canLoadMoreRelated,
                onLoadMore = onLoadMoreRelated,
                onVideoClick = onVideoClick
            )
        }
    }
}

/**
 * 详情页底部“相关推荐”：titleMedium 标题 + 2 列卡片网格。
 * 用 Column + chunked(2) 的普通 Row 实现，行高 10dp、列距 8dp；
 * 加载中给 2×2 封面+文本骨架；数据到达后骨架消失。
 * 完整池超过一页时，网格下方显示“加载更多”，点击就地追加一页（纯本地展开）。
 */
@Composable
private fun RelatedSection(
    videos: List<Video>,
    loading: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.detail_related_title),
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(12.dp))

    if (videos.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            videos.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { item ->
                        VideoGridCard(
                            video = item,
                            onClick = { onVideoClick(item.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 奇数个时第二格留空占位，保持两列宽度一致
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (canLoadMore) {
            Spacer(Modifier.height(12.dp))
            // 整行描边按钮：向下箭头 + 文案，展开后剩余条数不足一页时隐藏
            TextButton(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(text = stringResource(R.string.related_load_more))
            }
        }
    } else if (loading) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) {
                        RelatedCardSkeleton(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 相关推荐卡片骨架：封面比例块 + 两行文字 */
@Composable
private fun RelatedCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .shimmerPlaceholder()
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerPlaceholder()
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerPlaceholder()
        )
    }
}

/**
 * 选集按钮组：“选集”标题下方一条横向滑动的圆角按钮带。
 * - 选中集主题色实心填充 + onPrimary 文字 SemiBold，未选集 surfaceVariant 底；
 *   颜色用 [animateColorAsState] 平滑过渡
 * - 切集（含进入详情自动恢复上次选集）时自动横向滚动到选中项，避免其停在视口外
 * - 集名为空时兜底“第N集”；LazyRow 嵌在竖屏 LazyColumn 单 item 内（仅横向滚动，
 *   与外层方向正交，无同方向嵌套冲突）
 */
@Composable
private fun EpisodeSelector(
    episodes: List<Episode>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    // 选中项变化（手动切集/历史续播恢复）时滚动到可见位置
    LaunchedEffect(selectedIndex, episodes.size) {
        if (selectedIndex in episodes.indices) listState.animateScrollToItem(selectedIndex)
    }
    Column {
        Text(
            text = stringResource(R.string.detail_episodes),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(episodes) { index, episode ->
                val selected = index == selectedIndex
                val accent = MaterialTheme.colorScheme.primary
                val container by animateColorAsState(
                    targetValue = if (selected) accent
                    else MaterialTheme.colorScheme.surfaceVariant,
                    label = "episodeContainer"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "episodeContent"
                )
                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .widthIn(min = 54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(container)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = episode.name.ifBlank {
                            stringResource(R.string.detail_episode_n, index + 1)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold
                        else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 详情标签按钮，对标网页 .chip：全圆角胶囊、表面色底、灰字、13px 中号字；
 * 按下时平滑切换为主题 primary 半透明底 + primary 文字
 *（动态取色跟随壁纸，关闭时为品牌粉）。
 */
@Composable
private fun MetaChip(
    text: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = MaterialTheme.colorScheme.primary
    val container by animateColorAsState(
        targetValue = if (pressed) accent.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        label = "chipContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (pressed) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipContent"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 13.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * 详情信息行：左侧固定宽度字段名，右侧为纯文本值或可点击胶囊按钮组。
 * chips 为空（代码/发布日期等纯文本行）显示文本；
 * 有 chips（类型/演员/制作商/系列/类别/标签）显示胶囊，点击携带 href 路径跳转对应视频流。
 * 字段名与右侧内容整体垂直居中对齐，胶囊换行时标签在区块上下居中。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailInfoRow(
    row: VideoInfoRow,
    onChipClick: (InfoChip) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(72.dp)
                .align(Alignment.CenterVertically)
        )
        if (row.chips.isEmpty()) {
            Text(
                text = row.value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                row.chips.forEach { chip ->
                    MetaChip(text = chip.text, onClick = { onChipClick(chip) })
                }
            }
        }
    }
}

/**
 * 影片标题，交互对齐主流视频客户端（YouTube/NewPipe/B站）的通用做法：
 * - 折叠态最多两行并自然省略；确认溢出后第二行末尾内联主题色“…更多”，
 *   **整个标题区域可点**（带水波纹 + Button 语义），不再是只能点小箭头；
 * - 展开态显示全文，文末内联主题色“收起”，再次点整段折回；
 * - 短标题（两行内放得下）不渲染提示、整段不可点；
 * - 高度变化由 [animateContentSize] 平滑过渡。
 *
 * 截断点不做逐字符二分：先让 Text 用自然省略号渲染全文，onTextLayout 拿到
 * 第二行右端，再按“…更多”的[TextMeasurer]实测像素宽度用 getOffsetForHorizontal
 * 反查切点——对中英日文/全角半角混排都准确，且只测量一次。
 */
@Composable
private fun ExpandableTitle(text: String) {
    // 详情从缓存条目补全为完整标题时全部重置，避免沿用上一个标题的展开/截断状态
    var expanded by remember(text) { mutableStateOf(false) }
    var overflowing by remember(text) { mutableStateOf(false) }
    // 折叠态保留的正文字符数；-1 = 尚未测量，首帧先渲染全文参与溢出检测
    var cutIndex by remember(text) { mutableIntStateOf(-1) }

    val titleStyle = MaterialTheme.typography.headlineSmall
    val primaryColor = MaterialTheme.colorScheme.primary
    val expandLabel = stringResource(R.string.title_expand)
    val collapseLabel = stringResource(R.string.detail_collapse)
    val toggleStyle = SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)
    val textMeasurer = rememberTextMeasurer()

    // “…更多”实测宽度，用于从第二行右端反推切点
    val suffix = "…$expandLabel"
    val suffixWidthPx = remember(textMeasurer, titleStyle, suffix) {
        textMeasurer.measure(text = AnnotatedString(suffix), style = titleStyle).size.width
    }

    val displayText = buildAnnotatedString {
        when {
            expanded -> {
                append(text)
                append(' ')
                withStyle(toggleStyle) { append(collapseLabel) }
            }
            cutIndex >= 0 -> {
                append(text, 0, cutIndex.coerceIn(0, text.length))
                withStyle(toggleStyle) { append(suffix) }
            }
            else -> append(text)
        }
    }

    Text(
        text = displayText,
        style = titleStyle,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (expanded || overflowing) return@Text
            if (!result.hasVisualOverflow) return@Text
            overflowing = true
            // 从第二行右边界减去“…更多”宽度反查字符偏移
            val targetX = (result.size.width - suffixWidthPx).toFloat()
            val centerY = (result.getLineTop(1) + result.getLineBottom(1)) / 2f
            var cut = result.getOffsetForPosition(Offset(targetX, centerY))
            // getOffsetForPosition 对 CJK 可能向上取整到下一字符，逐字符回退到
            // “切点实际水平位置 + 提示宽度 <= 行宽”为止，既贴满行尾又不溢到第三行
            while (cut > 0 &&
                result.getHorizontalPosition(cut, true) + suffixWidthPx > result.size.width
            ) {
                cut--
            }
            cutIndex = cut.coerceIn(0, text.length)
        },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(
                enabled = overflowing,
                role = Role.Button,
                onClickLabel = if (expanded) collapseLabel else expandLabel
            ) { expanded = !expanded }
    )
}

/**
 * 影片简介：默认折叠为 3 行，内容超出时显示“查看更多”，展开后可“收起”。
 * 是否溢出由文本布局结果判定（与网页 watch__more 的 overflowing 逻辑一致）。
 */
@Composable
private fun ExpandableSynopsis(text: String) {
    var expanded by remember { mutableStateOf(false) }
    var overflowing by remember { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (!expanded && result.hasVisualOverflow) overflowing = true
        }
    )
    if (overflowing) {
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.detail_collapse else R.string.detail_expand
                ),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.size(2.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }
    }
}

/**
 * 播放区（item 内容）：
 * - 已开始播放且存在播放地址时，用自定义 Media3/ExoPlayer 播放器接管渲染与控制
 * - 未开始播放时显示占位封面，中央圆形按钮随解析状态切换：
 *   解析中=加载转圈、解析失败=重试图标+重试入口、已就绪=播放三角
 */
@Composable
private fun PlayerArea(
    video: Video,
    playbackUrl: String,
    playbackHeaders: Map<String, String>,
    playbackStarted: Boolean,
    resolveFailed: Boolean,
    isFullscreen: Boolean,
    selectedEpisodeIndex: Int,
    onFullscreenChange: (Boolean) -> Unit,
    onPlayClick: () -> Unit,
    onRetry: () -> Unit,
    onPlaybackReady: () -> Unit,
    onPlaybackError: () -> Unit,
    startPositionMs: Long,
    onPlaybackProgress: (Int, Long, Long) -> Unit,
    onPlayingElapsed: (Long) -> Unit = {},
    pipEnabled: Boolean = true,
    resizeMode: ResizeModeOption = ResizeModeOption.FIT,
    onResizeModeChange: (ResizeModeOption) -> Unit = {},
    onEpisodeEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val detailCode = video.detailCodeFromInfoRows()

    if (playbackStarted && playbackUrl.isNotBlank()) {
        // 尺寸（竖屏 16:9 / 全屏 fillMaxSize）由调用方通过 modifier 显式给定，
        // 内部不再叠加，保证全屏切换时 PlayerView 只做同一节点的尺寸变化
        VideoPlayer(
            url = playbackUrl,
            headers = playbackHeaders,
            title = detailCode,
            isFullscreen = isFullscreen,
            onFullscreenChange = onFullscreenChange,
            onPlaybackReady = onPlaybackReady,
            onPlaybackError = onPlaybackError,
            startPositionMs = startPositionMs,
            episodeIndex = selectedEpisodeIndex,
            onPlaybackProgress = onPlaybackProgress,
            onPlayingElapsed = onPlayingElapsed,
            pipEnabled = pipEnabled,
            resizeMode = resizeMode,
            onResizeModeChange = onResizeModeChange,
            onEpisodeEnded = onEpisodeEnded,
            modifier = modifier.background(Color.Black)
        )
    } else {
        // 解析三态：解析中（转圈）/ 解析失败（重试）/ 已就绪（播放三角）
        val isResolving = playbackUrl.isBlank() && !resolveFailed
        Box(
            modifier = modifier
                .shimmerPlaceholder()
                // 解析中点击无意义，禁用点击；失败态点击任意处重试，就绪态点击播放
                .clickable(enabled = !isResolving) {
                    if (resolveFailed) onRetry() else onPlayClick()
                },
            contentAlignment = Alignment.Center
        ) {
            // 与首页列表流一致的封面图；加载中/无封面时显示波纹空白占位
            if (video.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = video.coverUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            // 底部暗色渐变遮罩，保证播放按钮与提示文字可读
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.55f)
                        )
                    )
            )
            // 中央圆形按钮：独立居中，位置不随下方提示出现/消失而移动，
            // 避免“正在解析”提示消失时按钮突然下坠的跳动
            // 流行播放按钮样式：半透明深色圆底 + 白色描边 + 柔光；
            // 内部图标随解析状态切换为 转圈/重试/播放三角
            Box(contentAlignment = Alignment.Center) {
                // 柔光用径向渐变手绘，不用 shadowElevation：
                // Android 硬件阴影对圆形 Outline 只做凸多边形近似，投影会呈八边形，
                // 按钮半透明、叠在明亮封面上时该八边形阴影隐约可见
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Black.copy(alpha = 0f)
                                    )
                                )
                            )
                        }
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f)),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isResolving -> CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )

                            resolveFailed -> Icon(
                                imageVector = Icons.Filled.Replay,
                                contentDescription = stringResource(R.string.detail_retry),
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )

                            else -> Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.detail_play),
                                tint = Color.White,
                                modifier = Modifier
                                    .size(46.dp)
                                    // 三角形视觉重心偏左，右移 2dp 做光学居中
                                    .offset(x = 2.dp)
                            )
                        }
                    }
                }
            }

            // 状态提示：锚定在播放区底部，与居中按钮解耦，
            // 出现/消失只在底部区域变化，不会推动中央按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // 解析中：半透明胶囊提示，弱化干扰
                    isResolving -> Text(
                        text = stringResource(R.string.detail_resolving),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    // 失败：失败原因胶囊 + 明确的重试按钮
                    resolveFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.detail_resolve_failed),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        TextButton(
                            onClick = onRetry,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.detail_retry),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
