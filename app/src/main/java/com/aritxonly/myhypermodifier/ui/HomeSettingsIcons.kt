package com.aritxonly.myhypermodifier

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Exact white silhouette from design/config-icon-mask.svg (viewBox 0 0 100 100).
private val HomeIconShape = GenericShape { size, _ ->
    val x = size.width / 100f
    val y = size.height / 100f
    moveTo(0f, 42.7f * y)
    cubicTo(0f, 27.7f * y, 0f, 20.3f * y, 2.9f * x, 14.6f * y)
    cubicTo(5.5f * x, 9.5f * y, 9.5f * x, 5.5f * y, 14.6f * x, 2.9f * y)
    cubicTo(20.3f * x, 0f, 27.7f * x, 0f, 42.7f * x, 0f)
    lineTo(57.3f * x, 0f)
    cubicTo(72.3f * x, 0f, 79.7f * x, 0f, 85.4f * x, 2.9f * y)
    cubicTo(90.5f * x, 5.5f * y, 94.5f * x, 9.5f * y, 97.1f * x, 14.6f * y)
    cubicTo(100f * x, 20.3f * y, 100f * x, 27.7f * y, 100f * x, 42.7f * y)
    lineTo(100f * x, 57.3f * y)
    cubicTo(100f * x, 72.3f * y, 100f * x, 79.7f * y, 97.1f * x, 85.4f * y)
    cubicTo(94.5f * x, 90.5f * y, 90.5f * x, 94.5f * y, 85.4f * x, 97.1f * y)
    cubicTo(79.7f * x, 100f * y, 72.3f * x, 100f * y, 57.3f * x, 100f * y)
    lineTo(42.7f * x, 100f * y)
    cubicTo(27.7f * x, 100f * y, 20.3f * x, 100f * y, 14.6f * x, 97.1f * y)
    cubicTo(9.5f * x, 94.5f * y, 5.5f * x, 90.5f * y, 2.9f * x, 85.4f * y)
    cubicTo(0f, 79.7f * y, 0f, 72.3f * y, 0f, 57.3f * y)
    close()
}
private val HomeIconSize = 40.dp

@Composable
internal fun HomeAppIcon(packageName: String) {
    val context = LocalContext.current
    val icon = remember(context, packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(HomeIconSize).clip(HomeIconShape)
                .background(MiuixTheme.colorScheme.surfaceVariant),
        )
    } else {
        HomeSystemIcon(R.drawable.ic_home_apps, Color(0xFF7484A1))
    }
}

@Composable
internal fun HomeSystemIcon(@DrawableRes iconRes: Int, color: Color) {
    Box(
        modifier = Modifier.size(HomeIconSize).clip(HomeIconShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}
