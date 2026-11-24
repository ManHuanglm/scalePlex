package com.scaleplex.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColors(
    primary = Color(0xFF1E6FD9),
    primaryVariant = Color(0xFF1557B0),
    secondary = Color(0xFF00A884),
    background = Color(0xFFF5F6F8),
    surface = Color.White,
)

private val DarkColors = darkColors(
    primary = Color(0xFF4C8DFF),
    primaryVariant = Color(0xFF2F6FE0),
    secondary = Color(0xFF00C49A),
    background = Color(0xFF1E1F22),
    surface = Color(0xFF26282C),
)

/** 界面尺寸常量：统一侧边栏宽度与面板间距，避免各面板散落魔法数字。 */
object UiDimens {
    val SidebarWidth = 250.dp
    val PanelPadding = 12.dp
    val ControlGap = 8.dp
}

/** 状态与语义色：集中管理，避免界面层硬编码颜色。 */
object UiColors {
    val Online = Color(0xFF2E9E5B)
    val Connecting = Color(0xFFE0A426)
    val Offline = Color(0xFFD64545)
    val Unknown = Color(0xFF9AA0A6)
    val Success = Color(0xFF2E7D32)
    val Error = Color(0xFFC62828)
}

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colors = if (dark) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(8.dp),
        ),
        content = content,
    )
}
