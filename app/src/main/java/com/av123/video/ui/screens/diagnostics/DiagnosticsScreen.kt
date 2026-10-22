package com.av123.video.ui.screens.diagnostics

import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.util.rememberAppHaptics

import android.app.ActivityManager
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.av123.video.data.net.DataSourceProbeResult
import com.av123.video.data.net.NetworkEnvironment
import com.av123.video.data.repository.CacheUsage
import kotlinx.coroutines.Dispatchers
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
 * 设备环境（WebView 内核/App/Android/机型/语言）。
 * LaunchedEffect 以 epoch 为 key：快速重复刷新会自动取消上一轮未完成检测。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
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

    val haptics = rememberAppHaptics()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onBack()
                        }
                    ) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.diagnostics_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            epoch += 1
                        },
                        enabled = !loading
                    ) {
                        Icon(
                            AppIcons.Refresh,
                            contentDescription = stringResource(R.string.diagnostics_refresh)
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
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
        val runtimeText = runtimeMetricText(snapshot)

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
        }
    }
}

