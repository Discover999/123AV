package com.av123.video.ui.screens.profile

import com.av123.video.ui.icons.AppIcons

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.av123.video.LocalAppContainer
import com.av123.video.LocalBottomBarInset
import com.av123.video.R
import com.av123.video.data.prefs.AppLockTimeout
import com.av123.video.data.prefs.DarkMode
import com.av123.video.data.repository.BackupImportResult
import com.av123.video.data.repository.formatCacheSize
import com.av123.video.ui.lock.authenticateAppLock
import com.av123.video.ui.lock.canAuthenticateAppLock
import com.av123.video.ui.util.findActivity
import com.av123.video.ui.util.formatWatchDurationMs
import com.av123.video.ui.util.rememberAppHaptics
import com.av123.video.ui.util.rememberResumeTick
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenFavorites: () -> Unit,
    onOpenFollowed: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenStorage: () -> Unit,
    scrollToTopTick: Int = 0
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val settingsState by container.appSettingsState.collectAsStateWithLifecycle()
    val settings = settingsState.settings
    val historyRecords by container.historyRepository.records
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteRecords by container.favoriteRepository.favorites
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val followedActresses by container.followedActressRepository.actresses
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // 今日观看墙钟时长：统计入口副标题实时刷新（Room Flow）
    val todayWatchMs by container.historyRepository.observeDayPlayMs()
        .collectAsStateWithLifecycle(initialValue = 0L)
    val scope = rememberCoroutineScope()
    var showDarkModeSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLockUnavailableDialog by remember { mutableStateOf(false) }
    var showLockTimeoutSheet by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var cacheSizeBytes by remember { mutableStateOf<Long?>(null) }
    // 从存储空间页清完缓存退回时，副标题的体积要跟着更新
    val resumeTick = rememberResumeTick()

    LaunchedEffect(resumeTick) {
        cacheSizeBytes = runCatching { container.cacheManager.sizeBytes() }.getOrNull()
    }
    val cacheSummary = cacheSizeBytes?.let { formatCacheSize(it) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val exported = runCatching {
                val json = container.backupRepository.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    } ?: error("无法写入文件")
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (exported) R.string.profile_export_success else R.string.profile_export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                        it.readText()
                    } ?: error("无法读取文件")
                }
                container.backupRepository.importJson(text)
            }.getOrElse { BackupImportResult.Failure(it.message ?: "读取失败") }
            val message = when (result) {
                is BackupImportResult.Success -> resources.getString(R.string.profile_import_success)
                is BackupImportResult.Failure -> resources.getString(
                    R.string.profile_import_failed,
                    result.message
                )
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val listState = rememberLazyListState()
        // 重复点击底部 Tab：回到顶部
        LaunchedEffect(scrollToTopTick) {
            if (scrollToTopTick > 0) listState.scrollToItem(0)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 32.dp + LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // —— 用户信息头部卡片 ——
            item {
                ProfileHeader(
                    historyCount = historyRecords.size,
                    favoriteCount = favoriteRecords.size,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // —— 内容与数据 ——
            item {
                SettingsSection(
                    title = stringResource(R.string.profile_group_data),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SettingsItem(
                        icon = AppIcons.Favorite,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.profile_my_favorites),
                        subtitle = stringResource(
                            R.string.profile_my_favorites_subtitle, favoriteRecords.size
                        ),
                        trailing = {
                            Icon(
                                AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = onOpenFavorites
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.Stars,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.profile_my_followed),
                        subtitle = stringResource(
                            R.string.profile_my_followed_subtitle, followedActresses.size
                        ),
                        trailing = {
                            Icon(
                                AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = onOpenFollowed
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.QueryStats,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.profile_watch_stats),
                        subtitle = stringResource(
                            R.string.profile_watch_stats_subtitle,
                            formatWatchDurationMs(todayWatchMs)
                        ),
                        trailing = {
                            Icon(
                                AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = onOpenStats
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.Cloud,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.profile_data_source),
                        subtitle = stringResource(R.string.profile_data_source_value),
                        trailing = {
                            Icon(
                                AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = onOpenDiagnostics
                    )
                }
            }

            // —— 数据与备份 ——
            item {
                SettingsSection(
                    title = stringResource(R.string.profile_group_data_management),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SettingsItem(
                        icon = AppIcons.FileUpload,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.profile_export_data),
                        subtitle = stringResource(R.string.profile_export_data_desc),
                        trailing = {
                            Icon(
                                AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {
                            exportLauncher.launch("123AV-backup-${LocalDate.now()}.json")
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.FileDownload,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.profile_import_data),
                        subtitle = stringResource(R.string.profile_import_data_desc),
                        trailing = {
                            Icon(
                                AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { showImportConfirm = true }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.CleaningServices,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.storage_title),
                        subtitle = if (cacheSummary != null) {
                            stringResource(R.string.profile_cache_size, cacheSummary)
                        } else {
                            stringResource(R.string.profile_cache_computing)
                        },
                        trailing = {
                            Icon(
                                AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = onOpenStorage
                    )
                }
            }

            // —— 隐私与安全 ——
            item {
                SettingsSection(
                    title = stringResource(R.string.profile_group_security),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SettingsItem(
                        icon = AppIcons.Lock,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.profile_app_lock),
                        subtitle = stringResource(R.string.profile_app_lock_desc),
                        trailing = {
                            HapticSwitch(
                                checked = settings.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        // 关闭无需验证，立即生效（仅影响下次冷启动）
                                        scope.launch {
                                            container.settingsStore.setAppLockEnabled(false)
                                        }
                                    } else {
                                        // 开启前：必须存在可用凭据，且本人验证通过才置位
                                        val activity =
                                            context.findActivity() as? FragmentActivity
                                        if (activity == null ||
                                            !canAuthenticateAppLock(activity)
                                        ) {
                                            showLockUnavailableDialog = true
                                            return@HapticSwitch
                                        }
                                        authenticateAppLock(
                                            activity = activity,
                                            title = resources.getString(
                                                R.string.app_lock_prompt_title
                                            ),
                                            subtitle = resources.getString(
                                                R.string.profile_app_lock
                                            ),
                                            onSuccess = {
                                                scope.launch {
                                                    container.settingsStore.setAppLockEnabled(true)
                                                }
                                            },
                                            // 取消/失败：开关保持关闭（绑定状态，不乐观更新）
                                            onError = { _, _ -> }
                                        )
                                    }
                                }
                            )
                        }
                    )
                    if (settings.appLockEnabled) {
                        SettingsDivider()
                        SettingsItem(
                            icon = AppIcons.Timer,
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.profile_app_lock_relock),
                            subtitle = appLockTimeoutLabel(settings.appLockTimeout),
                            trailing = {
                                Icon(
                                    AppIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = { showLockTimeoutSheet = true }
                        )
                    }
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.VisibilityOff,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.profile_secure_screen),
                        subtitle = stringResource(R.string.profile_secure_screen_desc),
                        trailing = {
                            HapticSwitch(
                                checked = settings.secureScreenEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.settingsStore.setSecureScreenEnabled(enabled)
                                    }
                                }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.PhoneAndroid,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.profile_flip_to_exit),
                        subtitle = stringResource(R.string.profile_flip_to_exit_desc),
                        trailing = {
                            HapticSwitch(
                                checked = settings.flipToExitEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.settingsStore.setFlipToExitEnabled(enabled)
                                    }
                                }
                            )
                        }
                    )
                }
            }

            // —— 外观 ——
            item {
                SettingsSection(
                    title = stringResource(R.string.profile_group_appearance),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SettingsItem(
                        icon = AppIcons.Palette,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.profile_dynamic_color),
                        subtitle = if (dynamicColorSupported) {
                            stringResource(R.string.profile_dynamic_color_desc)
                        } else {
                            stringResource(R.string.profile_dynamic_color_unsupported)
                        },
                        trailing = {
                            HapticSwitch(
                                checked = settings.dynamicColor && dynamicColorSupported,
                                enabled = dynamicColorSupported,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.settingsStore.setDynamicColor(enabled)
                                    }
                                }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.ViewAgenda,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.profile_floating_nav),
                        subtitle = stringResource(R.string.profile_floating_nav_desc),
                        trailing = {
                            HapticSwitch(
                                checked = settings.floatingNavBar,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.settingsStore.setFloatingNavBar(enabled)
                                    }
                                }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.DarkMode,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.profile_dark_mode),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = darkModeLabel(settings.darkMode),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    AppIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { showDarkModeSheet = true }
                    )
                }
            }

            // —— 播放 ——
            item {
                SettingsSection(
                    title = stringResource(R.string.profile_group_playback),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SettingsItem(
                        icon = AppIcons.PictureInPictureAlt,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.profile_pip),
                        subtitle = stringResource(R.string.profile_pip_desc),
                        trailing = {
                            HapticSwitch(
                                checked = settings.pipEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.settingsStore.setPipEnabled(enabled)
                                    }
                                }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = AppIcons.PlaylistPlay,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.profile_auto_next),
                        subtitle = stringResource(R.string.profile_auto_next_desc),
                        trailing = {
                            HapticSwitch(
                                checked = settings.autoPlayNext,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.settingsStore.setAutoPlayNext(enabled)
                                    }
                                }
                            )
                        }
                    )
                }
            }

            // —— 关于 ——
            item {
                SettingsSection(
                    title = stringResource(R.string.profile_group_about),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SettingsItem(
                        icon = AppIcons.Info,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.profile_about_version),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "v$versionName",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    AppIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    if (showDarkModeSheet) {
        DarkModeSheet(
            current = settings.darkMode,
            onDismiss = { showDarkModeSheet = false },
            onSelect = { mode ->
                scope.launch { container.settingsStore.setDarkMode(mode) }
                showDarkModeSheet = false
            }
        )
    }

    if (showLockTimeoutSheet) {
        AppLockTimeoutSheet(
            current = settings.appLockTimeout,
            onDismiss = { showLockTimeoutSheet = false },
            onSelect = { timeout ->
                scope.launch { container.settingsStore.setAppLockTimeout(timeout) }
                showLockTimeoutSheet = false
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.profile_import_confirm_title)) },
            text = { Text(stringResource(R.string.profile_import_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        importLauncher.launch(arrayOf("application/json"))
                    }
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.profile_about_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.profile_about_dialog_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.profile_about_dialog_disclaimer_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.profile_about_dialog_disclaimer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.profile_about_dialog_got_it))
                }
            }
        )
    }

    // 设备没有任何锁屏/生物凭据时开启应用锁：弹说明并保持关闭
    if (showLockUnavailableDialog) {
        AlertDialog(
            onDismissRequest = { showLockUnavailableDialog = false },
            title = { Text(stringResource(R.string.app_lock_no_credential)) },
            text = {
                Text(
                    text = stringResource(R.string.app_lock_no_credential_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showLockUnavailableDialog = false }) {
                    Text(stringResource(R.string.app_lock_no_credential_got_it))
                }
            }
        )
    }
}

/**
 * 设置开关统一带一次轻反馈：开关属于会改变行为的操作。
 * 视觉与布局沿用 Material3 默认 Switch，不改尺寸。
 */
@Composable
private fun HapticSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true
) {
    val haptics = rememberAppHaptics()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { callback ->
            { value ->
                haptics.tick()
                callback(value)
            }
        },
        enabled = enabled
    )
}
