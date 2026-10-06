package com.av123.video.ui.screens.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
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
import androidx.compose.ui.unit.dp
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.net.DataSourceProbeResult
import com.av123.video.data.net.IpProbe
import com.av123.video.data.net.NetworkEnvironment
import com.av123.video.data.net.NetworkType
import com.av123.video.data.net.ProbeFailureKind
import com.av123.video.data.net.ProbeTimings
import com.av123.video.data.net.SiteProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 诊断页一屏的数据快照 */
private data class DiagnosticsData(
    val result: DataSourceProbeResult,
    val env: NetworkEnvironment
)

/**
 * 网络诊断完整页：站点连通性 + 分段耗时（DNS/TCP/TLS/TTFB）、公网信息与网络类型、
 * 设备环境（WebView 内核/App/Android/机型/语言），支持一键复制纯文本报告。
 * LaunchedEffect 以 epoch 为 key：快速重复刷新会自动取消上一轮未完成检测。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var epoch by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var data by remember { mutableStateOf<DiagnosticsData?>(null) }

    LaunchedEffect(epoch) {
        loading = true
        val snapshot = withContext(Dispatchers.IO) {
            val result = container.dataSourceProbe.probe()
            val env = NetworkEnvironment.collect(context.applicationContext)
            DiagnosticsData(result, env)
        }
        data = snapshot
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.diagnostics_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { epoch += 1 }, enabled = !loading) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.diagnostics_refresh)
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val snapshot = data
        if (loading && snapshot == null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.diagnostics_checking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        if (snapshot == null) return@Scaffold

        val timeFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
        val checkedAtText = timeFormatter.format(Date(snapshot.result.checkedAt))
        // 数据层只产归一化类别，中文文案在组合期统一从 strings.xml 映射后再交给纯文本报告
        val networkTypeText = snapshot.env.networkTypeText()
        val siteFailText = (snapshot.result.site as? SiteProbe.Failed)?.kind?.failureText()
        val ipFailText = (snapshot.result.ip as? IpProbe.Failed)?.kind?.failureText()

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SiteDiagnosticsCard(result = snapshot.result) }
            item { NetworkDiagnosticsCard(result = snapshot.result, env = snapshot.env) }
            item { DeviceEnvironmentCard(env = snapshot.env, checkedAtText = checkedAtText) }
            item {
                Button(
                    onClick = {
                        val report = buildDiagnosticsReport(
                            result = snapshot.result,
                            env = snapshot.env,
                            checkedAtText = checkedAtText,
                            networkTypeText = networkTypeText,
                            siteFailReasonText = siteFailText,
                            ipFailReasonText = ipFailText
                        )
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText(DIAGNOSTICS_CLIP_LABEL, report)
                        )
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.diagnostics_copied)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.diagnostics_copy))
                }
            }
        }
    }
}

/** 站点状态卡：host、结论、状态码/总耗时 + DNS/TCP/TLS/首字节四分段 */
@Composable
private fun SiteDiagnosticsCard(result: DataSourceProbeResult) {
    val host = remember(result.siteUrl) {
        android.net.Uri.parse(result.siteUrl).host?.takeIf { it.isNotBlank() } ?: result.siteUrl
    }
    DiagnosticsCard(title = host) {
        when (val site = result.site) {
            is SiteProbe.Ok -> {
                StatusHeader(
                    icon = Icons.Rounded.CloudDone,
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
                    icon = Icons.Rounded.CloudOff,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    title = stringResource(R.string.profile_probe_http_error),
                    subtitle = stringResource(R.string.profile_probe_http_code, site.httpCode)
                )
                Spacer(Modifier.height(12.dp))
                TimingSegments(timings = site.timings, totalMs = site.latencyMs)
            }

            is SiteProbe.Failed -> {
                val reasonText = site.kind.failureText()
                StatusHeader(
                    icon = Icons.Rounded.CloudOff,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    title = stringResource(R.string.profile_probe_unreachable),
                    subtitle = reasonText
                )
                Spacer(Modifier.height(12.dp))
                InfoLine(
                    label = stringResource(R.string.diagnostics_fail_reason),
                    value = reasonText
                )
                Spacer(Modifier.height(12.dp))
                TimingSegments(timings = null, totalMs = null)
            }
        }
    }
}

