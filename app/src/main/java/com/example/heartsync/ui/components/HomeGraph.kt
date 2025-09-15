// app/src/main/java/com/example/heartsync/ui/components/HomeGraph.kt
package com.example.heartsync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.heartsync.data.model.GraphState

@Composable
fun HomeGraphSection(
    state: GraphState,
    modifier: Modifier = Modifier   // ✅ 기본값은 항상 Modifier
) {
    // 한쪽만 있어도 그리기
    val hasData = state.smoothedL.isNotEmpty() || state.smoothedR.isNotEmpty()

    val containerMod = modifier
        .fillMaxWidth()
        .height(220.dp)
        .padding(horizontal = 16.dp)

    if (!hasData) {
        Box(
            modifier = containerMod
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "데이터가 없습니다",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    DualLineGraph(
        left = state.smoothedL,
        right = state.smoothedR,
        modifier = containerMod
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    )
}

@Composable
private fun DualLineGraph(
    left: List<Float>,
    right: List<Float>,
    modifier: Modifier = Modifier
) {
    // ✅ 색/스타일은 Composable에서 미리 읽어 변수로 넘김 (Canvas 안에서 호출 금지)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val leftColor = MaterialTheme.colorScheme.primary
    val rightColor = MaterialTheme.colorScheme.tertiary

    val all = remember(left, right) { left + right }
    val minY = all.minOrNull() ?: 0f
    val maxY = all.maxOrNull() ?: 1f
    val yRange = if (maxY == minY) 1f else (maxY - minY)
    val n = maxOf(left.size, right.size).coerceAtLeast(2)

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val w = size.width
        val h = size.height
        val paddingX = 24f
        val paddingY = 18f
        val plotW = w - paddingX * 2
        val plotH = h - paddingY * 2

        // 가로 그리드
        val grid = 4
        repeat(grid + 1) { i ->
            val y = paddingY + plotH * i / grid
            drawLine(
                color = gridColor,       // ✅ 미리 뽑아둔 색 사용
                start = Offset(paddingX, y),
                end = Offset(paddingX + plotW, y),
                strokeWidth = 1f
            )
        }

        fun yMap(v: Float): Float {
            val norm = (v - minY) / yRange
            return paddingY + plotH * (1f - norm)
        }
        fun xAt(i: Int): Float {
            val denom = (n - 1).coerceAtLeast(1)
            return paddingX + plotW * i / denom
        }

        // Left 라인 (있을 때만)
        if (left.isNotEmpty()) {
            val pathL = Path()
            left.forEachIndexed { i, v ->
                val p = Offset(xAt(i.coerceAtMost(n - 1)), yMap(v))
                if (i == 0) pathL.moveTo(p.x, p.y) else pathL.lineTo(p.x, p.y)
            }
            drawPath(
                path = pathL,
                color = leftColor,       // ✅ 미리 뽑아둔 색 사용
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Right 라인 (있을 때만)
        if (right.isNotEmpty()) {
            val pathR = Path()
            right.forEachIndexed { i, v ->
                val p = Offset(xAt(i.coerceAtMost(n - 1)), yMap(v))
                if (i == 0) pathR.moveTo(p.x, p.y) else pathR.lineTo(p.x, p.y)
            }
            drawPath(
                path = pathR,
                color = rightColor,      // ✅ 미리 뽑아둔 색 사용
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
