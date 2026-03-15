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
        val maxX = with(density) { (maxWidth - handleSize).toPx() }
        val maxY = with(density) { (maxHeight - handleSize).toPx() }
        
        var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
        var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
        var initialized by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(maxX, maxY) {
            if (!initialized) {
                // 初始位置：右上角偏下25%
                offsetX = maxX.coerceAtLeast(0f)
                offsetY = (maxY * 0.25f).coerceIn(0f, maxY.coerceAtLeast(0f))
                initialized = true
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // 允许在全屏任何位置移动，包括部分超出边界
                        offsetX = (offsetX + dragAmount.x).coerceIn(
                            -handleSize.toPx() / 2, 
                            maxX + handleSize.toPx() / 2
                        )
                        offsetY = (offsetY + dragAmount.y).coerceIn(
                            -handleSize.toPx() / 2, 
                            maxY + handleSize.toPx() / 2
                        )
                    }
                }
                .pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                }
                .size(handleSize),
            contentAlignment = Alignment.Center
        ) {
            // 浅灰半透明细圆环
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .border(
                        width = 0.5.dp,
                        color = Color.Gray.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            )
        }
    }
}