/** 四分段等宽数字行：DNS / TCP / TLS / 首字节（TTFB），无值显示"—" */
@Composable
private fun TimingSegments(timings: ProbeTimings?, totalMs: Long?) {
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
private fun NetworkDiagnosticsCard(result: DataSourceProbeResult, env: NetworkEnvironment) {
    DiagnosticsCard(title = stringResource(R.string.diagnostics_section_network)) {
        InfoLine(
            label = stringResource(R.string.diagnostics_network_type),
            value = env.networkTypeText()
        )
        Spacer(Modifier.height(12.dp))
        when (val ip = result.ip) {
            is IpProbe.Ok -> {
                InfoLineWithIcon(
                    icon = Icons.Rounded.Public,
                    label = stringResource(R.string.profile_probe_ip),
                    value = ip.ip
                )
                Spacer(Modifier.height(10.dp))
                InfoLineWithIcon(
                    icon = Icons.Rounded.Place,
                    label = stringResource(R.string.profile_probe_location),
                    value = ip.location.ifBlank { stringResource(R.string.profile_probe_unknown) }
                )
                Spacer(Modifier.height(10.dp))
                InfoLineWithIcon(
                    icon = Icons.Rounded.Router,
                    label = stringResource(R.string.profile_probe_isp),
                    value = ip.isp.ifBlank { stringResource(R.string.profile_probe_unknown) }
                )
            }

            is IpProbe.Failed -> {
                InfoLine(
                    label = stringResource(R.string.profile_probe_ip_failed),
                    value = ip.kind.failureText()
                )
            }
        }
    }
}

/** 设备环境卡：WebView 内核/App/Android/机型/语言 + 检测时间 */
@Composable
private fun DeviceEnvironmentCard(env: NetworkEnvironment, checkedAtText: String) {
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
private fun StatusHeader(
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
                .clip(RoundedCornerShape(12.dp))
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

/** 统一诊断卡片容器 */
@Composable
private fun DiagnosticsCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/** 标签 + 值信息行 */
@Composable
private fun InfoLine(label: String, value: String) {
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
            modifier = Modifier.weight(0.58f)
        )
    }
}

/** 带小图标的标签 + 值信息行（公网信息区） */
@Composable
private fun InfoLineWithIcon(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private const val DIAGNOSTICS_CLIP_LABEL = "VideoHub diagnostics"

/** 归一化失败类别 → strings.xml 中文文案（站点/IP 探测失败共用） */
@Composable
private fun ProbeFailureKind.failureText(): String = when (this) {
    ProbeFailureKind.DNS -> stringResource(R.string.diagnostics_fail_dns)
    ProbeFailureKind.TIMEOUT -> stringResource(R.string.diagnostics_fail_timeout)
    ProbeFailureKind.SSL -> stringResource(R.string.diagnostics_fail_ssl)
    ProbeFailureKind.NETWORK -> stringResource(R.string.diagnostics_fail_network)
    ProbeFailureKind.OTHER -> stringResource(R.string.diagnostics_fail_other)
}

/** 网络类型 → strings.xml 中文文案（蜂窝带带宽时附带 Mbps） */
@Composable
private fun NetworkEnvironment.networkTypeText(): String = when (networkType) {
    NetworkType.WIFI -> stringResource(R.string.diagnostics_net_wifi)
    NetworkType.CELLULAR -> cellularDownMbps
        ?.let { stringResource(R.string.diagnostics_net_cellular_mbps, it) }
        ?: stringResource(R.string.diagnostics_net_cellular)
    NetworkType.ETHERNET -> stringResource(R.string.diagnostics_net_ethernet)
    NetworkType.VPN -> stringResource(R.string.diagnostics_net_vpn)
    NetworkType.OTHER -> stringResource(R.string.diagnostics_net_other)
    NetworkType.NONE -> stringResource(R.string.diagnostics_net_none)
}

/** 毫秒段文本：有值 "123 ms"，无值 "—"（报告复制也用同款口径） */
private fun Long?.toMsOrDash(): String = this?.let { "$it ms" } ?: "—"

/**
 * 纯文本诊断报告拼装（纯函数，UI 展示与剪贴板共用同一口径，便于走查）：
 * 站点结论/分段、网络、设备环境三大段，缺失字段一律 "—"。
 */
internal fun buildDiagnosticsReport(
    result: DataSourceProbeResult,
    env: NetworkEnvironment,
    checkedAtText: String,
    /** 以下动态文案由组合期从 strings.xml 映射后传入，保证数据层/报告均不内置中文 */
    networkTypeText: String,
    siteFailReasonText: String?,
    ipFailReasonText: String?
): String {
    val host = android.net.Uri.parse(result.siteUrl).host?.takeIf { it.isNotBlank() }
        ?: result.siteUrl

    var statusText: String
    var codeText = "—"
    var seg: ProbeTimings? = null
    var totalMs: Long? = null
    var failReason: String? = null
    when (val site = result.site) {
        is SiteProbe.Ok -> {
            statusText = "可以正常访问"
            codeText = "HTTP ${site.httpCode}"
            seg = site.timings
            totalMs = site.latencyMs
        }
        is SiteProbe.HttpError -> {
            statusText = "站点返回错误（HTTP ${site.httpCode}）"
            codeText = "HTTP ${site.httpCode}"
            seg = site.timings
            totalMs = site.latencyMs
        }
        is SiteProbe.Failed -> {
            statusText = "暂时无法访问"
            failReason = siteFailReasonText
        }
    }

    val ipLines = when (val ip = result.ip) {
        is IpProbe.Ok -> listOf(
            "公网 IP：${ip.ip}",
            "归属地：${ip.location.ifBlank { "—" }}",
            "运营商：${ip.isp.ifBlank { "—" }}"
        )
        is IpProbe.Failed -> listOf("公网信息：获取失败（${ipFailReasonText ?: "—"}）")
    }

    return buildString {
        appendLine("VideoHub 网络诊断")
        appendLine("检测时间：$checkedAtText")
        appendLine("")
        appendLine("【站点状态】")
        appendLine("站点：$host")
        appendLine("状态：$statusText")
        if (failReason != null) appendLine("失败原因：$failReason")
        appendLine("状态码：$codeText")
        appendLine("总耗时：${totalMs.toMsOrDash()}")
        appendLine("DNS：${seg?.dnsMs.toMsOrDash()}")
        appendLine("TCP：${seg?.tcpMs.toMsOrDash()}")
        appendLine("TLS：${seg?.tlsMs.toMsOrDash()}")
        appendLine("首字节：${seg?.ttfbMs.toMsOrDash()}")
        appendLine("")
        appendLine("【网络信息】")
        appendLine("网络类型：$networkTypeText")
        ipLines.forEach { appendLine(it) }
        appendLine("")
        appendLine("【设备环境】")
        appendLine("WebView 内核：${env.webViewPackage.ifBlank { "—" }}")
        appendLine("应用版本：${env.appVersionName.ifBlank { "—" }}")
        appendLine("Android 版本：${env.androidRelease.ifBlank { "—" }}")
        appendLine("设备型号：${env.model.ifBlank { "—" }}")
        appendLine("系统语言：${env.language.ifBlank { "—" }}")
    }
}
