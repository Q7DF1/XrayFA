package com.android.xrayfa.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.xrayfa.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp

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
            HomeContent()
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
        //These colors need to be added to theme folder/ appropriate folder
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

@Composable
fun HomeContent(modifier: Modifier = Modifier) {
    Box (
        modifier = modifier.fillMaxSize()
    ){
        TopBar(modifier = modifier.align(Alignment.TopCenter))

        PowerSection(modifier = modifier.align(Alignment.Center)
            .align(Alignment.Center)
            .padding(bottom = 450.dp))

        ConnectionStatus(modifier = modifier.align(Alignment.Center)
            .offset(y = -90.dp)
            .offset(x = -50.dp))

        SpeedPill(modifier = modifier.align(Alignment.Center)
            .offset(y = 80.dp)) //NEEDS ADJUSTING

        BottomNavPill(modifier = modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun TopBar(modifier: Modifier = Modifier){
    Box( modifier = modifier.fillMaxWidth()
        .height(100.dp)
    ){
        Text("XrayFA", fontSize = 35.sp , color = Color.White, modifier = Modifier.align(Alignment.Center).padding(20.dp))
        //Side menu bar (on click nothing for now)
        IconButton(onClick = {},
            modifier = Modifier.align(Alignment.CenterStart)
            .size(80.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.menu_bar),
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier.size(60.dp)

            )
        }
    }
}
/*
@Composable
fun PowerSection(modifier: Modifier = Modifier){
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ){
    Canvas(modifier = Modifier.fillMaxSize()
        .padding(bottom = 400.dp)){
        drawCircle(
            color = Color.White,
            radius = 300f
        )
        drawCircle(
            color = Color(0xFFBECBD2).copy(alpha = 0.8f),
            radius = 300f,
            style = Stroke(width = 20f)
        )
    }
        Icon(
            painter = painterResource(R.drawable.on_button),
            contentDescription = "On Button",
            tint = Color.Unspecified,
            /*
            modifier = Modifier.size(300.dp)
            .padding(bottom = 100.dp)

             */
        )
    }
}

 */
@Composable
fun PowerSection(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 2
            )

            drawCircle(
                color = Color(0xFFBECBD2).copy(alpha = 0.8f),
                radius = size.minDimension / 2,
                style = Stroke(width = 20f)
            )
        }
        Icon(
            painter = painterResource(R.drawable.on_button),
            contentDescription = "Power",
            tint = Color.Unspecified,
            modifier = Modifier.size(160.dp)
                .align(Alignment.Center)
                .offset(x = 5.dp)
        )
    }
}
@Composable
fun ConnectionStatus(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.width(200.dp)
        .height(50.dp),
        contentAlignment = Alignment.Center
    ) {
        val connectionStat = true
        if(connectionStat) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.Green,
                    radius = 25f
                )
            }
            Text("Connected", fontSize = 20.sp , color = Color.White, modifier = Modifier.align(Alignment.Center).offset(x = 65.dp).offset(y = 2.dp))
            Text("Server:233boy-Tokyo-01", fontSize = 15.sp , color = Color(0xFF3B5C83), modifier = Modifier.align(Alignment.CenterStart)
                .fillMaxWidth().height(20.dp).offset(y = 30.dp).offset(x = 80.dp))

        }


    }

}

@Composable
fun SpeedPill(modifier: Modifier = Modifier) {
    Box(
        modifier =  modifier.width(350.dp)
            .height(80.dp),
        contentAlignment = Alignment.Center //Content is not aligned at center

    ) {
        Canvas(modifier = Modifier.fillMaxSize()
        ) {
            drawRoundRect(
                color = Color(0xFF74CEFF).copy(alpha = 0.4f),
                size = size, //same size as cavas
                cornerRadius = CornerRadius(100f, 100f),
            )
        }
        Text("Upload", fontSize = 20.sp , color = Color(0xFF43A9FF), modifier = Modifier.align(Alignment.CenterStart).offset(x = 50.dp))
        Text("Download", fontSize = 20.sp , color = Color(0xFF43A9FF), modifier = Modifier.align(Alignment.CenterEnd).offset(x = -50.dp))
        Icon(
            painter = painterResource(R.drawable.arrow),
            contentDescription = "Power",
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp)
                .align(Alignment.TopStart)
                .offset(x = 20.dp)
                .offset(y = 30.dp)
                .rotate(90f)
        )
        Icon(
            painter = painterResource(R.drawable.arrow),
            contentDescription = "Arrow",
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp)
                .align(Alignment.BottomEnd)
                .offset(x = -20.dp)
                .offset(y = -30.dp)
                .rotate(270f)
        )
    }
}

@Composable
fun BottomNavPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.width(200.dp) .height(100.dp),
        contentAlignment = Alignment.Center //Content is not aligned at center

    ) {
        Canvas(modifier = Modifier.fillMaxSize()
        ) {
            drawRoundRect(
                color = Color(0xFFBECBD2).copy(alpha = 0.8f),
                size = Size(500f, 100f),
                cornerRadius = CornerRadius(40f, 40f)
            )
        }
    }
}
