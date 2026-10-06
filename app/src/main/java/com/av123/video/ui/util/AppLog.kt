package com.av123.video.ui.util

import android.util.Log
import com.av123.video.BuildConfig

/**
 * 全局日志入口：仅 debug 包输出到 logcat。
 * release 包（R8 内联后）调用整体变为空实现，避免播放地址、请求头等
 * 敏感抓流信息在用户设备上可被任意应用通过 logcat 读取。
 */
object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    fun e(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.e(tag, message)
    }
}
