package me.rerere.rikkahub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 跟随"按钮阴影"设置给按钮/容器加投影。 */
@Composable
fun Modifier.appButtonShadow(shape: Shape, elevation: Dp = 4.dp): Modifier =
    if (LocalButtonShadow.current) {
        shadow(elevation = elevation, shape = shape, clip = false)
    } else {
        this
    }
