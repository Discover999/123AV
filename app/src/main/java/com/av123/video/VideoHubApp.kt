package com.av123.video

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.av123.video.data.db.LegacyDataMigrator
import com.av123.video.data.db.VideoHubDatabase
import com.av123.video.data.net.DataSourceProbe
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.net.HttpClient
import com.av123.video.data.prefs.AppSettings
import com.av123.video.data.prefs.SettingsStore
import com.av123.video.data.repository.BackupRepository
import com.av123.video.data.repository.CacheManager
import com.av123.video.data.repository.FavoriteRepository
import com.av123.video.data.repository.FollowedActressRepository
import com.av123.video.data.repository.HistoryRepository
import com.av123.video.data.repository.PageCacheStore
import com.av123.video.data.repository.SearchHistoryRepository
import com.av123.video.data.repository.VideoRepository
import com.av123.video.data.source.JsoupSiteAdapter
import com.av123.video.data.source.JsoupSiteConfig
import com.av123.video.data.source.JsoupVideoDataSource
import com.av123.video.data.source.SiteConfigLoader
import com.av123.video.data.source.SiteConfigStore
import com.av123.video.data.source.VideoSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Application 入口，同时充当轻量服务容器（ServiceLocator）与 Coil 全局 ImageLoader 工厂。
 * 已使用 [JsoupVideoDataSource] 从网页解析真实数据。
 */
class VideoHubApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * 全 App 唯一的图片加载器：
     * - 网络层复用 [HttpClient] 的 OkHttpClient（连接池/Dispatcher 共享）；
     * - 显式内存缓存（可用内存 25%）与磁盘缓存（cacheDir/image_cache，150MB LRU），
     *   冷启动与断网时封面可直接从本地显示，不依赖默认实现的隐式行为；
     * - 200ms 淡入，避免图片逐张突兀出现。
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(HttpClient.client)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(java.io.File(cacheDir, "image-cache"))
                    .maxSizeBytes(150L * 1024L * 1024L)
                    .build()
            }
            .crossfade(200)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Room 数据库 + 首页/列表磁盘缓存 */
    val database = VideoHubDatabase.get(appContext)
    val pageCacheStore = PageCacheStore(database.pageCacheDao())

    /** 内置 → 本地缓存 → 代码兜底的站点配置；远程刷新暂未启用 */
    val siteConfigStore: SiteConfigStore = SiteConfigStore(
        bundled = SiteConfigLoader.loadBundled(appContext),
        cacheFile = java.io.File(appContext.filesDir, "site-config-cache.json")
    )

    /** 当前生效的站点契约（缓存版本高于内置时使用缓存） */
    val siteConfig: JsoupSiteConfig = siteConfigStore.current

    /** 当前数据源站点地址（“我的-数据源检测”、分享链接兜底共用） */
    val siteBaseUrl: String = siteConfig.baseUrl

    /** 当前数据源：从网页解析真实数据 */
    val dataSource: JsoupVideoDataSource = JsoupVideoDataSource(siteConfig)

    /** 仓库层唯一依赖的站点契约；后续替换解析实现不影响 UI/仓库 */
    val videoSite: VideoSite = JsoupSiteAdapter(dataSource, siteConfig)

    /** 全局瞬时错误事件总线（页面有内容时的网络失败经此发全局 Snackbar） */
    val errorReporter: ErrorReporter = ErrorReporter()

    val videoRepository: VideoRepository =
        VideoRepository(videoSite, appScope, errorReporter, pageCacheStore)
    val backupRepository: BackupRepository = BackupRepository(database)
    val cacheManager: CacheManager = CacheManager(appContext, pageCacheStore)
    val historyRepository: HistoryRepository =
        HistoryRepository(database.historyDao(), database.watchDailyDao())
    val favoriteRepository: FavoriteRepository =
        FavoriteRepository(database.favoriteDao(), database.favoriteGroupDao())
    val followedActressRepository: FollowedActressRepository =
        FollowedActressRepository(database.followedActressDao())
    val searchHistoryRepository: SearchHistoryRepository =
        SearchHistoryRepository(database.searchKeywordDao())
    val dataSourceProbe: DataSourceProbe = DataSourceProbe(siteUrl = siteBaseUrl)
    val settingsStore: SettingsStore = SettingsStore(appContext)

    /**
     * 全局设置热流：DataStore 只订阅一次并在 App 进程内保持活跃，
     * 各页面共享同一份内存状态，避免每页各自冷订阅、重复读盘。
     */
    val appSettings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    init {
        appScope.launch {
            // 旧 DataStore 历史/收藏/搜索词一次性迁入 Room（幂等）
            LegacyDataMigrator(appContext, database).migrateIfNeeded()
        }
        appScope.launch {
            // 错开首页首帧与抓取；远程配置只落缓存，下次冷启动生效
            delay(2_000)
            siteConfigStore.refreshFromNetwork(BuildConfig.SITE_CONFIG_URL)
        }
    }
}

/** 让 Compose 层级可以直接拿到容器中的仓库 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 尚未提供")
}
