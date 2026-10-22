package com.av123.video.ui.screens.profile

import com.av123.video.ui.icons.AppIcons

import com.av123.video.ui.theme.AppShapes

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.ui.theme.appCardContainerColor
import com.av123.video.data.prefs.AppLockTimeout
import com.av123.video.data.prefs.DarkMode
import com.av123.video.data.repository.BackupImportResult
import com.av123.video.data.repository.formatCacheSize
import com.av123.video.ui.lock.authenticateAppLock
import com.av123.video.ui.lock.canAuthenticateAppLock
import com.av123.video.ui.util.findActivity
import com.av123.video.ui.util.rememberAppHaptics
import com.av123.video.ui.util.formatWatchDurationMs
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 头部用户卡片：头像 + 名称 + 数据统计（三格均可点） */
@Composable
internal fun ProfileHeader(
    historyCount: Int,
    favoriteCount: Int,
    followedCount: Int,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenFollowed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.Hero,
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
                        imageVector = AppIcons.Person,
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderStat(
                    label = stringResource(R.string.profile_stats_history),
                    value = stringResource(R.string.profile_stats_history_value, historyCount),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenHistory
                )
                HeaderStatDivider()
                HeaderStat(
                    label = stringResource(R.string.profile_stats_favorites),
                    value = stringResource(R.string.profile_stats_history_value, favoriteCount),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFavorites
                )
                HeaderStatDivider()
                HeaderStat(
                    label = stringResource(R.string.profile_stats_followed),
                    value = stringResource(R.string.profile_stats_followed_value, followedCount),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFollowed
                )
            }
        }
    }
}

@Composable
private fun HeaderStatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f))
    )
}

@Composable
internal fun HeaderStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val haptics = rememberAppHaptics()
    Column(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(AppShapes.Medium)
                        .clickable {
                            haptics.tick()
                            onClick()
                        }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
internal fun SettingsSection(
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
            shape = AppShapes.Large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = appCardContainerColor()
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
            Column(content = content)
        }
    }
}

/** 设置项：彩色圆角图标容器 + 标题/副标题 + 尾部控件 */
@Composable
internal fun SettingsItem(
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
                .clip(AppShapes.Medium)
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
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/** 深色模式选择底部弹窗 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DarkModeSheet(
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
internal fun darkModeLabel(mode: DarkMode): String = when (mode) {
    DarkMode.SYSTEM -> stringResource(R.string.profile_dark_mode_system)
    DarkMode.LIGHT -> stringResource(R.string.profile_dark_mode_light)
    DarkMode.DARK -> stringResource(R.string.profile_dark_mode_dark)
}

@Composable
internal fun darkModeDesc(mode: DarkMode): String = when (mode) {
    DarkMode.SYSTEM -> stringResource(R.string.profile_dark_mode_system_desc)
    DarkMode.LIGHT -> stringResource(R.string.profile_dark_mode_light_desc)
    DarkMode.DARK -> stringResource(R.string.profile_dark_mode_dark_desc)
}

@Composable
internal fun appLockTimeoutLabel(timeout: AppLockTimeout): String = when (timeout) {
    AppLockTimeout.IMMEDIATE -> stringResource(R.string.app_lock_timeout_immediate)
    AppLockTimeout.THIRTY_SECONDS -> stringResource(R.string.app_lock_timeout_30s)
    AppLockTimeout.ONE_MINUTE -> stringResource(R.string.app_lock_timeout_1m)
    AppLockTimeout.FIVE_MINUTES -> stringResource(R.string.app_lock_timeout_5m)
}

/** 应用锁离开后重新验证的时长选择 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppLockTimeoutSheet(
    current: AppLockTimeout,
    onDismiss: () -> Unit,
    onSelect: (AppLockTimeout) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            text = stringResource(R.string.profile_app_lock_relock),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        AppLockTimeout.entries.forEach { timeout ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(timeout) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = current == timeout,
                    onClick = { onSelect(timeout) }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = appLockTimeoutLabel(timeout),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

