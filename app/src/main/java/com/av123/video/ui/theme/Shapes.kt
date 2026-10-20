package com.av123.video.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 全 App 统一的圆角档位。
 * 除底栏的形变圆角（随 morph 变化）外，新增 UI 一律从这里取值，不要再写具体 dp。
 */
object AppShapes {
    /** 角标、小标签、小色块 */
    val Small: Shape = RoundedCornerShape(8.dp)

    /** 视频缩略图卡片（用户确认过的 10dp） */
    val Compact: Shape = RoundedCornerShape(10.dp)

    /** 缩略图、输入框、次级容器 */
    val Medium: Shape = RoundedCornerShape(12.dp)

    /** 标准卡片 */
    val Large: Shape = RoundedCornerShape(16.dp)

    /** 精选卡、弹层、较大容器 */
    val ExtraLarge: Shape = RoundedCornerShape(20.dp)

    /** 头部大卡、锁屏面板 */
    val Hero: Shape = RoundedCornerShape(28.dp)

    /** 全圆角胶囊 */
    val Pill: Shape = CircleShape
}
