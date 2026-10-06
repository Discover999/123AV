package com.av123.video.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.ui.components.BrandLogo
import com.av123.video.ui.components.appErrorText
import com.av123.video.ui.screens.channels.ChannelScreen
import com.av123.video.ui.screens.channels.ChannelsScreen
import com.av123.video.ui.screens.detail.DetailScreen
import com.av123.video.ui.screens.diagnostics.DiagnosticsScreen
import com.av123.video.ui.screens.favorites.FavoritesScreen
import com.av123.video.ui.screens.followed.FollowedScreen
import com.av123.video.ui.screens.history.HistoryScreen
import com.av123.video.ui.screens.home.HomeScreen
import com.av123.video.ui.screens.profile.ProfileScreen
import com.av123.video.ui.screens.search.SearchScreen
import com.av123.video.ui.screens.stats.StatsScreen
import com.av123.video.ui.util.EXPANDED_LIST_PANE_WIDTH_DP
import com.av123.video.ui.util.rememberIsExpandedScreen

/** 路由常量 */
object Routes {
    const val HOME = "home"
    const val CHANNELS = "channels"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val FOLLOWED = "followed"
    const val STATS = "stats"
    const val DIAGNOSTICS = "diagnostics"
    const val CHANNEL = "channel?title={title}&path={path}"
    const val DETAIL = "detail/{videoId}"

    /** 展开态右栏空态占位路由（无视频选中时显示品牌空态） */
    const val PANE_EMPTY = "pane_empty"

    /** 栏目列表页路由：path 含斜杠与查询串（如 /cn/all?sort=today），必须编码 */
    fun channel(title: String, path: String): String =
        "channel?title=${android.net.Uri.encode(title)}&path=${android.net.Uri.encode(path)}"

    fun detail(videoId: String) = "detail/$videoId"
}

/** 底部一级目的地 */
enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(Routes.HOME, R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    CHANNELS(Routes.CHANNELS, R.string.tab_channels, Icons.Filled.Category, Icons.Outlined.Category),
    HISTORY(Routes.HISTORY, R.string.tab_history, Icons.Filled.History, Icons.Outlined.History),
    PROFILE(Routes.PROFILE, R.string.tab_profile, Icons.Filled.Person, Icons.Outlined.Person);

    companion object {
        val topLevelRoutes = entries.map { it.route }.toSet()
    }
}

/** 二级页统一横向滑入（与详情页一致，支持预测性返回跟手） */
private fun AnimatedContentTransitionScope<*>.horizontalEnter(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(380, easing = FastOutSlowInEasing)
    ) + fadeIn(tween(220, easing = LinearEasing))

private fun AnimatedContentTransitionScope<*>.horizontalPopExit(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(380, easing = FastOutSlowInEasing)
    ) + fadeOut(tween(220, easing = LinearEasing))

/**
 * 应用根框架：
 * - 紧凑屏（手机竖屏等，宽度 < 840dp）：单 NavHost + 底部导航，所有页整屏切换；
 * - 展开屏（平板/折叠屏展开，宽度 ≥ 840dp）：左 420dp 列表栏 + 右详情栏双栏，
 *   列表点击在右栏开详情，全屏播放时左栏动画收起到 0、详情铺满。
 *
 * 预测性返回：
 * - Manifest 已开启 android:enableOnBackInvokedCallback="true"
 * - Navigation Compose 2.8+ 原生支持预测性返回手势，
 *   下面配置的 popEnter/popExit 转场会随手势进度实时推进（详情页右滑返回、Tab 页滑回桌面）。
 */
@Composable
fun VideoHubApp() {
    if (rememberIsExpandedScreen()) {
        ExpandedShell()
    } else {
        CompactShell()
    }
}

/** 全局瞬时错误 Snackbar 收集（各外壳共用）：网络失败事件统一提示，不打断当前页面 */
@Composable
private fun rememberGlobalSnackbarHostState(): SnackbarHostState {
    val container = LocalAppContainer.current
    val appContext = LocalContext.current.applicationContext
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(container) {
        container.errorReporter.events.collect { event ->
            snackbarHostState.showSnackbar(appErrorText(appContext, event.error))
        }
    }
    return snackbarHostState
}

