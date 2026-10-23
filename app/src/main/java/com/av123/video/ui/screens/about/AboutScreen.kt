package com.av123.video.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.av123.video.R
import com.av123.video.data.net.UpdateChecker
import com.av123.video.ui.components.BrandLogo
import com.av123.video.ui.icons.AppIconSize
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.appCardContainerColor
import com.av123.video.ui.util.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REPO_URL = "https://github.com/Discover999/123AV"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val RELEASES_URL = "$REPO_URL/releases/latest"

/** 检查更新的结果弹窗；[link] 为空表示只有一句结论 */
private data class UpdateDialog(val title: String, val link: String?)

/**
 * 「关于」底部抽屉（和深色模式同一个形态）：
 * 图标 + 版本号 + 检查更新 + 更多链接。不放简介，避免把一块文字塞进抽屉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty().ifBlank { "0.1.0" }
    }
    var checking by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<UpdateDialog?>(null) }

    fun checkUpdate() {
        if (checking) return
        haptics.tick()
        checking = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
            checking = false
            result.onSuccess { release ->
                val latest = release.tag.removePrefix("v")
                dialog = if (UpdateChecker.isNewer(latest, versionName)) {
                    UpdateDialog(
                        title = resources.getString(R.string.about_new_version, latest),
                        link = release.pageUrl.ifBlank { RELEASES_URL }
                    )
                } else {
                    UpdateDialog(
                        title = resources.getString(R.string.about_up_to_date, "v$versionName"),
                        link = null
                    )
                }
            }.onFailure {
                // 失败只给一句结论，不摆「去下载 / 稍后」——那不是一次可下载的结果
                dialog = UpdateDialog(
                    title = resources.getString(R.string.about_check_failed),
                    link = null
                )
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            // 直接用启动器图标：自适应图标是 XML，Compose 的 painterResource
            // 解不了（只支持 VectorDrawable/位图），会直接崩，交给 ImageView 渲染
            AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        setImageResource(R.mipmap.ic_launcher)
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(96.dp)
            )
            Spacer(Modifier.height(10.dp))
            // 图标下面的应用名（品牌字标就是 123AV），再下面才是版本号
            BrandLogo(
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            AboutBadge(
                text = "v$versionName",
                accent = true,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { checkUpdate() },
                enabled = !checking,
                shape = AppShapes.Pill,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(48.dp)
                    .widthIn(min = 208.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(AppIconSize.Inline)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (checking) R.string.about_checking else R.string.about_check_update
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.about_more),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(AppShapes.Large)
                    .background(appCardContainerColor())
            ) {
                AboutLinkRow(
                    icon = AppIcons.Public,
                    title = stringResource(R.string.about_project),
                    subtitle = stringResource(R.string.about_project_url)
                ) {
                    haptics.tick()
                    uriHandler.openUri(REPO_URL)
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                AboutLinkRow(
                    icon = AppIcons.DescriptionOutline,
                    title = stringResource(R.string.about_feedback)
                ) {
                    haptics.tick()
                    uriHandler.openUri(ISSUES_URL)
                }
            }
        }
    }

    dialog?.let { current ->
        // 自己排的紧凑弹窗：AlertDialog 的标题/按钮下内边距对一句结论来说太空
        Dialog(onDismissRequest = { dialog = null }) {
            Surface(
                shape = AppShapes.ExtraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 12.dp,
                        top = 18.dp,
                        bottom = 6.dp
                    )
                ) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (current.link != null) {
                            TextButton(onClick = { dialog = null }) {
                                Text(stringResource(R.string.about_later))
                            }
                        }
                        TextButton(onClick = {
                            dialog = null
                            current.link?.let { uriHandler.openUri(it) }
                        }) {
                            Text(
                                stringResource(
                                    if (current.link != null) R.string.about_download
                                    else R.string.profile_about_dialog_got_it
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutBadge(text: String, accent: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(AppShapes.Pill)
            .background(if (accent) scheme.primary else scheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            // 比原来的 12sp 大一档，抽屉里这枚版本号是主要信息
            fontSize = 14.sp,
            color = if (accent) scheme.onPrimary else scheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AboutLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AppShapes.Medium)
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(AppIconSize.Inline)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface
            )
            // 没有地址的行只显示标题（问题反馈就是这样）
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(AppIconSize.Inline)
        )
    }
}
