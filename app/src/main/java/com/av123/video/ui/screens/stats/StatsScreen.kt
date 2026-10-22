package com.av123.video.ui.screens.stats

import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.util.rememberAppHaptics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.repository.CategoryStat
import com.av123.video.data.repository.DayWatch
import com.av123.video.data.repository.WatchStats
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.shimmerPlaceholder
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.appCardContainerColor
import com.av123.video.ui.util.formatWatchDurationMs
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 观看统计页：累计时长 + 今日/近 7 天、近 7 天柱图（数值/均值/最佳）、
 * 完成度环与继续观看入口、分类偏好占比、最近 12 周热力图。
 * 全部数据为本机 Room 聚合，页面进入时一次性快照，右上角可手动刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: StatsViewModel = viewModel {
        StatsViewModel(container.historyRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats_title),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onBack()
                        }
                    ) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.stats_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            viewModel.refresh()
                        },
                        enabled = !uiState.loading
                    ) {
                        Icon(
                            AppIcons.Refresh,
                            contentDescription = stringResource(R.string.stats_refresh)
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val stats = uiState.stats
        when {
            // 首屏加载中：骨架卡，避免整页转圈
            uiState.loading && stats == null ->
                StatsSkeleton(Modifier.padding(padding))

            // 完全没有观看历史：空态只留标题
            stats != null && stats.totalVideos == 0 ->
                EmptyState(
                    icon = AppIcons.QueryStatsOutline,
                    title = stringResource(R.string.stats_empty_title),
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                )

            stats != null ->
                StatsContent(
                    stats = stats,
                    onOpenHistory = onOpenHistory,
                    modifier = Modifier.padding(padding)
                )
        }
    }
}

@Composable
private fun StatsContent(
    stats: WatchStats,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { HeroCard(stats = stats) }
        item { WeekChartCard(week7 = stats.week7, last7DaysMs = stats.last7DaysMs) }
        item { CompletionCard(stats = stats, onOpenHistory = onOpenHistory) }
        if (stats.categories.isNotEmpty()) {
            item { CategoryCard(categories = stats.categories) }
        }
        item { HeatmapCard(heatmap = stats.heatmap, longestStreak = stats.longestStreak) }
    }
}

/** 累计时长主卡：累计 + 今日/7 天 + 连续/活跃/活跃日均 */
@Composable
private fun HeroCard(stats: WatchStats) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.stats_total_label),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatWatchDurationMs(stats.totalMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.stats_total_caption,
                    formatWatchDurationMs(stats.todayMs),
                    formatWatchDurationMs(stats.last7DaysMs)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.onPrimaryContainer.copy(alpha = 0.16f))
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroStat(
                    value = stringResource(R.string.stats_unit_days, stats.streakDays),
                    label = stringResource(R.string.stats_streak),
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    value = stringResource(R.string.stats_unit_days, stats.activeDays),
                    label = stringResource(R.string.stats_active_days),
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    value = averagePerActiveDay(stats),
                    label = stringResource(R.string.stats_active_average),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)
        )
    }
}

private fun averagePerActiveDay(stats: WatchStats): String =
    if (stats.activeDays <= 0 || stats.totalMs <= 0L) "—"
    else formatWatchDurationMs(stats.totalMs / stats.activeDays)

/**
 * 近 7 天柱图：柱顶数值 + 均值虚线 + 最佳日。
 * 柱高按 7 天最大值归一，数值用 18m / 1h12 这类紧凑写法。
 */
