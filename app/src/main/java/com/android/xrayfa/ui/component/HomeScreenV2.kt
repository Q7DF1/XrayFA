package com.android.xrayfa.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@Preview
@Composable
fun HomeScreenV2Preview() {
    HomeScreenV2()
}
@Composable
fun HomeScreenV2() {
    Scaffold(
        containerColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HomeHeaderBackground(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun HomeHeaderBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ovalWidth = size.width * 1.25f
        val ovalHeight = size.height * 0.5f
        val brushStart1 = size.width* 0.25f //100
        val brushEnd1 = size.height * 0.9f
        val brushStart2 = size.width *0.5f
        val brushEnd2 = size.height * 1.75f
        val colorStops = arrayOf(
            0.0f to Color(0xFF4695EF),
            0.20f to Color(0xFF43A9FF),
            0.34f to Color(0xFF3BB2FF),
            0.58f to Color(0xFF3FAEFF),
            0.75f to Color(0xFF43A9FF),
            0.92f to Color(0xFF4B9FFF)
        )
        val colorStops2 = arrayOf(
            0.0f to Color(0xFF70C7F6),
            1.0f to Color(0xFF002199),
        )
        drawOval(
            //These colors need to be added to theme folder/ appropriate folder
            brush = Brush.linearGradient(
                colorStops = colorStops,
                start = Offset(brushStart1, size.width*0.125f), //(100,100)
                end =  Offset(brushEnd1, size.width*0.75f) //(360,300)

            ),
            size = Size(ovalWidth, size.height * 1.25f),
            topLeft = Offset(
                x = (size.width - ovalWidth) / 2f,
                y = -(size.height - ovalHeight) / 2f
            )
        )
        drawOval(
            //These colors need to be added to theme folder/ appropriate folder
            brush = Brush.linearGradient(
                colorStops = colorStops2,
                start = Offset(brushStart2, size.width*0.25f),//(200,100)
                end =  Offset(brushEnd2, size.width*0.875f) //(700,300 or 350)

            ),
            size = Size(ovalWidth, size.height * 1.25f),
            topLeft = Offset(
                x = (size.width - ovalWidth) / 2f,
                y = -(size.height - ovalHeight) / 2f
            )
        )
    }
}

