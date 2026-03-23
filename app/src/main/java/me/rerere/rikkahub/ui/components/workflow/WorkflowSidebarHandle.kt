package me.rerere.rikkahub.ui.components.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.GripVertical
import kotlin.math.roundToInt

/**
 * 自定义工作流悬浮手柄
 * - 使用圆形渐变背景
 * - 可拖拽定位
 * - 点击展开工作流面板
 */
@Composable
fun WorkflowSidebarHandle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handleSize = 48.dp

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val maxY = with(density) { (maxHeight - handleSize).toPx().coerceAtLeast(0f) }
        var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
        var initialized by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(maxY) {
            if (!initialized) {
                offsetY = maxY * 0.35f
                initialized = true
            } else {
                offsetY = offsetY.coerceIn(0f, maxY)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .pointerInput(maxY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxY)
                    }
                }
                .pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                }
        ) {
            // 外层阴影
            Box(
                modifier = Modifier
                    .size(handleSize)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 内层白色图标
                Icon(
                    imageVector = Lucide.GripVertical,
                    contentDescription = "工作流",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
