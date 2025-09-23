package com.android.v2rayForAndroidUI.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.ui.tooling.preview.Preview

class ArcBottomShape(private val arcHeight: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ) = androidx.compose.ui.graphics.Outline.Generic(Path().apply {
        reset()
        // 左上角
        moveTo(0f, 0f)
        // 右上角
        lineTo(size.width, 0f)
        // 右下角（开始画弧线）
        lineTo(size.width, size.height - arcHeight)

        // 画一个二次贝塞尔曲线（从右下到底部弧线再到左下）
        quadraticBezierTo(
            size.width / 2, size.height + arcHeight, // 控制点
            0f, size.height - arcHeight              // 终点
        )

        close()
    })
}

@Composable
@Preview
fun ArcSurfaceDemo() {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .fillMaxHeight(0.8f),
        shape = ArcBottomShape(arcHeight = 100f) // 调整弧度
    ) {
        Box(
            modifier = Modifier
                .background(androidx.compose.ui.graphics.Color.Cyan)
        )
    }
}
