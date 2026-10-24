package com.av123.video.ui.screens.diagnostics

import com.av123.video.ui.icons.AppIconSize
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.util.rememberAppHaptics

import com.av123.video.ui.theme.AppShapes

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.core.net.toUri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.ui.theme.appCardContainerColor
import com.av123.video.data.diagnostics.DiagnosticsMetrics
import com.av123.video.data.diagnostics.DiagnosticsMetricsSnapshot
import com.av123.video.data.net.AppErrorKind
import com.av123.video.data.net.DataSourceProbeResult
import com.av123.video.data.net.IpProbe
import com.av123.video.data.net.NetworkEnvironment
import com.av123.video.data.net.NetworkType
import com.av123.video.data.net.ProbeFailureKind
import com.av123.video.data.net.ProbeTimings
import com.av123.video.data.net.SiteProbe
import com.av123.video.data.repository.CacheUsage
import com.av123.video.data.repository.formatCacheSize
import com.av123.video.data.source.SiteConfigSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 站点状态卡：host、结论、状态码/总耗时 + DNS/TCP/TLS/首字节四分段 */
@Composable
internal fun SiteDiagnosticsCard(result: DataSourceProbeResult) {
    val host = remember(result.siteUrl) {
        result.siteUrl.toUri().host?.takeIf { it.isNotBlank() } ?: result.siteUrl
    }
    DiagnosticsCard(
        title = stringResource(R.string.diagnostics_section_site_host, host),
        trailing = {
            val context = LocalContext.current
            val haptics = rememberAppHaptics()
            Box(
                modifier = Modifier
                    .clip(AppShapes.Small)
                    .clickable {
                        haptics.tick()
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(result.siteUrl))
                            )
                        }
                    }
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = AppIcons.OpenInNew,
                    contentDescription = stringResource(R.string.diagnostics_open_site),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppIconSize.Inline)
                )
            }
        }
    ) {
        when (val site = result.site) {
            is SiteProbe.Ok -> {
                StatusHeader(
                    icon = AppIcons.CloudDone,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    title = stringResource(R.string.profile_probe_ok),
                    subtitle = stringResource(R.string.profile_probe_http_code, site.httpCode)
                )
                Spacer(Modifier.height(12.dp))
                TimingSegments(timings = site.timings, totalMs = site.latencyMs)
            }

            is SiteProbe.HttpError -> {
                StatusHeader(
                    icon = AppIcons.CloudOff,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    title = stringResource(R.string.profile_probe_http_error),
                    subtitle = stringResource(R.string.profile_probe_http_code, site.httpCode)
                )
                Spacer(Modifier.height(12.dp))
                TimingSegments(timings = site.timings, totalMs = site.latencyMs)
            }

            is SiteProbe.Failed -> {
                // 原因已经在结论头的副标题里，不再重复一行；失败时也没有分段耗时可画
                StatusHeader(
                    icon = AppIcons.CloudOff,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    title = stringResource(R.string.profile_probe_unreachable),
                    subtitle = site.kind.failureText()
                )
            }
        }
    }
}

