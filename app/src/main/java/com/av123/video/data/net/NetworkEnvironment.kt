package com.av123.video.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.webkit.WebView
import java.util.Locale

/** 默认网络的归一化类型（文案由 UI 层经 strings.xml 映射） */
enum class NetworkType { WIFI, CELLULAR, ETHERNET, VPN, OTHER, NONE }

/**
 * 诊断页"设备环境/网络类型"快照：
 * - [networkType] 当前默认网络类型；[cellularDownMbps] 蜂窝下行带宽（仅蜂窝时有值）
 * - [webViewPackage] System WebView 内核包名 + 版本（API 26+ 可取）
 * - [appVersionName] 应用版本名；[androidRelease] Android 版本；[model] 机型；[language] 系统语言
 */
data class NetworkEnvironment(
    val networkType: NetworkType,
    val cellularDownMbps: Int?,
    val webViewPackage: String,
    val appVersionName: String,
    val androidRelease: String,
    val model: String,
    val language: String
) {
    companion object {
        fun collect(context: Context): NetworkEnvironment {
            val (type, downMbps) = currentNetwork(context)
            return NetworkEnvironment(
                networkType = type,
                cellularDownMbps = downMbps,
                webViewPackage = currentWebViewPackage(),
                appVersionName = currentAppVersionName(context),
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                model = Build.MODEL.orEmpty(),
                language = Locale.getDefault().toLanguageTag()
            )
        }

        /** 返回网络类型与蜂窝下行带宽（Mbps，非蜂窝为 null；取 0 也按 null 处理） */
        private fun currentNetwork(context: Context): Pair<NetworkType, Int?> {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return NetworkType.OTHER to null
            val network = cm.activeNetwork ?: return NetworkType.NONE to null
            val caps = cm.getNetworkCapabilities(network) ?: return NetworkType.NONE to null
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    NetworkType.WIFI to null
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                    NetworkType.ETHERNET to null
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    // 下行带宽（kbps）可感知 5G/4G 粗粒度，取得到才附带
                    val downKbps = caps.linkDownstreamBandwidthKbps
                    val downMbps = (downKbps / 1000).takeIf { it > 0 }
                    NetworkType.CELLULAR to downMbps
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ->
                    NetworkType.VPN to null
                else -> NetworkType.OTHER to null
            }
        }

        private fun currentWebViewPackage(): String = runCatching {
            WebView.getCurrentWebViewPackage()?.let { pkg ->
                "${pkg.packageName} ${pkg.versionName.orEmpty()}"
            }.orEmpty()
        }.getOrDefault("")

        private fun currentAppVersionName(context: Context): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
}
