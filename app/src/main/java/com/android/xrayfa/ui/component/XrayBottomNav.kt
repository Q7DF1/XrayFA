package com.android.xrayfa.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.Logcat
import com.android.xrayfa.ui.navigation.NavigateDestination
import kotlin.collections.forEach

@Deprecated("XrayBottomNavOpt")
@Composable
fun XrayBottomNav(
    items: List<NavigateDestination>,
    currentScreen: NavigateDestination,
    onItemSelected: (NavigateDestination) -> Unit,
    labelProvider: (NavigateDestination) -> String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.route == currentScreen.route
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.14f else 1f,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                )
                val labelPadding by animateDpAsState(if (selected) 8.dp else 0.dp)

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) selectedColor.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onItemSelected(item) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.route,
                        tint = if (selected) selectedColor else unselectedColor,
                        modifier = Modifier
                            .size(28.dp)
                            .scale(iconScale)
                    )
                    Spacer(Modifier.width(labelPadding))
                    Text(
                        text = labelProvider(item),
                        color = if (selected) selectedColor else unselectedColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
}


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun XrayBottomNavOpt(
    items: List<NavigateDestination>,
    currentScreen: NavigateDestination,
    onItemSelected: (NavigateDestination) -> Unit,
    labelProvider: (NavigateDestination) -> String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
) {
    val density = LocalDensity.current
    val itemCount = items.size
    val selectedIndex = items.indexOfFirst { it.route == currentScreen.route }.coerceAtLeast(0)

    val animOffsetX = remember { Animatable(0f) }
    val animWidth = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val heightDp = 48.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(backgroundColor)
            .padding(horizontal = 8.dp)
    ) {
        val itemWidthPx = constraints.maxWidth / itemCount
        val itemWidthDp = with(density) { itemWidthPx.toDp() }

        // 动画控制背景位置和宽度
        LaunchedEffect(selectedIndex, itemWidthPx) {
            animOffsetX.animateTo(
                targetValue = selectedIndex * itemWidthPx.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                ))
            animWidth.animateTo(itemWidthPx.toFloat(), tween(300))
        }

        // 背景放大镜
        Box(
            modifier = Modifier
                .offset { IntOffset(animOffsetX.value.toInt(), 0) }
                .width(with(density) { animWidth.value.toDp() })
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(selectedColor.copy(alpha = 0.12f))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val iconScale by animateFloatAsState(if (selected) 1.14f else 1f, tween(300))
                val labelPadding by animateDpAsState(if (selected) 8.dp else 0.dp, tween(300))

                Box(
                    modifier = Modifier
                        .width(itemWidthDp)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onItemSelected(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.route,
                            tint = if (selected) selectedColor else unselectedColor,
                            modifier = Modifier.size(28.dp).scale(iconScale)
                        )
                        Spacer(Modifier.width(labelPadding))
                        if (selected) {
                            Text(
                                text = labelProvider(item),
                                color = selectedColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun FloatingBottomNav(
    currentScreen: NavigateDestination,
    onItemSelected: (NavigateDestination) -> Unit = {},
) {

    var isOn by remember { mutableStateOf(false) }
    val transition = updateTransition(targetState = isOn, label = "switchTransition")

    val ballOffset by transition.animateDp(
        label = "ballOffset",
        transitionSpec = { spring(stiffness = Spring.StiffnessMedium) }
    ) { state ->
        if (state) 30.dp else 0.dp // 球移动距离
    }

    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier.background(Color.Gray,RoundedCornerShape(32.dp))
                .padding(end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = ballOffset) // 核心：动画偏移
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .shadow(2.dp, CircleShape)
                    .clickable{isOn  = !isOn}
                    .align(Alignment.CenterStart)
            )
            Row(
            ) {
                ItemTab(
                    icon = Config.icon,
                    title = stringResource(Config.title),
                )
                ItemTab(
                    icon = Home.icon,
                    title = stringResource(Home.title),
                )
            }

        }

        Surface(
            color = Color.Gray,
            shape = CircleShape,
        ) {
            ItemTab(
                icon = Logcat.icon,
                title = stringResource(Logcat.title),
            )
        }

    }
}


@Composable
fun ComposeLikeSwitch(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    thumbSize: Dp = 32.dp,
    trackHeight: Dp = 40.dp,
    trackPadding: Dp = 4.dp,
    checkedColor: Color = Color(0xFF4CAF50),
    uncheckedColor: Color = Color.Gray,
    content: @Composable RowScope.() -> Unit // 允许你放ItemTab
) {
    val transition = updateTransition(targetState = checked, label = "switchTransition")

    // track 颜色动画
    val trackColor by transition.animateColor(label = "trackColor") { isChecked ->
        if (isChecked) checkedColor else uncheckedColor
    }

    // 偏移动画
    val offsetFraction by transition.animateFloat(
        transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) },
        label = "thumbOffset"
    ) { isChecked ->
        if (isChecked) 1f else 0f
    }

    var trackWidth by remember { mutableStateOf(0) }
    var thumbWidth by remember { mutableStateOf(0) }

    // 根据 fraction 动态计算 thumb 偏移
    val offsetDp = with(LocalDensity.current) {
        ((trackWidth - thumbWidth - trackPadding.toPx() * 2) * offsetFraction).toDp()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .padding(trackPadding)
            .onGloballyPositioned { trackWidth = it.size.width }
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        // 你自定义的 Row 内容
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // thumb 小球
        Box(
            modifier = Modifier
                .onGloballyPositioned { thumbWidth = it.size.width }
                .offset(x = offsetDp)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White)
                .shadow(2.dp, CircleShape)
        )
    }
}



@Composable
internal fun ItemTab(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp)
            .wrapContentWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title)
    }
}