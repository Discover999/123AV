package com.av123.video.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 站点 --color-gradient 两端色（品牌渐变，固定色，不随动态取色变化） */
private val brandGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFF8A5C), Color(0xFFFF4D8D))
)

/**
 * 品牌字标，复刻站点 logo 结构 `<b>123</b><span>MV</span><i></i>`：
 * - “123”：站点品牌渐变（--color-gradient，135° #ff8a5c → #ff4d8d）；
 * - “MV”：跟随主题正文色（对应 --color-text-1），深色/浅色模式自适应；
 * - 尾部 i：小尺寸渐变装饰圆块。
 * 字重 ExtraBold、字距 -0.03em，与站点 --weight-extrabold / letter-spacing 一致。
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge
) {
    val tightSpacing = (-0.03f * style.fontSize.value).sp
    val scheme = MaterialTheme.colorScheme
    // 浅色主题下 onSurface 接近纯黑，和渐变“123”并排偏重；只压淡一档，暗色保持原样
    val avColor = if (scheme.onSurface.luminance() < 0.5f) {
        // 浅色主题用二级文字色：比 onSurface 轻一档，且是实色
        scheme.onSurfaceVariant
    } else {
        scheme.onSurface
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "123",
            style = style.copy(
                brush = brandGradient,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = tightSpacing
            )
        )
        Text(
            text = "AV",
            style = style.copy(
                color = avColor,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = tightSpacing
            )
        )
        // 尾部就是句号本身：直接用「.」字符，天然落在基线上、随字号缩放，
        // 不需要再手算偏移（用 Box 时要么飘到字上方、要么突出底边）
        Text(
            text = ".",
            style = style.copy(
                brush = brandGradient,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp
            )
        )
    }
}