/** 站点配置只读卡：配置来源、版本、缓存/检查时间、远程刷新与最近错误 */
@Composable
internal fun SiteConfigCard(
    source: SiteConfigSource,
    version: String,
    updatedText: String,
    checkedText: String,
    remoteText: String,
    error: String?
) {
    DiagnosticsCard(title = stringResource(R.string.diagnostics_section_site_config)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = version,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            ConfigSourceChip(source)
        }
        Spacer(Modifier.height(12.dp))
        InfoLine(
            label = stringResource(R.string.diagnostics_config_updated),
            value = updatedText
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(
            label = stringResource(R.string.diagnostics_config_remote),
            value = remoteText
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(
            label = stringResource(R.string.diagnostics_config_checked),
            value = checkedText
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.diagnostics_config_last_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
internal fun ConfigSourceChip(source: SiteConfigSource) {
    val background = when (source) {
        SiteConfigSource.BUNDLED -> MaterialTheme.colorScheme.primaryContainer
        SiteConfigSource.CACHE -> MaterialTheme.colorScheme.tertiaryContainer
        SiteConfigSource.FALLBACK -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (source) {
        SiteConfigSource.BUNDLED -> MaterialTheme.colorScheme.onPrimaryContainer
        SiteConfigSource.CACHE -> MaterialTheme.colorScheme.onTertiaryContainer
        SiteConfigSource.FALLBACK -> MaterialTheme.colorScheme.onErrorContainer
    }
    Box(
        modifier = Modifier
            .clip(AppShapes.Pill)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = source.label(),
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

@Composable
internal fun SiteConfigSource.label(): String = when (this) {
    SiteConfigSource.BUNDLED -> stringResource(R.string.diagnostics_config_source_bundled)
    SiteConfigSource.CACHE -> stringResource(R.string.diagnostics_config_source_cache)
    SiteConfigSource.FALLBACK -> stringResource(R.string.diagnostics_config_source_fallback)
}

/** 四分段等宽数字行：DNS / TCP / TLS / 首字节（TTFB），无值显示"—" */
@Composable
internal fun TimingSegments(timings: ProbeTimings?, totalMs: Long?) {
    Column {
        val segments = listOf(
            stringResource(R.string.diagnostics_seg_dns) to timings?.dnsMs,
            stringResource(R.string.diagnostics_seg_tcp) to timings?.tcpMs,
            stringResource(R.string.diagnostics_seg_tls) to timings?.tlsMs,
            stringResource(R.string.diagnostics_seg_ttfb) to timings?.ttfbMs
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            segments.forEach { (label, ms) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = ms?.let { stringResource(R.string.profile_probe_latency_ms, it) }
                            ?: stringResource(R.string.diagnostics_dash),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        InfoLine(
            label = stringResource(R.string.diagnostics_total),
            value = totalMs?.let { stringResource(R.string.profile_probe_latency_ms, it) }
                ?: stringResource(R.string.diagnostics_dash)
        )
    }
}

/** 网络信息卡：网络类型 + 公网 IP/归属地/运营商 */
@Composable
internal fun NetworkDiagnosticsCard(result: DataSourceProbeResult, env: NetworkEnvironment) {
    DiagnosticsCard(title = stringResource(R.string.diagnostics_section_network)) {
        InfoLineWithIcon(
            icon = env.networkType.icon,
            label = stringResource(R.string.diagnostics_network_type),
            value = env.networkTypeText()
        )
        Spacer(Modifier.height(12.dp))
        when (val ip = result.ip) {
            is IpProbe.Ok -> {
                InfoLineWithIcon(
                    icon = AppIcons.Public,
                    label = stringResource(R.string.profile_probe_ip),
                    value = ip.ip
                )
                Spacer(Modifier.height(10.dp))
                InfoLineWithIcon(
                    icon = AppIcons.Place,
                    label = stringResource(R.string.profile_probe_location),
                    value = ip.location.ifBlank { stringResource(R.string.profile_probe_unknown) }
                )
                Spacer(Modifier.height(10.dp))
                InfoLineWithIcon(
                    icon = AppIcons.Router,
                    label = stringResource(R.string.profile_probe_isp),
                    value = ip.isp.ifBlank { stringResource(R.string.profile_probe_unknown) }
                )
            }

            is IpProbe.Failed -> {
                InfoLineWithIcon(
                    icon = AppIcons.CloudOff,
                    label = stringResource(R.string.profile_probe_ip_failed),
                    value = ip.kind.failureText()
                )
            }
        }
    }
}

internal data class RuntimeMetricText(
    val captureValue: String,
    val captureDetail: String,
    val failureValue: String,
    val failureDetail: String,
    val cacheValue: String,
    val cacheDetail: String,
    val webViewValue: String,
    val webViewDetail: String
)

@Composable
internal fun runtimeMetricText(data: DiagnosticsData): RuntimeMetricText {
    val metrics = data.metrics
    val dash = stringResource(R.string.diagnostics_dash)

    val captureValue = metrics.captureSuccessPercent?.let { "$it%" } ?: dash
    val captureDetail = if (metrics.captureAttempts == 0) {
        stringResource(R.string.diagnostics_runtime_no_capture)
    } else {
        stringResource(
            R.string.diagnostics_runtime_capture_detail,
            metrics.captureSuccesses,
            metrics.captureAttempts
        )
    }

    val failureValue = metrics.failureTotal.toString()
    val failureDetail = if (metrics.failureTotal == 0) {
        stringResource(R.string.diagnostics_runtime_no_failure)
    } else {
        val parts = mutableListOf<String>()
        for (kind in AppErrorKind.entries) {
            val count = metrics.failuresByKind[kind] ?: continue
            parts += "${kind.label()} $count"
        }
        parts.joinToString(" · ")
    }

    val cacheValue = formatCacheSize(data.cache.totalBytes)
    val cacheDetail = stringResource(
        R.string.diagnostics_runtime_cache_detail,
        formatCacheSize(data.cache.pageBytes),
        formatCacheSize(data.cache.mediaBytes),
        formatCacheSize(data.cache.imageBytes)
    )

    val webViewValue = data.webViewProcesses?.let {
        stringResource(R.string.diagnostics_runtime_process_count, it)
    } ?: dash

    return RuntimeMetricText(
        captureValue = captureValue,
        captureDetail = captureDetail,
        failureValue = failureValue,
        failureDetail = failureDetail,
        cacheValue = cacheValue,
        cacheDetail = cacheDetail,
        webViewValue = webViewValue,
        webViewDetail = stringResource(R.string.diagnostics_runtime_process_caption)
    )
}

@Composable
internal fun AppErrorKind.label(): String = when (this) {
    AppErrorKind.DNS -> stringResource(R.string.diagnostics_kind_dns)
    AppErrorKind.TIMEOUT -> stringResource(R.string.diagnostics_kind_timeout)
    AppErrorKind.SSL -> stringResource(R.string.diagnostics_kind_ssl)
    AppErrorKind.NETWORK -> stringResource(R.string.diagnostics_kind_network)
    AppErrorKind.HTTP_CLIENT -> stringResource(R.string.diagnostics_kind_http_client)
    AppErrorKind.HTTP_SERVER -> stringResource(R.string.diagnostics_kind_http_server)
    AppErrorKind.PARSE -> stringResource(R.string.diagnostics_kind_parse)
    AppErrorKind.RESPONSE_TOO_LARGE -> stringResource(R.string.diagnostics_kind_response_too_large)
    AppErrorKind.UNKNOWN -> stringResource(R.string.diagnostics_kind_unknown)
}

@Composable
internal fun RuntimeDiagnosticsCard(text: RuntimeMetricText) {
    DiagnosticsCard(title = stringResource(R.string.diagnostics_section_runtime)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuntimeMetricTile(
                modifier = Modifier.weight(1f),
                value = text.captureValue,
                label = stringResource(R.string.diagnostics_runtime_capture),
                detail = text.captureDetail
            )
            RuntimeMetricTile(
                modifier = Modifier.weight(1f),
                value = text.failureValue,
                label = stringResource(R.string.diagnostics_runtime_failures),
                detail = text.failureDetail
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuntimeMetricTile(
                modifier = Modifier.weight(1f),
                value = text.cacheValue,
                label = stringResource(R.string.diagnostics_runtime_cache),
                // 三个分项挪到卡底一行，格子只留总量
                detail = ""
            )
            RuntimeMetricTile(
                modifier = Modifier.weight(1f),
                value = text.webViewValue,
                label = stringResource(R.string.diagnostics_runtime_webview),
                detail = text.webViewDetail
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = text.cacheDetail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.diagnostics_runtime_session_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun RuntimeMetricTile(
    modifier: Modifier,
    value: String,
    label: String,
    detail: String
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun countWebViewProcesses(context: Context): Int? {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val fromManager = manager?.runningAppProcesses?.let { processes ->
        val uid = Process.myUid()
        processes.count { info ->
            info.uid == uid && isWebViewProcessName(info.processName)
        }
    }
    val fromProc = countWebViewProcessesFromProc()
    return listOfNotNull(fromManager, fromProc).maxOrNull()
}

internal fun isWebViewProcessName(name: String): Boolean =
    name.contains("sandboxed_process") || name.contains(":webview")

/** ActivityManager 可能不返回沙箱进程；用 /proc 同包名 cmdline 兜底。 */
internal fun countWebViewProcessesFromProc(): Int? = runCatching {
    File("/proc").listFiles()
        ?.count { dir ->
            if (dir.name.toIntOrNull() == null) return@count false
            val cmdline = runCatching {
                File(dir, "cmdline").readText().trim('\u0000')
            }.getOrDefault("")
            cmdline.contains("com.av123.video:sandboxed_process") ||
                cmdline.contains("com.av123.video:webview")
        } ?: 0
}.getOrNull()

/** 设备环境卡：WebView 内核/App/Android/机型/语言 + 检测时间 */
@Composable
internal fun DeviceEnvironmentCard(env: NetworkEnvironment, checkedAtText: String) {
    val dash = stringResource(R.string.diagnostics_dash)
    DiagnosticsCard(title = stringResource(R.string.diagnostics_section_device)) {
        InfoLine(
            label = stringResource(R.string.diagnostics_webview),
            value = env.webViewPackage.ifBlank { dash }
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(
            label = stringResource(R.string.diagnostics_app_version),
            value = env.appVersionName.ifBlank { dash }
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(
            label = stringResource(R.string.diagnostics_android),
            value = env.androidRelease.ifBlank { dash }
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(
            label = stringResource(R.string.diagnostics_model),
            value = env.model.ifBlank { dash }
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(
            label = stringResource(R.string.diagnostics_language),
            value = env.language.ifBlank { dash }
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(
            label = stringResource(R.string.diagnostics_checked_at_label),
            value = checkedAtText
        )
    }
}

/** 结论头：圆角图标 + 粗体标题 + 副标题 */
@Composable
internal fun StatusHeader(
    icon: ImageVector,
    containerColor: Color,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AppShapes.Medium)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 统一诊断卡片容器；[trailing] 可放标题右侧的操作（如站点状态卡的「在浏览器打开」） */
@Composable
internal fun DiagnosticsCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = appCardContainerColor()
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/** 标签 + 值信息行；长文本（ETag 等）按调用方指定单行省略 */
@Composable
internal fun InfoLine(
    label: String,
    value: String,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f),
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

/**
 * 带小图标的标签 + 值信息行。
 * 图标 + 间距占去 28dp，标签列取 0.36 而非 [InfoLine] 的 0.42，数值列才能落在同一左边缘。
 */
@Composable
internal fun InfoLineWithIcon(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.36f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.64f)
        )
    }
}

/** 归一化失败类别 → strings.xml 中文文案（站点/IP 探测失败共用） */
@Composable
internal fun ProbeFailureKind.failureText(): String = when (this) {
    ProbeFailureKind.DNS -> stringResource(R.string.diagnostics_fail_dns)
    ProbeFailureKind.TIMEOUT -> stringResource(R.string.diagnostics_fail_timeout)
    ProbeFailureKind.SSL -> stringResource(R.string.diagnostics_fail_ssl)
    ProbeFailureKind.NETWORK -> stringResource(R.string.diagnostics_fail_network)
    ProbeFailureKind.OTHER -> stringResource(R.string.diagnostics_fail_other)
}

/** 网络类型 → strings.xml 中文文案（蜂窝带带宽时附带 Mbps） */
@Composable
internal fun NetworkEnvironment.networkTypeText(): String = when (networkType) {
    NetworkType.WIFI -> stringResource(R.string.diagnostics_net_wifi)
    NetworkType.CELLULAR -> cellularDownMbps
        ?.let { stringResource(R.string.diagnostics_net_cellular_mbps, it) }
        ?: stringResource(R.string.diagnostics_net_cellular)
    NetworkType.ETHERNET -> stringResource(R.string.diagnostics_net_ethernet)
    NetworkType.VPN -> stringResource(R.string.diagnostics_net_vpn)
    NetworkType.OTHER -> stringResource(R.string.diagnostics_net_other)
    NetworkType.NONE -> stringResource(R.string.diagnostics_net_none)
}

/** 网络类型 → 网络信息卡行首图标 */
internal val NetworkType.icon: ImageVector
    get() = when (this) {
        NetworkType.WIFI -> AppIcons.Wifi
        NetworkType.CELLULAR -> AppIcons.SignalCellularAlt
        NetworkType.ETHERNET -> AppIcons.SettingsEthernet
        NetworkType.VPN -> AppIcons.VpnLock
        NetworkType.OTHER -> AppIcons.DeviceHub
        NetworkType.NONE -> AppIcons.WifiOff
    }
