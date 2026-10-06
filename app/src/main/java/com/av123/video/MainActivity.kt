package com.av123.video

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.av123.video.data.prefs.AppSettings
import com.av123.video.data.prefs.DarkMode
import com.av123.video.ui.lock.AppLockGate
import com.av123.video.ui.navigation.VideoHubApp
import com.av123.video.ui.theme.VideoHubTheme
import com.av123.video.ui.util.AppLog
import com.av123.video.ui.util.findActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : FragmentActivity() {

    /** 启动时同步读取的外观设置：决定启动屏/窗口底色与 Compose 首帧，避免深浅色闪烁 */
    private lateinit var initialSettings: AppSettings

    /**
     * 播放器注册的画中画准入判断：仅全屏播放态才允许 Home 时进入 PiP。
     * 单 Activity 架构下同屏最多一个播放器，单槽注册即可。
     */
    @Volatile
    var pipEligibilityProvider: () -> Boolean = { false }

    /** 画中画模式变化监听器（播放器注册以隐藏控件、保持播放） */
    @Volatile
    var pipModeChangedListener: ((Boolean) -> Unit)? = null

    /**
     * 用户按 Home 离开（而非直接返回桌面的其他路径）：全屏播放中自动进入画中画。
     * minSdk 26，无需版本判断；部分机型（低内存/不支持）可能抛异常，静默兜底。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isInPictureInPictureMode &&
            runCatching { pipEligibilityProvider() }.getOrDefault(false)
        ) {
            runCatching {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                // Android 12+：手势上滑回桌面也自动进入 PiP（无缝缩放）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                    builder.setSeamlessResizeEnabled(true)
                }
                enterPictureInPictureMode(builder.build())
            }.onFailure { AppLog.w(TAG_PIP, "进入画中画失败: ${it.message}") }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipModeChangedListener?.invoke(isInPictureInPictureMode)
    }

    /**
     * 播放器全屏状态变化时注册/撤销 PiP 参数。
     * Android 12+ 的 [PictureInPictureParams.Builder.setAutoEnterEnabled] 必须
     * 在手势 Home 发生前通过 setPictureInPictureParams 注册才生效。
     */
    fun updatePipParams(eligible: Boolean) {
        runCatching {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(eligible)
                builder.setSeamlessResizeEnabled(true)
            }
            setPictureInPictureParams(builder.build())
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // Application.onCreate 先于 Activity 创建，容器已就绪
        val app = newBase.applicationContext as VideoHubApp
        // 同步读取一次持久化设置（DataStore 首帧，文件很小，耗时可忽略）
        initialSettings = runBlocking { app.container.settingsStore.settings.first() }

        val systemDark =
            (newBase.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val appNight = when (initialSettings.darkMode) {
            DarkMode.SYSTEM -> systemDark
            DarkMode.LIGHT -> false
            DarkMode.DARK -> true
        }

        // 覆盖 Context 的夜间配置，让启动屏背景、窗口背景等 DayNight 资源
        // 在 Compose 初始化之前就按用户设置解析，杜绝“先浅后深”的闪烁
        val config = Configuration(newBase.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appNight) Configuration.UI_MODE_NIGHT_YES
                else Configuration.UI_MODE_NIGHT_NO
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动屏（androidx core-splashscreen），必须在 super.onCreate 前安装
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 边到边布局，MD3 推荐
        enableEdgeToEdge()

        val container = (application as VideoHubApp).container

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                val settings by container.settingsStore.settings
                    .collectAsStateWithLifecycle(initialValue = initialSettings)

                // 注意：attachBaseContext 为防启动闪烁强制覆盖了 Activity 的 uiMode，
                // 故 isSystemInDarkTheme() 读到的是被覆盖后的配置，不能用于“跟随系统”。
                // 这里直接观察未被包装的 Application Context 的真实系统配置。
                val systemDark = rememberSystemDarkMode()
                val darkTheme = when (settings.darkMode) {
                    DarkMode.SYSTEM -> systemDark
                    DarkMode.LIGHT -> false
                    DarkMode.DARK -> true
                }

                // 系统状态栏/导航栏图标外观跟随 App 实际明暗主题：
                // 暗色模式用浅色图标，浅色模式用深色图标；
                // App 内强制深色或运行时切换时也会即时生效，避免黑图标压在黑背景上
                val view = LocalView.current
                SideEffect {
                    val window = view.context.findActivity()?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                }

                VideoHubTheme(
                    darkTheme = darkTheme,
                    dynamicColor = settings.dynamicColor
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // 应用锁：仅按冷启动快照决定本进程是否加锁，不随后续设置 Flow 变化
                        AppLockGate(enabledAtLaunch = initialSettings.appLockEnabled) {
                            VideoHubApp()
                        }
                    }
                }
            }
        }

        // 首页绘制完成后的空闲时机预热 WebView/Chromium：首次创建 WebView 会
        // 加载 native 库、启动浏览器进程（模拟器/部分机型会卡住主线程 1-2s）。
        // 提前完成后，详情页的无头 m3u8 拦截 WebView 创建时几乎零开销。
        Handler(Looper.getMainLooper()).postDelayed({
            WebViewWarmer.warmUp(applicationContext)
        }, WEBVIEW_WARM_UP_DELAY_MS)
    }
}

/**
 * WebView/Chromium 内核预热：启动 2s 后（首页已稳定显示）创建一个无头 WebView
 * 加载 about:blank，触发 native 库加载与浏览器进程启动，随后销毁。
 * 进程级初始化（浏览器进程、Chromium 运行时）在 WebView 销毁后仍然保留，
 * 后续详情页再创建 WebView 时无需重新初始化。
 */
object WebViewWarmer {
    @SuppressLint("SetJavaScriptEnabled")
    fun warmUp(context: Context) {
        runCatching {
            val webView = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                loadUrl("about:blank")
            }
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    webView.stopLoading()
                    webView.destroy()
                }
            }, 3_000)
        }
    }
}

/** 启动后延迟预热的时间，确保首页首帧/封面加载已完成，不与启动争抢资源 */
private const val WEBVIEW_WARM_UP_DELAY_MS = 2_000L

private const val TAG_PIP = "MainActivity"

/**
 * 读取真实系统的明暗模式。
 *
 * 不能使用 [androidx.compose.foundation.isSystemInDarkTheme]：本 Activity 在
 * attachBaseContext 中用 createConfigurationContext 强制覆盖了 uiMode（启动屏防闪烁），
 * 导致 Compose 的 LocalConfiguration 始终是被覆盖后的值，运行时从“强制深色”切回
 * “跟随系统”时无法还原。Application Context 未被包装，其配置反映真实系统状态。
 */
@androidx.compose.runtime.Composable
private fun rememberSystemDarkMode(): Boolean {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    fun Configuration.isNight(): Boolean =
        (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    var systemDark by remember(appContext) {
        mutableStateOf(appContext.resources.configuration.isNight())
    }
    DisposableEffect(appContext) {
        val callback = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                systemDark = newConfig.isNight()
            }

            override fun onLowMemory() = Unit
        }
        appContext.registerComponentCallbacks(callback)
        onDispose { appContext.unregisterComponentCallbacks(callback) }
    }
    return systemDark
}
