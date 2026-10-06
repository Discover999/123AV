package com.av123.video.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/** 深色模式偏好 */
enum class DarkMode { SYSTEM, LIGHT, DARK }

/**
 * 播放器画面比例模式（映射 media3 AspectRatioFrameLayout.RESIZE_MODE_*）：
 * - [FIT] 适应：等比缩放完整显示，可能留黑边（默认）
 * - [FILL] 填充：拉伸填满视图，画面可能变形
 * - [ZOOM] 裁切填满：等比放大到铺满并裁掉超出部分，不变形
 */
enum class ResizeModeOption { FIT, FILL, ZOOM }

/** 全局外观与播放设置 */
data class AppSettings(
    val dynamicColor: Boolean = true,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    /** 全屏播放回桌面时是否自动进入画中画小窗 */
    val pipEnabled: Boolean = true,
    /** 当前选集播放结束后是否自动连播下一集 */
    val autoPlayNext: Boolean = true,
    /** 播放器画面比例（适应/填充/裁切填满），全局默认、跨影片持久化 */
    val videoResizeMode: ResizeModeOption = ResizeModeOption.FIT,
    /** 冷启动是否需要生物识别/设备凭据解锁 */
    val appLockEnabled: Boolean = false
)

/** 用户偏好仓库（动态取色开关、深色模式、画中画开关） */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            darkMode = runCatching {
                DarkMode.valueOf(prefs[KEY_DARK_MODE] ?: DarkMode.SYSTEM.name)
            }.getOrDefault(DarkMode.SYSTEM),
            pipEnabled = prefs[KEY_PIP_ENABLED] ?: true,
            autoPlayNext = prefs[KEY_AUTO_PLAY_NEXT] ?: true,
            videoResizeMode = runCatching {
                ResizeModeOption.valueOf(prefs[KEY_VIDEO_RESIZE_MODE] ?: ResizeModeOption.FIT.name)
            }.getOrDefault(ResizeModeOption.FIT),
            appLockEnabled = prefs[KEY_APP_LOCK_ENABLED] ?: false
        )
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDarkMode(mode: DarkMode) {
        store.edit { it[KEY_DARK_MODE] = mode.name }
    }

    suspend fun setPipEnabled(enabled: Boolean) {
        store.edit { it[KEY_PIP_ENABLED] = enabled }
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        store.edit { it[KEY_AUTO_PLAY_NEXT] = enabled }
    }

    suspend fun setVideoResizeMode(mode: ResizeModeOption) {
        store.edit { it[KEY_VIDEO_RESIZE_MODE] = mode.name }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        store.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    private companion object {
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        val KEY_PIP_ENABLED = booleanPreferencesKey("pip_enabled")
        val KEY_AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val KEY_VIDEO_RESIZE_MODE = stringPreferencesKey("video_resize_mode")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }
}