/** 底部导航条（紧凑态底部 / 展开态左栏底部共用），重复点击一级 Tab 回顶计数在调用侧维护 */
@Composable
private fun AppBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    scrollToTopTicks: MutableMap<String, Int>
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(destination.route) {
                            // 保存状态 + 单例复用，实现 Tab 间快速切换且不重建
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else {
                        // 已在该 Tab：二次点击回顶部
                        scrollToTopTicks[destination.route] =
                            (scrollToTopTicks[destination.route] ?: 0) + 1
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}

/** 底栏显隐动画（与紧凑/展开外壳保持一致的上下滑入滑出） */
@Composable
private fun BottomBarWithAnimation(
    visible: Boolean,
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(tween(300)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + fadeOut(tween(200)),
        content = content
    )
}

/** 紧凑态外壳：单 NavHost 整屏导航（与双栏改造前行为一致） */
@Composable
private fun CompactShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in TopLevelDestination.topLevelRoutes

    // 重复点击已选中的一级 Tab：对应路由计数 +1，页面观察后滚动回顶部
    val scrollToTopTicks = remember { mutableStateMapOf<String, Int>() }

    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomBarWithAnimation(visible = showBottomBar) {
                AppBottomBar(
                    navController = navController,
                    currentRoute = currentRoute,
                    scrollToTopTicks = scrollToTopTicks
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            // Tab 之间：淡入淡出
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(250)) }
        ) {
            listDestinations(
                navController = navController,
                scrollToTopTicks = scrollToTopTicks,
                onVideoClick = { videoId -> navController.navigate(Routes.detail(videoId)) }
            )
            detailDestination(
                onBack = { navController.popBackStack() },
                onChannelClick = { title, path ->
                    navController.navigate(Routes.channel(title, path))
                },
                onVideoClick = { videoId -> navController.navigate(Routes.detail(videoId)) },
                paneCloseButton = false,
                onFullscreenStateChange = null
            )
        }
    }
}

/**
 * 展开态双栏外壳：
 * Row { 左 420dp：Scaffold(列表 NavHost + 底栏)；分隔线；右 weight(1f)：详情 NavHost/空态 }。
 * 全屏播放时左栏宽度动画到 0、底栏与分隔线隐藏，右栏铺满；
 * Row 子树（含详情 PlayerView）全程不增删，只改宽度约束，避免播放器黑帧。
 */
