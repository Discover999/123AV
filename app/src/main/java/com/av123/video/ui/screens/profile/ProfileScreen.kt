package com.av123.video.ui.screens.profile

import android.os.Build
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Stars
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.prefs.AppSettings
import com.av123.video.data.prefs.DarkMode
import com.av123.video.ui.lock.authenticateAppLock
import com.av123.video.ui.lock.canAuthenticateAppLock
import com.av123.video.ui.util.findActivity
import com.av123.video.ui.util.formatWatchDurationMs
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenFavorites: () -> Unit,
    onOpenFollowed: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    scrollToTopTick: Int = 0
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.settingsStore.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
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
                        icon = Icons.Rounded.Favorite,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.profile_my_favorites),
                        subtitle = stringResource(
                            R.string.profile_my_favorites_subtitle, favoriteRecords.size
                        ),
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = onOpenFavorites
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Rounded.Stars,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.profile_my_followed),
                        subtitle = stringResource(
                            R.string.profile_my_followed_subtitle, followedActresses.size
                        ),
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = onOpenFollowed
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Rounded.QueryStats,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.profile_watch_stats),
                        subtitle = stringResource(
                            R.string.profile_watch_stats_subtitle,
                            formatWatchDurationMs(todayWatchMs)
                        ),
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = onOpenStats
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Rounded.Cloud,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.profile_data_source),
                        subtitle = stringResource(R.string.profile_data_source_value),
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = onOpenDiagnostics
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
                        icon = Icons.Rounded.Lock,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.profile_app_lock),
                        subtitle = stringResource(R.string.profile_app_lock_desc),
                        trailing = {
                            Switch(
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
                                            return@Switch
                                        }
                                        authenticateAppLock(
                                            activity = activity,
                                            title = context.getString(
                                                R.string.app_lock_prompt_title
                                            ),
                                            subtitle = context.getString(
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
                }
            }

            // —— 外观 ——
            item {
                SettingsSection(
                    title = stringResource(R.string.profile_group_appearance),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SettingsItem(
                        icon = Icons.Rounded.Palette,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.profile_dynamic_color),
                        subtitle = if (dynamicColorSupported) {
                            stringResource(R.string.profile_dynamic_color_desc)
                        } else {
                            stringResource(R.string.profile_dynamic_color_unsupported)
                        },
                        trailing = {
                            Switch(
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
                        icon = Icons.Rounded.DarkMode,
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
                                    Icons.Rounded.ChevronRight,
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
                        icon = Icons.Rounded.PictureInPictureAlt,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.profile_pip),
                        subtitle = stringResource(R.string.profile_pip_desc),
                        trailing = {
                            Switch(
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
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.profile_auto_next),
                        subtitle = stringResource(R.string.profile_auto_next_desc),
                        trailing = {
                            Switch(
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
                        icon = Icons.Rounded.Info,
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
                                    Icons.Rounded.ChevronRight,
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

/** 头部用户卡片：头像 + 名称 + 数据统计 */
@Composable
private fun ProfileHeader(
    historyCount: Int,
    favoriteCount: Int,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_user_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.profile_user_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderStat(
                    label = stringResource(R.string.profile_stats_history),
                    value = stringResource(R.string.profile_stats_history_value, historyCount)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(colorScheme.onPrimaryContainer.copy(alpha = 0.18f))
                )
                HeaderStat(
                    label = stringResource(R.string.profile_stats_favorites),
                    value = stringResource(R.string.profile_stats_history_value, favoriteCount)
                )
            }
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        )
    }
}

/** 设置分组：小标题 + 圆角卡片 */
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
            Column(content = content)
        }
    }
}

/** 设置项：彩色圆角图标容器 + 标题/副标题 + 尾部控件 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 卡片内设置项之间的缩进分隔线 */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/** 深色模式选择底部弹窗 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkModeSheet(
    current: DarkMode,
    onDismiss: () -> Unit,
    onSelect: (DarkMode) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            text = stringResource(R.string.profile_dark_mode),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        DarkMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = current == mode,
                    onClick = { onSelect(mode) }
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = darkModeLabel(mode),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = darkModeDesc(mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun darkModeLabel(mode: DarkMode): String = when (mode) {
    DarkMode.SYSTEM -> stringResource(R.string.profile_dark_mode_system)
    DarkMode.LIGHT -> stringResource(R.string.profile_dark_mode_light)
    DarkMode.DARK -> stringResource(R.string.profile_dark_mode_dark)
}

@Composable
private fun darkModeDesc(mode: DarkMode): String = when (mode) {
    DarkMode.SYSTEM -> stringResource(R.string.profile_dark_mode_system_desc)
    DarkMode.LIGHT -> stringResource(R.string.profile_dark_mode_light_desc)
    DarkMode.DARK -> stringResource(R.string.profile_dark_mode_dark_desc)
}


