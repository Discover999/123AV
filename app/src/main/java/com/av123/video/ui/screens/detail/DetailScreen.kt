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
import com.av123.video.data.prefs.ResizeModeOption
import com.av123.video.ui.components.ErrorState
import com.av123.video.ui.components.LoadingSkeleton
import com.av123.video.ui.components.LoadingState
import com.av123.video.ui.components.VideoGridCard
import com.av123.video.ui.components.appErrorText
import com.av123.video.ui.components.rememberTogglePopScale
import com.av123.video.ui.components.shimmerPlaceholder
import com.av123.video.ui.theme.Motion
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
    val appSettingsState by container.appSettingsState.collectAsStateWithLifecycle()
    val appSettings = appSettingsState.settings
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
                // P3.1：同一时刻只允许一个隐藏 WebView。
                // 播放地址解析完成或失败后，才启动相关推荐渲染，避免两个 Chromium 实例并行抢网络/内存。
                if (!isLoading && playback == null && !resolveFailed) {
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
                if (relatedLoading &&
                    video!!.sourcePageUrl.isNotBlank() &&
                    (playback != null || resolveFailed)
                ) {
                    RelatedExtractWebView(
                        pageUrl = video!!.sourcePageUrl,
                        onExtracted = detailViewModel::onRelatedHtml,
                        onFinished = detailViewModel::onRelatedFinished
                    )
                }
            }
            isLoading -> LoadingState(
                Modifier.padding(padding).fillMaxSize(),
                variant = LoadingSkeleton.Detail
            )
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

internal fun Video.detailCodeFromInfoRows(): String = infoRows
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
    modifier: Modifier = Modifier,
    onPlayingElapsed: (Long) -> Unit = {},
    pipEnabled: Boolean = true,
    resizeMode: ResizeModeOption = ResizeModeOption.FIT,
    onResizeModeChange: (ResizeModeOption) -> Unit = {},
    onEpisodeEnded: () -> Unit = {}
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
            animationSpec = Motion.MorphColorSpring,
            label = "favoriteButtonContainer"
        )
        val favoriteContent by animateColorAsState(
            targetValue = if (isFavorite) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            animationSpec = Motion.MorphColorSpring,
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
                    fadeIn(animationSpec = Motion.FadeThrough) togetherWith
                        fadeOut(animationSpec = Motion.Overlay)
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