@Composable
private fun ExpandedShell() {
    val listNav = rememberNavController()
    val detailNav = rememberNavController()

    val listEntry by listNav.currentBackStackEntryAsState()
    val detailEntry by detailNav.currentBackStackEntryAsState()
    val listRoute = listEntry?.destination?.route
    val detailRoute = detailEntry?.destination?.route
    val detailOpen = detailRoute != null && detailRoute != Routes.PANE_EMPTY

    val scrollToTopTicks = remember { mutableStateMapOf<String, Int>() }
    // 右栏详情播放器全屏态（由 DetailScreen 上抛）：收起左栏铺满
    var detailFullscreen by rememberSaveable { mutableStateOf(false) }

    // 详情关闭（回退到空态）时复位全屏标记，避免下次打开残留收起状态
    LaunchedEffect(detailOpen) {
        if (!detailOpen) detailFullscreen = false
    }
    // 右栏存在详情时返回键优先 pop 右栏；空态时不拦截，交系统/Tab 默认行为
    BackHandler(enabled = detailOpen) {
        detailNav.popBackStack()
    }

    val listPaneWidth by animateDpAsState(
        targetValue = if (detailFullscreen) 0.dp else EXPANDED_LIST_PANE_WIDTH_DP.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "listPaneWidth"
    )

    val snackbarHostState = rememberGlobalSnackbarHostState()

    Row(Modifier.fillMaxSize()) {
        // —— 左栏：列表侧（一级 Tab + 收藏/关注/统计/栏目/搜索均在此栏内切换） ——
        Box(
            modifier = Modifier
                .width(listPaneWidth)
                .fillMaxHeight()
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    BottomBarWithAnimation(
                        visible = !detailFullscreen &&
                            listRoute in TopLevelDestination.topLevelRoutes
                    ) {
                        AppBottomBar(
                            navController = listNav,
                            currentRoute = listRoute,
                            scrollToTopTicks = scrollToTopTicks
                        )
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = listNav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(innerPadding),
                    // Tab 之间：淡入淡出
                    enterTransition = { fadeIn(animationSpec = tween(300)) },
                    exitTransition = { fadeOut(animationSpec = tween(200)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                    popExitTransition = { fadeOut(animationSpec = tween(250)) }
                ) {
                    listDestinations(
                        navController = listNav,
                        scrollToTopTicks = scrollToTopTicks,
                        // 列表点视频：展开态一律在右栏打开
                        onVideoClick = { videoId -> detailNav.navigate(Routes.detail(videoId)) }
                    )
                }
            }
        }

        // —— 分隔线：全屏时淡出（仅视觉元素，不影响详情组合树） ——
        AnimatedVisibility(
            visible = !detailFullscreen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            VerticalDivider(Modifier.fillMaxHeight())
        }

        // —— 右栏：详情 NavHost，空态显示品牌占位 ——
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            NavHost(
                navController = detailNav,
                startDestination = Routes.PANE_EMPTY,
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                popExitTransition = { fadeOut(animationSpec = tween(180)) }
            ) {
                composable(Routes.PANE_EMPTY) {
                    DetailPaneEmpty()
                }
                detailDestination(
                    onBack = { detailNav.popBackStack() },
                    // 详情页胶囊：栏目流始终在左栏打开
                    onChannelClick = { title, path ->
                        listNav.navigate(Routes.channel(title, path))
                    },
                    // 相关推荐：右栏内继续下钻
                    onVideoClick = { videoId -> detailNav.navigate(Routes.detail(videoId)) },
                    paneCloseButton = true,
                    onFullscreenStateChange = { detailFullscreen = it }
                )
            }
        }
    }
}

