package com.av123.video.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.repository.CategoryStat
import com.av123.video.data.repository.DayWatch
import com.av123.video.data.repository.WatchStats
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.util.formatWatchDurationMs
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 观看统计页：今日/近 7 天墙钟时长（只统计真实播放时长，暂停/拖动不计）、
 * 近 7 天柱图、累计影片/看完率/连续天数/活跃天数 2×2 概览、分类 Top3 占比。
 * 全部数据为本机 Room 聚合，页面进入时一次性快照，右上角可手动刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: StatsViewModel = viewModel {
        StatsViewModel(container.historyRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.stats_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !uiState.loading) {
                        Icon(
                            Icons.Rounded.Refresh,
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
            // 首屏加载中
            uiState.loading && stats == null -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // 完全没有观看历史：空态引导
            stats != null && stats.totalVideos == 0 -> {
                EmptyState(
                    icon = Icons.Outlined.QueryStats,
                    title = stringResource(R.string.stats_empty_title),
                    subtitle = stringResource(R.string.stats_empty_subtitle),
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                )
            }
            stats != null -> StatsContent(stats = stats, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun StatsContent(stats: WatchStats, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TodayCard(stats = stats) }
        item { WeekChartCard(week7 = stats.week7) }
        item {
            // 2×2 概览：累计影片 / 已看完 / 连续天数 / 活跃天数
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewCard(
                    label = stringResource(R.string.stats_total_videos),
                    value = stringResource(R.string.stats_unit_videos, stats.totalVideos),
                    modifier = Modifier.weight(1f)
                )
                OverviewCard(
                    label = stringResource(R.string.stats_finished),
                    value = stringResource(
                        R.string.stats_finished_value,
                        stats.finishedVideos,
                        (stats.finishedRatio * 100f).roundToInt()
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewCard(
                    label = stringResource(R.string.stats_streak),
                    value = stringResource(R.string.stats_unit_days, stats.streakDays),
                    modifier = Modifier.weight(1f)
                )
                OverviewCard(
                    label = stringResource(R.string.stats_active_days),
                    value = stringResource(R.string.stats_unit_days, stats.activeDays),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (stats.categories.isNotEmpty()) {
            item { CategoryCard(categories = stats.categories) }
        }
    }
}

/** 今日时长主卡：主色容器 + 大号时长 + 近 7 天合计副标题 */
@Composable
private fun TodayCard(stats: WatchStats) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.stats_today_label),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatWatchDurationMs(stats.todayMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.stats_7days_caption,
                    formatWatchDurationMs(stats.last7DaysMs)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

/** 近 7 天柱图卡：当天主色高亮，其余为主色浅容器；零值留 2dp 占位线 */
@Composable
private fun WeekChartCard(week7: List<DayWatch>) {
    val colorScheme = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val maxMs = remember(week7) { week7.maxOfOrNull { it.playMs } ?: 0L }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.stats_chart_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                week7.forEach { day ->
                    val isToday = day.date == today
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 柱区：底对齐，高度按 7 天最大值比例
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (day.playMs > 0L && maxMs > 0L) {
                                val fraction = (day.playMs.toFloat() / maxMs.toFloat())
                                    .coerceIn(0.05f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .fillMaxHeight(fraction)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 6.dp,
                                                topEnd = 6.dp
                                            )
                                        )
                                        .background(
                                            if (isToday) colorScheme.primary
                                            else colorScheme.primaryContainer
                                        )
                                )
                            } else {
                                // 零值占位：底部 2dp 细线，保持日期间距节奏
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(colorScheme.outlineVariant)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isToday) {
                                stringResource(R.string.stats_weekday_today)
                            } else {
                                weekdayLabel(day.date)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) colorScheme.primary
                            else colorScheme.onSurfaceVariant,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/** 概览小卡：数值 + 名称 */
@Composable
private fun OverviewCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 分类偏好 Top3：名称 + 部数 + 占该用户所有分类记录的比例条 */
@Composable
private fun CategoryCard(categories: List<CategoryStat>) {
    val colorScheme = MaterialTheme.colorScheme
    val top = categories.take(3)
    val total = categories.sumOf { it.count }.coerceAtLeast(1)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surfaceContainerLow
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.stats_unit_videos, category.count),
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
                        .clip(RoundedCornerShape(4.dp)),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

/** 周一~周日短标签：来自 strings.xml 的 stats_weekdays 数组（周一→周日顺序） */
@Composable
private fun weekdayLabel(date: LocalDate): String {
    val labels = LocalContext.current.resources.getStringArray(R.array.stats_weekdays)
    return labels[date.dayOfWeek.value - 1]
}
