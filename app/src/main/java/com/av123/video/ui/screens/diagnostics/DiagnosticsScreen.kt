package com.av123.video.ui.screens.diagnostics

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Process
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

/** 诊断页一屏的数据快照 */
internal data class DiagnosticsData(
    val result: DataSourceProbeResult,
    val env: NetworkEnvironment,
    val cache: CacheUsage,
    val metrics: DiagnosticsMetricsSnapshot,
    val webViewProcesses: Int?
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
    val configState by container.siteConfigStore.state.collectAsStateWithLifecycle()

    var epoch by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var data by remember { mutableStateOf<DiagnosticsData?>(null) }

    LaunchedEffect(epoch) {
        loading = true
        val snapshot = withContext(Dispatchers.IO) {
            val result = container.dataSourceProbe.probe()
            val env = NetworkEnvironment.collect(context.applicationContext)
            val cache = container.cacheManager.usage()
            val metrics = DiagnosticsMetrics.snapshot()
            val webViewProcesses = countWebViewProcesses(context.applicationContext)
            DiagnosticsData(result, env, cache, metrics, webViewProcesses)
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

        val configSourceLabel = configState.source.label()
        val configVersionText = "${configState.config.id}@${configState.config.version}"
        val configUpdatedText = configState.cachedAt
            ?.let { timeFormatter.format(Date(it)) }
            ?: stringResource(R.string.diagnostics_dash)
        val configCheckedText = configState.lastCheckedAt
            ?.let { timeFormatter.format(Date(it)) }
            ?: stringResource(R.string.diagnostics_config_never)
        val configEtagText = configState.etag
            ?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.diagnostics_dash)
        val configRemoteText = if (configState.config.remoteEnabled) {
            stringResource(
                R.string.diagnostics_config_remote_on,
                configState.config.remoteRefreshHours
            )
        } else {
            stringResource(R.string.diagnostics_config_remote_off)
        }
        val siteConfigLines = buildList {
            add("${context.getString(R.string.diagnostics_config_source)}：$configSourceLabel")
            add("${context.getString(R.string.diagnostics_config_version)}：$configVersionText")
            add("${context.getString(R.string.diagnostics_config_updated)}：$configUpdatedText")
            add("${context.getString(R.string.diagnostics_config_checked)}：$configCheckedText")
            add("${context.getString(R.string.diagnostics_config_etag)}：$configEtagText")
            add("${context.getString(R.string.diagnostics_config_remote)}：$configRemoteText")
            configState.lastError?.let {
                add(context.getString(R.string.diagnostics_config_last_error, it))
            }
        }
        val runtimeText = runtimeMetricText(snapshot)
        val runtimeLines = listOf(
            "${context.getString(R.string.diagnostics_runtime_capture)}：" +
                "${runtimeText.captureValue}（${runtimeText.captureDetail}）",
            "${context.getString(R.string.diagnostics_runtime_failures)}：" +
                "${runtimeText.failureValue}（${runtimeText.failureDetail}）",
            "${context.getString(R.string.diagnostics_runtime_cache)}：" +
                "${runtimeText.cacheValue}（${runtimeText.cacheDetail}）",
            "${context.getString(R.string.diagnostics_runtime_webview)}：" +
                "${runtimeText.webViewValue}（${runtimeText.webViewDetail}）"
        )

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SiteDiagnosticsCard(result = snapshot.result) }
            item {
                SiteConfigCard(
                    source = configState.source,
                    version = configVersionText,
                    updatedText = configUpdatedText,
                    checkedText = configCheckedText,
                    etagText = configEtagText,
                    remoteText = configRemoteText,
                    error = configState.lastError
                )
            }
            item { RuntimeDiagnosticsCard(runtimeText) }
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
                            ipFailReasonText = ipFailText,
                            siteConfigLines = siteConfigLines,
                            runtimeLines = runtimeLines
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
    ipFailReasonText: String?,
    /** 由组合期从 SiteConfigStore 组装；空列表时不输出该段 */
    siteConfigLines: List<String> = emptyList(),
    /** 由组合期从运行指标组装；空列表时不输出该段 */
    runtimeLines: List<String> = emptyList()
): String {
    val host = result.siteUrl.toUri().host?.takeIf { it.isNotBlank() }
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
        if (siteConfigLines.isNotEmpty()) {
            appendLine("【站点配置】")
            siteConfigLines.forEach { appendLine(it) }
            appendLine("")
        }
        if (runtimeLines.isNotEmpty()) {
            appendLine("【运行指标】")
            runtimeLines.forEach { appendLine(it) }
            appendLine("")
        }
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