/** 右栏空态：品牌字标 + 引导文案 */
@Composable
private fun DetailPaneEmpty() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        BrandLogo(style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pane_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 列表侧目的地集合（紧凑态与展开态左栏共用）：
 * 首页/分栏/历史/我的四个一级 Tab + 搜索/收藏/关注/统计/栏目流。
 * [onVideoClick] 由外壳决定走向：紧凑态压入本 NavHost 详情，展开态打开右栏。
 */
private fun NavGraphBuilder.listDestinations(
    navController: NavHostController,
    scrollToTopTicks: Map<String, Int>,
    onVideoClick: (String) -> Unit
) {
    composable(Routes.HOME) {
        HomeScreen(
            onVideoClick = onVideoClick,
            onSearchClick = { navController.navigate(Routes.SEARCH) },
            // 首页区块右上角"查看全部"：进入 path 对应的频道视频流页（如 /cn/new）
            onSectionMore = { title, path ->
                navController.navigate(Routes.channel(title, path))
            },
            scrollToTopTick = scrollToTopTicks[Routes.HOME] ?: 0
        )
    }
    composable(Routes.CHANNELS) {
        ChannelsScreen(
            onChannelClick = { title, path ->
                navController.navigate(Routes.channel(title, path))
            },
            scrollToTopTick = scrollToTopTicks[Routes.CHANNELS] ?: 0
        )
    }
    composable(Routes.HISTORY) {
        HistoryScreen(
            onVideoClick = onVideoClick,
            scrollToTopTick = scrollToTopTicks[Routes.HISTORY] ?: 0
        )
    }
    composable(Routes.PROFILE) {
        ProfileScreen(
            onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
            onOpenFollowed = { navController.navigate(Routes.FOLLOWED) },
            onOpenStats = { navController.navigate(Routes.STATS) },
            onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
            scrollToTopTick = scrollToTopTicks[Routes.PROFILE] ?: 0
        )
    }
    composable(
        route = Routes.FAVORITES,
        // 我的收藏：与搜索/详情一致横向滑入/滑出，支持预测性返回跟手
        enterTransition = { horizontalEnter() },
        popExitTransition = { horizontalPopExit() }
    ) {
        FavoritesScreen(
            onBack = { navController.popBackStack() },
            onVideoClick = onVideoClick
        )
    }
    composable(
        route = Routes.FOLLOWED,
        // 我的关注：与收藏页一致横向滑入/滑出，支持预测性返回跟手
        enterTransition = { horizontalEnter() },
        popExitTransition = { horizontalPopExit() }
    ) {
        FollowedScreen(
            onBack = { navController.popBackStack() },
            onVideoClick = onVideoClick,
            onOpenChannel = { title, channelPath ->
                navController.navigate(Routes.channel(title, channelPath))
            }
        )
    }
    composable(
        route = Routes.STATS,
        // 观看统计：与收藏/关注一致横向滑入/滑出，支持预测性返回跟手
        enterTransition = { horizontalEnter() },
        popExitTransition = { horizontalPopExit() }
    ) {
        StatsScreen(onBack = { navController.popBackStack() })
    }
    composable(
        route = Routes.DIAGNOSTICS,
        // 网络诊断：与统计/收藏一致横向滑入/滑出，支持预测性返回跟手
        enterTransition = { horizontalEnter() },
        popExitTransition = { horizontalPopExit() }
    ) {
        DiagnosticsScreen(onBack = { navController.popBackStack() })
    }
    composable(
        route = Routes.CHANNEL,
        arguments = listOf(
            navArgument("title") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("path") {
                type = NavType.StringType
                defaultValue = ""
            }
        ),
        // 栏目列表页：与搜索/详情一致横向滑入/滑出，支持预测性返回跟手
        enterTransition = { horizontalEnter() },
        popExitTransition = { horizontalPopExit() }
    ) { entry ->
        ChannelScreen(
            title = entry.arguments?.getString("title").orEmpty(),
            path = entry.arguments?.getString("path").orEmpty(),
            onBack = { navController.popBackStack() },
            onVideoClick = onVideoClick,
            // 索引聚合页（类别/演员/发行/系列）点击条目：进入该条目的视频流页
            onEntryClick = { entryTitle, entryPath ->
                navController.navigate(Routes.channel(entryTitle, entryPath))
            }
        )
    }
    composable(
        route = Routes.SEARCH,
        // 搜索页：与详情页一致横向滑入/滑出，支持预测性返回跟手
        enterTransition = { horizontalEnter() },
        popExitTransition = { horizontalPopExit() }
    ) {
        SearchScreen(
            onBack = { navController.popBackStack() },
            onVideoClick = onVideoClick
        )
    }
}

/**
 * 详情目的地（紧凑态整屏 / 展开态右栏共用同一组合）：
 * @param paneCloseButton 展开态右栏顶栏用关闭（X）语义
 * @param onFullscreenStateChange 全屏态上抛（展开态外壳据此收起左栏）；紧凑态传 null
 */
private fun NavGraphBuilder.detailDestination(
    onBack: () -> Unit,
    onChannelClick: (String, String) -> Unit,
    onVideoClick: (String) -> Unit,
    paneCloseButton: Boolean,
    onFullscreenStateChange: ((Boolean) -> Unit)?
) {
    composable(
        route = Routes.DETAIL,
        arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
        // 详情页：横向滑入/滑出，预测性返回时随手指跟手
        enterTransition = { horizontalEnter() },
        popExitTransition = { horizontalPopExit() }
    ) { entry: NavBackStackEntry ->
        val videoId = entry.arguments?.getString("videoId").orEmpty()
        DetailScreen(
            videoId = videoId,
            onBack = onBack,
            // 类型/制作商/类别等胶囊：按其 href 推入分类视频流页（标题为胶囊文案）
            onChannelClick = onChannelClick,
            onVideoClick = onVideoClick,
            paneCloseButton = paneCloseButton,
            onFullscreenStateChange = onFullscreenStateChange
        )
    }
}
