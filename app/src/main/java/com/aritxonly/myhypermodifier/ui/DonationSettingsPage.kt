package com.aritxonly.myhypermodifier

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class DonationMethod(val label: String, val asset: String) {
    WeChat("微信支付", "donation/wechat.png"),
    Alipay("支付宝", "donation/alipay.jpg"),
}

@Composable
internal fun DonationSettingsPage(padding: PaddingValues, onScroll: (Float) -> Unit) {
    val context = LocalContext.current.applicationContext
    var selected by rememberSaveable { mutableIntStateOf(0) }
    SettingsScrollPage(padding, onScroll) {
        SettingsSection {
            Text(
                "感谢你的支持",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp),
            )
            Text(
                "如果 HyperModifier 对你有所帮助，欢迎自愿捐赠，支持持续开发与维护。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
        // Use Miuix's plain TabRow: the glass wrapper would sample its own recorded page.
        TabRow(
            tabs = listOf("微信支付", "支付宝"),
            selectedTabIndex = selected,
            onTabSelected = { selected = it },
            modifier = Modifier.padding(vertical = 8.dp),
        )
        androidx.compose.runtime.key(selected) {
            DonationCodeCard(context, DonationMethod.entries[selected], Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DonationCodeCard(context: Context, method: DonationMethod, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val codeHeight = (LocalConfiguration.current.screenHeightDp.dp - 420.dp).coerceIn(220.dp, 380.dp)
    Column(modifier) {
        SettingsSection {
            // Small previews decode off the main thread. Saving still copies the full original.
            androidx.compose.runtime.key(method) {
                val imageResult by produceState<Result<ImageBitmap>?>(null, context, method) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            context.assets.open(method.asset).use {
                                requireNotNull(BitmapFactory.decodeStream(
                                    it, null, BitmapFactory.Options().apply { inSampleSize = 2 },
                                )).asImageBitmap()
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.widthIn(max = 360.dp).fillMaxWidth()
                            .height(codeHeight).clip(RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val image = imageResult?.getOrNull()
                        if (image != null) {
                            Image(
                                bitmap = image,
                                contentDescription = "${method.label}捐赠收款码",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text(
                                if (imageResult == null) "正在加载收款码…" else "收款码加载失败，请重新进入页面",
                                style = MiuixTheme.textStyles.body2,
                                modifier = Modifier.padding(22.dp),
                            )
                        }
                    }
                }
            }
        }
        Text(
            "保存后，打开${method.label}扫一扫，从相册选择收款码。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
        TextButton(
            text = if (saving) "正在保存…" else "保存收款码",
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
            onClick = {
                saving = true
                scope.launch {
                    try {
                        val success = withContext(Dispatchers.IO) { saveDonationCode(context, method) }
                        Toast.makeText(context, if (success) "收款码已保存到相册" else "保存失败，请重试", Toast.LENGTH_SHORT).show()
                    } finally {
                        saving = false
                    }
                }
            },
        )
    }
}

/** Copy original bytes without recompressing or altering the payment QR code. */
private fun saveDonationCode(context: Context, method: DonationMethod): Boolean {
    val resolver = context.contentResolver
    var inserted: android.net.Uri? = null
    return try {
        val extension = method.asset.substringAfterLast('.')
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "HyperModifier_${method.name}_${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, if (extension == "png") "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/HyperModifier")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        inserted = uri
        requireNotNull(resolver.openOutputStream(uri)).use { output ->
            context.assets.open(method.asset).use { it.copyTo(output) }
        }
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0)
        true
    } catch (_: Exception) {
        inserted?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
        false
    }
}
