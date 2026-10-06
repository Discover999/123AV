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
import com.av123.video.data.prefs.SettingsStore
import com.av123.video.data.repository.FavoriteRepository
import com.av123.video.data.repository.FollowedActressRepository
import com.av123.video.data.repository.HistoryRepository
import com.av123.video.data.repository.PageCacheStore
import com.av123.video.data.repository.SearchHistoryRepository
import com.av123.video.data.repository.VideoRepository
import com.av123.video.data.source.JsoupSiteConfig
import com.av123.video.data.source.JsoupVideoDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /** 当前数据源站点地址（“我的-数据源检测”、分享链接兜底共用） */
    val siteBaseUrl = "https://123av.com/"

    /** Room 数据库 + 首页/列表磁盘缓存 */
    val database = VideoHubDatabase.get(appContext)
    private val pageCacheStore = PageCacheStore(database.pageCacheDao())

    /** 编译期内置站点配置（远程配置缺失/失败时的最终兜底） */
    private val defaultSiteConfig = JsoupSiteConfig(
        baseUrl = siteBaseUrl,
        localePath = "/cn",
        categoryPaths = mapOf(
            "新发布" to "/new",
            "最近添加" to "/recent"
        ),
        // 搜索地址：{baseUrl}/cn/search?keyword=<关键词>
        searchQueryParam = "keyword"
    )

    /** 当前数据源：从网页解析真实数据 */
    val dataSource: JsoupVideoDataSource = JsoupVideoDataSource(defaultSiteConfig)

    /** 全局瞬时错误事件总线（页面有内容时的网络失败经此发全局 Snackbar） */
    val errorReporter: ErrorReporter = ErrorReporter()

    val videoRepository: VideoRepository =
        VideoRepository(dataSource, appScope, errorReporter, pageCacheStore)
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

    init {
        appScope.launch {
            // 旧 DataStore 历史/收藏/搜索词一次性迁入 Room（幂等）
            LegacyDataMigrator(appContext, database).migrateIfNeeded()
        }
    }
}

/** 让 Compose 层级可以直接拿到容器中的仓库 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 尚未提供")
}
