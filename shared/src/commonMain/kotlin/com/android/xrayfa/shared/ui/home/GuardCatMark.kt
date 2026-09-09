package com.android.xrayfa.shared.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.ui.theme.XrayBrand

@Composable
fun GuardCatMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    bodyTint: Color = XrayBrand.Blue,
    eyeTint: Color = XrayBrand.CatEyeAmber,
    contentDescription: String? = "XrayFA",
) {
    Box(modifier = modifier.size(size)) {
        Icon(
            imageVector = GuardCatBodyVector,
            contentDescription = contentDescription,
            tint = bodyTint,
            modifier = Modifier.size(size),
        )
        Icon(
            imageVector = GuardCatEyesVector,
            contentDescription = null,
            tint = eyeTint,
            modifier = Modifier.size(size),
        )
    }
}

private val GuardCatBodyVector: ImageVector
    get() {
        val cached = _guardCatBodyVector
        if (cached != null) return cached
        return ImageVector.Builder(
            name = "GuardCatBody",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 108f,
            viewportHeight = 108f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(38.18f, 30.27f)
                lineTo(47.785f, 39.31f)
                curveTo(50.61f, 37.05f, 57.39f, 37.05f, 60.215f, 39.31f)
                lineTo(69.82f, 30.27f)
                curveTo(72.08f, 36.485f, 74.905f, 44.395f, 74.905f, 54f)
                curveTo(74.905f, 63.04f, 72.08f, 69.255f, 65.865f, 73.21f)
                curveTo(61.345f, 75.47f, 56.825f, 76.035f, 54f, 77.73f)
                curveTo(51.175f, 76.035f, 46.655f, 75.47f, 42.135f, 73.21f)
                curveTo(35.92f, 69.255f, 33.095f, 63.04f, 33.095f, 54f)
                curveTo(33.095f, 44.395f, 35.92f, 36.485f, 38.18f, 30.27f)
                close()
            }
        }.build().also { _guardCatBodyVector = it }
    }

private val GuardCatEyesVector: ImageVector
    get() {
        val cached = _guardCatEyesVector
        if (cached != null) return cached
        return ImageVector.Builder(
            name = "GuardCatEyes",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 108f,
            viewportHeight = 108f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(40.89f, 53.435f)
                arcTo(5.2f, 5.2f, 0f, true, true, 51.29f, 53.435f)
                arcTo(5.2f, 5.2f, 0f, true, true, 40.89f, 53.435f)
                close()
                moveTo(56.71f, 53.435f)
                arcTo(5.2f, 5.2f, 0f, true, true, 67.11f, 53.435f)
                arcTo(5.2f, 5.2f, 0f, true, true, 56.71f, 53.435f)
                close()
            }
        }.build().also { _guardCatEyesVector = it }
    }

private var _guardCatBodyVector: ImageVector? = null
private var _guardCatEyesVector: ImageVector? = null
