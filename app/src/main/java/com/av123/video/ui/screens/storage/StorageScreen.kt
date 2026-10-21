package com.av123.video.ui.screens.storage

import com.av123.video.ui.icons.AppIcons

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.repository.CacheBreakdown
import com.av123.video.data.repository.CacheKind
import com.av123.video.data.repository.CacheManager
import com.av123.video.data.repository.formatCacheSize
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.appCardContainerColor
import com.av123.video.ui.util.rememberResumeTick
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 存储空间总览：环形占用 + 行内勾选。
 * 勾选只由复选框切换（点行文字进只读明细），默认全选；
 * 清理按钮按选中项求和。缓存总额固定 1 GB，不在页面暴露设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(onBack: () -> Unit, onOpenKind: (CacheKind) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val resumeTick = rememberResumeTick()

    var breakdown by remember { mutableStateOf<CacheBreakdown?>(null) }
    var selected by remember { mutableStateOf<Set<CacheKind>>(emptySet()) }
    var selectionReady by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(resumeTick) {
        val current = runCatching { container.cacheManager.breakdown() }.getOrNull()
        breakdown = current
        if (current != null) {
            val eligible = eligibleKinds(current)
            // 首次进入默认全选；之后刷新只剔除已清空/不可清理的项，保留用户的手动选择
            selected = if (selectionReady) selected.intersect(eligible) else eligible
            selectionReady = true
        }
    }

    val eligible = breakdown?.let(::eligibleKinds) ?: emptySet()
    val allSelected = eligible.isNotEmpty() && selected.containsAll(eligible)
    val chosen = selected.filter { it in eligible }
    val chosenBytes = breakdown?.let { current -> chosen.sumOf { current.bytesOf(it) } } ?: 0L

    fun toggle(kind: CacheKind) {
        if (kind !in eligible) return
        selected = if (kind in selected) selected - kind else selected + kind
    }

    fun clearChosen() {
        val before = breakdown ?: return
        val target = chosen.toSet()
        if (target.isEmpty() || clearing) return
        clearing = true
        showConfirm = false
        scope.launch {
            val result = runCatching { container.cacheManager.clear(target) }.getOrNull()
            val after = runCatching { container.cacheManager.breakdown() }.getOrNull()
            breakdown = after
            selected = selected - target
            if (after != null) selected = selected.intersect(eligibleKinds(after))
            clearing = false
            val freed = (before.totalBytes - (after?.totalBytes ?: before.totalBytes))
                .coerceAtLeast(0L)
            val text = when {
                result == null -> resources.getString(R.string.storage_clear_failed)
                !result.mediaCacheCleared -> resources.getString(
                    R.string.storage_cleared_media_busy,
                    formatCacheSize(freed)
                )
                else -> resources.getString(R.string.storage_cleared, formatCacheSize(freed))
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.storage_back)
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                StorageSummaryCard(
                    breakdown = breakdown,
                    onKindClick = onOpenKind
                )
            }

            item { StorageSectionTitle() }
            item {
                StorageListCard {
                    CacheKind.entries.forEachIndexed { index, kind ->
                        if (index > 0) StorageDivider()
                        StorageKindRow(
                            kind = kind,
                            breakdown = breakdown,
                            checked = kind in selected,
                            enabled = kind in eligible,
                            onToggle = { toggle(kind) },
                            onClick = { onOpenKind(kind) }
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.storage_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            item {
                Button(
                    onClick = { showConfirm = true },
                    enabled = chosen.isNotEmpty() && !clearing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = when {
                            clearing -> stringResource(R.string.storage_clearing)
                            chosen.isEmpty() -> stringResource(R.string.storage_clear_nothing)
                            allSelected -> stringResource(
                                R.string.storage_clear_all,
                                formatCacheSize(chosenBytes)
                            )
                            else -> stringResource(
                                R.string.storage_clear_selected,
                                formatCacheSize(chosenBytes)
                            )
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showConfirm) {
        val current = breakdown
        if (current != null && chosen.isNotEmpty()) {
            // 标题在组合期取好：joinToString 的 lambda 不是组合上下文
            val titles = CacheKind.entries.associateWith { it.title() }
            val detail = chosen.joinToString(" · ") { kind ->
                "${titles.getValue(kind)} ${formatCacheSize(current.bytesOf(kind))}"
            }
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = {
                    Text(
                        if (allSelected) {
                            stringResource(R.string.storage_confirm_all_title)
                        } else {
                            stringResource(R.string.storage_confirm_selected_title)
                        }
                    )
                },
                text = {
                    Text(
                        stringResource(
                            R.string.storage_confirm_selected_body,
                            detail,
                            formatCacheSize(chosenBytes)
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { clearChosen() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.storage_confirm_clear))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }
}

/** 单类缓存明细：只做占用分析，清理统一回到总览勾选 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDetailScreen(kind: CacheKind, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val resumeTick = rememberResumeTick()
    var breakdown by remember { mutableStateOf<CacheBreakdown?>(null) }

    LaunchedEffect(resumeTick) {
        breakdown = runCatching { container.cacheManager.breakdown() }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kind.title()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.storage_back)
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { StorageDetailSummaryCard(kind = kind, breakdown = breakdown) }
            item { StorageCompositionCard(kind = kind, breakdown = breakdown) }
            item {
                Text(
                    text = kind.hint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun eligibleKinds(breakdown: CacheBreakdown): Set<CacheKind> =
    CacheKind.entries.filter { breakdown.bytesOf(it) > 0 && breakdown.clearableOf(it) }.toSet()

private fun percentText(bytes: Long, total: Long): String {
    if (bytes <= 0L || total <= 0L) return "0%"
    val percent = bytes.toDouble() / total * 100.0
    return when {
        percent >= 10.0 -> "${percent.roundToInt()}%"
        percent >= 1.0 -> String.format(Locale.ROOT, "%.1f%%", percent)
        else -> "<0.1%"
    }
}

@Composable
private fun CacheKind.title(): String = when (this) {
    CacheKind.MEDIA -> stringResource(R.string.storage_kind_media)
    CacheKind.IMAGE -> stringResource(R.string.storage_kind_image)
    CacheKind.PAGE -> stringResource(R.string.storage_kind_page)
    CacheKind.OTHER -> stringResource(R.string.storage_kind_other)
}

private fun CacheKind.icon(): ImageVector = when (this) {
    CacheKind.MEDIA -> AppIcons.PlayArrowOutline
    CacheKind.IMAGE -> AppIcons.ImageOutline
    CacheKind.PAGE -> AppIcons.DescriptionOutline
    CacheKind.OTHER -> AppIcons.LanguageOutline
}

/** 四类共用一套线性图标与配色：环、行首图标、图例三点一致 */
@Composable
private fun CacheKind.shareColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    return when (this) {
        CacheKind.MEDIA -> primary
        CacheKind.IMAGE -> lerp(primary, surface, 0.45f)
        CacheKind.PAGE -> lerp(primary, surface, 0.68f)
        // 第四类也用主题色系，避免灰图标看起来像没染色
        CacheKind.OTHER -> MaterialTheme.colorScheme.tertiary
    }
}

@Composable
private fun CacheKind.subtitle(breakdown: CacheBreakdown?): String {
    if (breakdown == null) return stringResource(R.string.storage_dash)
    val total = breakdown.totalBytes
    val count = when (this) {
        CacheKind.MEDIA -> if (breakdown.mediaClearable) {
            stringResource(R.string.storage_media_sub, breakdown.mediaCount)
        } else {
            stringResource(R.string.storage_media_sub_busy, breakdown.mediaCount)
        }
        CacheKind.IMAGE -> stringResource(R.string.storage_image_sub, breakdown.imageCount)
        CacheKind.PAGE -> stringResource(
            R.string.storage_page_sub,
            breakdown.pageStats.listLikeCount,
            breakdown.pageStats.detailCount
        )
        CacheKind.OTHER -> stringResource(R.string.storage_other_sub)
    }
    return "$count · ${percentText(breakdown.bytesOf(this), total)}"
}

@Composable
private fun CacheKind.hint(): String = when (this) {
    CacheKind.MEDIA -> stringResource(R.string.storage_hint_media)
    CacheKind.IMAGE -> stringResource(R.string.storage_hint_image)
    CacheKind.PAGE -> stringResource(R.string.storage_hint_page)
    CacheKind.OTHER -> stringResource(R.string.storage_hint_other)
}

@Composable
private fun StorageCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.elevatedCardColors(containerColor = appCardContainerColor()),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun StorageListCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.elevatedCardColors(containerColor = appCardContainerColor()),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun StorageDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp, end = 12.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

@Composable
private fun StorageSummaryCard(
    breakdown: CacheBreakdown?,
    onKindClick: (CacheKind) -> Unit
) {
    StorageCard {
        val total = breakdown?.totalBytes ?: 0L
        CacheDonut(
            breakdown = breakdown,
            onKindClick = onKindClick,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(184.dp)
        )

        Spacer(Modifier.height(10.dp))
        val used = formatCacheSize(total)
        val limitBytes = CacheManager.DEFAULT_TOTAL_BUDGET_BYTES
        val percent = if (limitBytes > 0L) (total * 100L / limitBytes).toInt() else 0
        Text(
            text = stringResource(R.string.storage_used_of_limit, used, percent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        if (breakdown != null) {
            val fraction by animateFloatAsState(
                targetValue = (total.toFloat() / limitBytes).coerceIn(0f, 1f),
                animationSpec = tween(400),
                label = "cacheUsage"
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        val caption = when {
            breakdown == null -> stringResource(R.string.storage_dash)
            breakdown.totalBytes == 0L -> stringResource(R.string.storage_empty_caption)
            breakdown.mediaBytes == 0L -> stringResource(R.string.storage_media_cleared)
            else -> stringResource(
                R.string.storage_media_share_count,
                percentText(breakdown.mediaBytes, breakdown.totalBytes),
                breakdown.mediaCount
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CacheDonut(
    breakdown: CacheBreakdown?,
    onKindClick: (CacheKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = breakdown?.totalBytes ?: 0L
    val colors = CacheKind.entries.associateWith { it.shareColor() }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val scope = rememberCoroutineScope()
    var pressedKind by remember { mutableStateOf<CacheKind?>(null) }
    val pressed = remember { Animatable(0f) }
    var rotationDeg by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "donutDragScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = (1f + pressed.value * 0.015f) * dragScale
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(breakdown) {
                // 环带拖动旋转：最终停在放手角度，但带着手势速度做一次弹性收尾
                val padPx = 8.dp.toPx()
                val strokePx = 40.dp.toPx()
                var lastAngle = 0f
                var lastTime = 0L
                var angularVelocity = 0f
                var rotationJob: Job? = null
                fun angleOf(offset: Offset): Float {
                    val dx = offset.x - size.width / 2f
                    val dy = offset.y - size.height / 2f
                    return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                }
                fun inRing(offset: Offset): Boolean {
                    val dx = offset.x - size.width / 2f
                    val dy = offset.y - size.height / 2f
                    val radius = sqrt(dx * dx + dy * dy)
                    val outer = size.width / 2f - padPx
                    return radius >= outer - strokePx && radius <= outer
                }
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = inRing(offset)
                        if (isDragging) {
                            rotationJob?.cancel()
                            lastAngle = angleOf(offset)
                            lastTime = System.nanoTime()
                            angularVelocity = 0f
                        }
                    },
                    onDrag = { change, _ ->
                        if (isDragging) {
                            change.consume()
                            val current = angleOf(change.position)
                            var delta = current - lastAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            val now = System.nanoTime()
                            val elapsed = (now - lastTime) / 1_000_000_000f
                            if (elapsed > 0.0001f) angularVelocity = delta / elapsed
                            lastTime = now
                            rotationDeg += delta
                            lastAngle = current
                        }
                    },
                    onDragEnd = {
                        if (isDragging) {
                            isDragging = false
                            val releaseAngle = rotationDeg
                            val releaseVelocity = angularVelocity.coerceIn(-900f, 900f)
                            // 目标就是放手角度：靠速度产生过冲再收回，不回 12 点
                            rotationJob = scope.launch {
                                animate(
                                    initialValue = releaseAngle,
                                    targetValue = releaseAngle,
                                    initialVelocity = releaseVelocity,
                                    animationSpec = spring(
                                        dampingRatio = 0.42f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) { value, _ -> rotationDeg = value }
                            }
                        }
                    },
                    onDragCancel = { isDragging = false }
                )
            }
            .pointerInput(breakdown) {
                val padPx = 8.dp.toPx()
                val strokePx = 40.dp.toPx()
                detectTapGestures(
                    onPress = press@ { offset ->
                        val current = breakdown ?: return@press
                        val kind = hitTestKind(
                            offset = offset,
                            size = size,
                            padPx = padPx,
                            strokePx = strokePx,
                            rotationDeg = rotationDeg,
                            breakdown = current
                        ) ?: return@press
                        pressedKind = kind
                        scope.launch {
                            pressed.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.5f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                        tryAwaitRelease()
                        scope.launch {
                            pressed.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.7f,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            if (pressed.value < 0.01f) pressedKind = null
                        }
                    },
                    onTap = tap@ { offset ->
                        val current = breakdown ?: return@tap
                        hitTestKind(
                            offset = offset,
                            size = size,
                            padPx = padPx,
                            strokePx = strokePx,
                            rotationDeg = rotationDeg,
                            breakdown = current
                        )?.let(onKindClick)
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 8.dp.toPx()
            val stroke = 40.dp.toPx()
            val inset = pad + stroke / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val baseTopLeft = Offset(inset, inset)
            if (total <= 0L) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = baseTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
            } else {
                val gap = 3f
                var start = -90f + rotationDeg
                CacheKind.entries.forEach { kind ->
                    val bytes = breakdown?.bytesOf(kind) ?: 0L
                    if (bytes <= 0L) return@forEach
                    val sweep = bytes.toFloat() / total * 360f
                    val isPressed = kind == pressedKind
                    val pop = if (isPressed) pressed.value else 0f
                    val mid = Math.toRadians((start + sweep / 2f).toDouble())
                    val popPx = pop * 6.dp.toPx()
                    drawArc(
                        color = colors.getValue(kind).copy(
                            alpha = if (pressedKind != null && !isPressed) 0.55f else 1f
                        ),
                        startAngle = start + gap / 2f,
                        // 页面与网页缓存可能只占 0.1%，给一个最小可见角，精确数值仍由行内百分比给出
                        sweepAngle = (sweep - gap).coerceAtLeast(1.5f),
                        useCenter = false,
                        topLeft = baseTopLeft + Offset(
                            cos(mid).toFloat() * popPx,
                            sin(mid).toFloat() * popPx
                        ),
                        size = arcSize,
                        style = Stroke(width = stroke + pop * 2.dp.toPx())
                    )
                    start += sweep
                }
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (breakdown == null) {
                Text(
                    text = stringResource(R.string.storage_calculating),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val text = formatCacheSize(breakdown.totalBytes)
                Text(
                    text = text.substringBeforeLast(' '),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = text.substringAfterLast(' '),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 环带命中：返回点按角度所在的分类；中心留白与环外都不响应 */
private fun hitTestKind(
    offset: Offset,
    size: IntSize,
    padPx: Float,
    strokePx: Float,
    rotationDeg: Float,
    breakdown: CacheBreakdown
): CacheKind? {
    val total = breakdown.totalBytes
    if (total <= 0L) return null
    val dx = offset.x - size.width / 2f
    val dy = offset.y - size.height / 2f
    val radius = sqrt(dx * dx + dy * dy)
    val outer = size.width / 2f - padPx
    if (radius < outer - strokePx || radius > outer) return null
    val degrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    val fromTop = ((degrees + 90f - rotationDeg) % 360f + 360f) % 360f
    var start = 0f
    CacheKind.entries.forEach { kind ->
        val bytes = breakdown.bytesOf(kind)
        if (bytes <= 0L) return@forEach
        val sweep = bytes.toFloat() / total * 360f
        if (fromTop >= start && fromTop < start + sweep) return kind
        start += sweep
    }
    return null
}

@Composable
private fun StorageSectionTitle() {
    Text(
        text = stringResource(R.string.storage_section_kinds),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp)
    )
}

@Composable
private fun CacheCheckbox(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val container by animateColorAsState(
        targetValue = if (checked) primary else Color.Transparent,
        animationSpec = tween(160),
        label = "checkFill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(160),
        label = "checkBorder"
    )
    val boxAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        animationSpec = tween(160),
        label = "checkEnabled"
    )
    val markAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(160),
        label = "checkMark"
    )
    val markScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.6f,
        animationSpec = tween(160),
        label = "checkScale"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { alpha = boxAlpha }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(container)
                .border(1.5.dp, borderColor, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        alpha = markAlpha
                        scaleX = markScale
                        scaleY = markScale
                    }
            )
        }
    }
}

@Composable
private fun StorageKindRow(
    kind: CacheKind,
    breakdown: CacheBreakdown?,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val bytes = breakdown?.bytesOf(kind) ?: 0L
    val clearable = breakdown?.clearableOf(kind) ?: true
    // 行首图标统一用主题主色：分类色只留给圆环，避免第四类看起来掉队
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CacheCheckbox(checked = checked, enabled = enabled, onToggle = onToggle)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = kind.icon(),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = kind.title(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (bytes > 0L) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = kind.subtitle(breakdown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = breakdown?.let { formatCacheSize(it.bytesOf(kind)) }
                    ?: stringResource(R.string.storage_dash),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (clearable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (!clearable) {
                Text(
                    text = stringResource(R.string.storage_media_busy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StorageDetailSummaryCard(kind: CacheKind, breakdown: CacheBreakdown?) {
    StorageCard {
        Text(
            text = kind.title(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = breakdown?.let { formatCacheSize(it.bytesOf(kind)) }
                ?: stringResource(R.string.storage_calculating),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = kind.subtitle(breakdown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StorageCompositionCard(kind: CacheKind, breakdown: CacheBreakdown?) {
    StorageCard {
        Text(
            text = stringResource(R.string.storage_composition),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        val share = breakdown?.let { percentText(it.bytesOf(kind), it.totalBytes) }
            ?: stringResource(R.string.storage_dash)
        when (kind) {
            CacheKind.MEDIA -> {
                StorageMetricRow(
                    label = stringResource(R.string.storage_media_segments),
                    value = stringResource(R.string.storage_count_segments, breakdown?.mediaCount ?: 0)
                )
                StorageMetricRow(
                    label = stringResource(R.string.storage_share),
                    value = share
                )
                StorageMetricRow(
                    label = stringResource(R.string.storage_media_policy),
                    value = stringResource(R.string.storage_media_policy_value)
                )
            }

            CacheKind.IMAGE -> {
                StorageMetricRow(
                    label = stringResource(R.string.storage_image_count),
                    value = stringResource(R.string.storage_count_images, breakdown?.imageCount ?: 0)
                )
                StorageMetricRow(
                    label = stringResource(R.string.storage_share),
                    value = share
                )
            }

            CacheKind.PAGE -> {
                val stats = breakdown?.pageStats
                StorageMetricRow(
                    label = stringResource(R.string.storage_page_feed),
                    value = stringResource(
                        R.string.storage_count_size,
                        stats?.feedCount ?: 0,
                        formatCacheSize(stats?.feedBytes ?: 0L)
                    )
                )
                StorageMetricRow(
                    label = stringResource(R.string.storage_page_list),
                    value = stringResource(
                        R.string.storage_count_size,
                        stats?.listCount ?: 0,
                        formatCacheSize(stats?.listBytes ?: 0L)
                    )
                )
                StorageMetricRow(
                    label = stringResource(R.string.storage_page_detail),
                    value = stringResource(
                        R.string.storage_count_size,
                        stats?.detailCount ?: 0,
                        formatCacheSize(stats?.detailBytes ?: 0L)
                    )
                )
                StorageMetricRow(
                    label = stringResource(R.string.storage_page_limit),
                    value = stringResource(R.string.storage_page_limit_value)
                )
            }

            CacheKind.OTHER -> {
                StorageMetricRow(
                    label = stringResource(R.string.storage_other_webview),
                    value = formatCacheSize(breakdown?.webViewBytes ?: 0L)
                )
                StorageMetricRow(
                    label = stringResource(R.string.storage_other_temp),
                    value = formatCacheSize(breakdown?.tempBytes ?: 0L)
                )
                StorageMetricRow(
                    label = stringResource(R.string.storage_share),
                    value = share
                )
            }
        }
    }
}

@Composable
private fun StorageMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
