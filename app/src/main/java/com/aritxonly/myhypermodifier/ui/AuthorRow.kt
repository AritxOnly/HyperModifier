package com.aritxonly.myhypermodifier

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AuthorRow(name: String, asset: String, url: String) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val avatar by produceState<ImageBitmap?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                appContext.assets.open(asset).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "打开 $name 的 GitHub") {
        context.openWebPage(url)
    }.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape)) {
            avatar?.let { Image(it, "$name 头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column {
            Text(name, style = MiuixTheme.textStyles.body1)
            Text("作者 · GitHub", style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