@Composable
private fun WeekChartCard(week7: List<DayWatch>, last7DaysMs: Long) {
    val colorScheme = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val maxMs = remember(week7) { week7.maxOfOrNull { it.playMs } ?: 0L }
    val averageMs = last7DaysMs / 7L
    val best = remember(week7) {
        week7.maxByOrNull { it.playMs }?.takeIf { it.playMs > 0L }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = appCardContainerColor()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.stats_chart_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatWatchDurationMs(last7DaysMs),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
                if (maxMs > 0L && averageMs > 0L) {
                    val avgFraction = (averageMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxSize()) {
                        // 柱区高 112dp，均值线按柱区高度定位
                        val barArea = 112.dp.toPx()
                        val y = size.height - barArea * avgFraction
                        drawLine(
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    week7.forEach { day ->
                        WeekBar(
                            day = day,
                            maxMs = maxMs,
                            isToday = day.date == today,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                week7.forEach { day ->
                    val isToday = day.date == today
                    Text(
                        text = if (isToday) {
                            stringResource(R.string.stats_weekday_today)
                        } else {
                            weekdayLabel(day.date)
                        },
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) colorScheme.primary else colorScheme.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.onSurface.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(10.dp))
            if (best != null) {
                Text(
                    text = stringResource(
                        R.string.stats_chart_footer,
                        formatWatchDurationMs(averageMs),
                        weekdayLabel(best.date),
                        formatWatchDurationMs(best.playMs)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.stats_chart_footer_average,
                        formatWatchDurationMs(averageMs)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeekBar(
    day: DayWatch,
    maxMs: Long,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val fraction = if (maxMs > 0L) {
        (day.playMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(modifier.fillMaxSize()) {
        if (day.playMs > 0L && fraction > 0f) {
            val barHeight = (112.dp * fraction).coerceAtLeast(2.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.6f)
                    .height(barHeight)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(
                        if (isToday) colorScheme.primary else colorScheme.primaryContainer
                    )
            )
            Text(
                text = chartValue(day.playMs),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(barHeight + 4.dp))
            )
        } else {
            // 零值占位：底部 2dp 细线，保持日期间距节奏
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.6f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colorScheme.outlineVariant)
            )
        }
    }
}

/** 柱顶紧凑数值：18m / 1h12；与时长文案同样四舍五入到分钟，避免 41m/42分钟并存 */
private fun chartValue(ms: Long): String {
    val minutes = ((ms + 30_000L) / 60_000L).toInt()
    return if (minutes < 60) {
        "${minutes}m"
    } else {
        String.format(Locale.ROOT, "%dh%02d", minutes / 60, minutes % 60)
    }
}

/** 完成度：环形比例 + 已看完/共 N 部 + 继续观看入口 */
@Composable
private fun CompletionCard(stats: WatchStats, onOpenHistory: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = appCardContainerColor()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.stats_completion),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 8.dp.toPx()
                        drawArc(
                            color = colorScheme.surfaceVariant,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        if (stats.finishedRatio > 0f) {
                            drawArc(
                                color = colorScheme.primary,
                                startAngle = -90f,
                                sweepAngle = 360f * stats.finishedRatio,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Text(
                        text = "${(stats.finishedRatio * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.stats_finished_count,
                            stats.finishedVideos
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (stats.continueWatching > 0) {
                            stringResource(
                                R.string.stats_completion_sub,
                                stats.totalVideos,
                                stats.continueWatching
                            )
                        } else {
                            stringResource(
                                R.string.stats_completion_sub_all,
                                stats.totalVideos
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (stats.continueWatching > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(AppShapes.Small)
                                .clickable(onClick = onOpenHistory)
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.stats_continue_watching),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.primary
                            )
                            Icon(
                                AppIcons.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 分类偏好 Top3：部数 + 占比，其他类别汇总一行 */
@Composable
private fun CategoryCard(categories: List<CategoryStat>) {
    val colorScheme = MaterialTheme.colorScheme
    val top = categories.take(3)
    val total = categories.sumOf { it.count }.coerceAtLeast(1)
    val others = categories.drop(3)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = appCardContainerColor()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.stats_categories),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))
            top.forEachIndexed { index, category ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                val percent = (category.count * 100f / total).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(
                            R.string.stats_category_value,
                            category.count,
                            percent
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.primary
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { category.count.toFloat() / total.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(AppShapes.Small),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.stats_categories_other,
                        others.size,
                        others.sumOf { it.count }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 最近 12 周热力图：列=周、行=周一..周日，今天描边 */
@Composable
private fun HeatmapCard(heatmap: List<DayWatch>, longestStreak: Int) {
    if (heatmap.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme
    val byDate = remember(heatmap) { heatmap.associateBy { it.date } }
    val first = heatmap.first().date
    val last = heatmap.last().date
    val firstMonday = remember(first) {
        first.minusDays((first.dayOfWeek.value - 1).toLong())
    }
    val weeks = remember(firstMonday, last) {
        ((last.toEpochDay() - firstMonday.toEpochDay()) / 7L).toInt() + 1
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = appCardContainerColor()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.stats_heatmap_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.stats_longest_streak, longestStreak),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(weeks) { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (dow in 1..7) {
                                val date = firstMonday.plusDays((week * 7 + dow - 1).toLong())
                                if (date.isBefore(first) || date.isAfter(last)) {
                                    Spacer(Modifier.size(18.dp))
                                } else {
                                    val ms = byDate[date]?.playMs ?: 0L
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(heatColor(ms, colorScheme))
                                            .then(
                                                if (date == last) {
                                                    Modifier.border(
                                                        width = 1.dp,
                                                        color = colorScheme.primary,
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_heatmap_less),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                repeat(5) { level ->
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(heatColorForLevel(level, colorScheme))
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = stringResource(R.string.stats_heatmap_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun heatColor(ms: Long, scheme: ColorScheme): Color {
    if (ms <= 0L) return heatColorForLevel(0, scheme)
    val minutes = ms / 60_000L
    val level = when {
        minutes < 15L -> 1
        minutes < 30L -> 2
        minutes < 60L -> 3
        else -> 4
    }
    return heatColorForLevel(level, scheme)
}

private fun heatColorForLevel(level: Int, scheme: ColorScheme): Color = when (level) {
    0 -> scheme.surfaceVariant.copy(alpha = 0.45f)
    1 -> scheme.primary.copy(alpha = 0.22f)
    2 -> scheme.primary.copy(alpha = 0.42f)
    3 -> scheme.primary.copy(alpha = 0.68f)
    else -> scheme.primary
}

/** 首屏骨架：与最终卡片同高，进入页面不跳结构 */
@Composable
private fun StatsSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), 16)
        SkeletonBlock(Modifier.fillMaxWidth().height(260.dp), 16)
        SkeletonBlock(Modifier.fillMaxWidth().height(120.dp), 16)
        SkeletonBlock(Modifier.fillMaxWidth().height(220.dp), 16)
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier, radius: Int) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).shimmerPlaceholder())
}

/** 周一~周日短标签：来自 strings.xml 的 stats_weekdays 数组（周一→周日顺序） */
@Composable
private fun weekdayLabel(date: LocalDate): String {
    val labels = LocalContext.current.resources.getStringArray(R.array.stats_weekdays)
    return labels[date.dayOfWeek.value - 1]
}
