package com.av123.video.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale as drawScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.view.View
import android.view.ViewOutlineProvider
import coil.compose.AsyncImage
import com.av123.video.R
import com.av123.video.ui.theme.Motion
import com.av123.video.data.model.Video

/**
 * 封面长按预览弹窗：静音、循环自动播放网页 data-preview 指向的预览视频
 * （主流视频站 hover 预览的同款交互）。
 *
 * 注意：预览地址虽以 .png 结尾，服务器实际返回的是 mp4 内容（隐式 mp4），
 * 因此 MediaItem 显式声明 [MimeTypes.VIDEO_MP4]，强制走 progressive 提取器，
 * 避免按扩展名推断异常。播放器为弹窗内独立实例，关闭即释放，
 * 与详情页的共享播放器互不影响。
 *
 * 交互：点击遮罩外区域关闭弹窗；点击预览视频本身通过 [onVideoClick]
 * 进入对应播放页。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPreviewDialog(
    video: Video,
    onDismiss: () -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(video.previewUrl) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            // 地址以 .png 结尾但实为 mp4 内容（隐式 mp4），显式声明 MIME 走 progressive
            val mediaItem = androidx.media3.common.MediaItem.Builder()
                .setUri(video.previewUrl)
                .setMimeType(MimeTypes.VIDEO_MP4)
                .build()
            setMediaItem(mediaItem)
            volume = 0f // 预览静音自动播放
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    // 生命周期联动：切后台暂停，回前台恢复；关闭弹窗时释放播放器
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var playerError by remember { mutableStateOf<PlaybackException?>(player.playerError) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onPlayerErrorChanged(e: PlaybackException?) {
                playerError = e
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    // 拦截卡片区域点击，避免点到内容时关闭弹窗
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 消费点击 */ }
            ) {
                Column {
                    // 预览播放区：16:9，裁剪填充（与封面一致）；
                    // 顶部两角随卡片圆角裁剪，否则视频黑底直角会顶出圆角。
                    // 点击视频本身进入对应播放页（父级 Surface 仅消费点击防关闭）。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(Color.Black)
                            .clickable { onVideoClick(video) }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                // 从 XML inflate：surface_type 只能通过布局属性设置，
                                // TextureView 保证圆角 outline 裁剪在 Android 12 以下也生效
                                (LayoutInflater.from(ctx)
                                    .inflate(R.layout.preview_player_view, FrameLayout(ctx), false) as PlayerView)
                                    .apply {
                                        // Compose 的 Modifier.clip 裁剪不到原生 PlayerView 画面，
                                        // 必须直接在 view 上按顶部圆角 outline 裁剪（只圆顶部两角）
                                        outlineProvider = object : ViewOutlineProvider() {
                                            override fun getOutline(view: View, viewOutline: android.graphics.Outline) {
                                                val radius = view.resources.displayMetrics.density * 20f
                                                val path = Path().apply {
                                                    addRoundRect(
                                                        RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                                                        // addRoundRect 八元组顺序：左上、右上、右下、左下（各 x,y）
                                                        floatArrayOf(
                                                            radius, radius,
                                                            radius, radius,
                                                            0f, 0f,
                                                            0f, 0f
                                                        ),
                                                        Path.Direction.CW
                                                    )
                                                }
                                                // API 30 起改用 setPath（setConvexPath 已弃用），
                                                // 旧系统保留凸路径版本以维持圆角裁剪
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                    viewOutline.setPath(path)
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    viewOutline.setConvexPath(path)
                                                }
                                            }
                                        }
                                        clipToOutline = true
                                        this.player = player
                                    }
                            },
                            onRelease = { it.player = null },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 加载中：缓冲转圈
                        if (playerError == null &&
                            (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp)
                            )
                        }

                        // 播放失败：回退展示封面
                        if (playerError != null) {
                            if (video.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = video.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = stringResource(R.string.video_preview_unavailable),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        // “预览”角标
                        Text(
                            text = stringResource(R.string.video_preview),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }

                    // 标题与元信息
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = buildList {
                                    if (video.code.isNotBlank()) add(video.code)
                                    if (video.durationText.isNotBlank()) add(video.durationText)
                                    if (video.viewsDisplay.isNotBlank()) add(video.viewsDisplay)
                                }.joinToString("  ·  "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 收藏 / 关注类“点亮”动效：当 [active] 由 false 变为 true 时，
 * 图标先收缩到 0.4 再冲到 1.3，最后带回弹地落回 1.0（点赞式 pop）；
 * 变为 false 时立即复位，不做动画。
 *
 * 返回当前缩放值，调用方挂在图标上即可：
 * ```
 * val scale = rememberTogglePopScale(active = isFavorite)
 * Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
 * ```
 * 初始即 active（如进页面时该内容已收藏）不会弹，只在状态翻转时触发。
 */
@Composable
fun rememberTogglePopScale(active: Boolean): Float {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            scale.snapTo(0.4f)
            scale.animateTo(1.3f, animationSpec = Motion.Overlay)
            scale.animateTo(targetValue = 1f, animationSpec = Motion.PopSpring)
        } else {
            scale.snapTo(1f)
        }
    }
    return scale.value
}