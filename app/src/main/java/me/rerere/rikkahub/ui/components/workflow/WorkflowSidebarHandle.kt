package me.rerere.rikkahub.ui.components.workflow

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun WorkflowSidebarHandle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handleSize = 36.dp
    val iconSize = 30.dp

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val maxX = with(density) { (maxWidth - handleSize).toPx().coerceAtLeast(0f) }
        val maxY = with(density) { (maxHeight - handleSize).toPx().coerceAtLeast(0f) }
        var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
        var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
        var initialized by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(maxX, maxY) {
            if (!initialized) {
                // 初始位置放在右上角
                offsetX = maxX
                offsetY = maxY * 0.25f
                initialized = true
            } else {
                // 限制在屏幕范围内
                offsetX = offsetX.coerceIn(0f, maxX * 1.1f)
                offsetY = offsetY.coerceIn(0f, maxY * 1.1f)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxY)
                    }
                }
                .pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                }
        ) {
            // 黑色圆环图标
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .border(
                        width = 1.dp,
                        color = Color.Black,
                        shape = CircleShape
                    )
            )
        }
    }
}
