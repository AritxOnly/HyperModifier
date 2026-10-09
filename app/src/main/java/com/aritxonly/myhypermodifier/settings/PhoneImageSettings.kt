package com.aritxonly.myhypermodifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal object PhoneImageSettings {
    fun profileKey(source: String, detected: String?): String =
        if (source == "auto") detected ?: "auto" else source

    fun transform(settings: ModifierSettings, key: String): FloatArray =
        PhoneImageProfile.read(settings.aboutPhoneImageTransforms, key)

    fun withTransform(settings: ModifierSettings, key: String, x: Float, y: Float, scale: Float): ModifierSettings {
        val profiles = runCatching { JSONObject(settings.aboutPhoneImageTransforms) }.getOrElse { JSONObject() }
        profiles.put(key, JSONObject().put("x", PhoneImageGeometry.offset(x))
            .put("y", PhoneImageGeometry.offset(y)).put("scale", PhoneImageGeometry.scale(scale)))
        return settings.copy(aboutPhoneImageTransforms = profiles.toString())
    }

    fun loadPreview(context: Context, settings: ModifierSettings, detected: String?): Bitmap? {
        if (settings.aboutPhoneImageSource == "custom") {
            if (settings.aboutPhoneCustomImage.isEmpty()) return null
            val bytes = Base64.decode(settings.aboutPhoneCustomImage, Base64.NO_WRAP)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        val key = profileKey(settings.aboutPhoneImageSource, detected)
        val encoded = ModifierSettingsStore.cachedPhonePreset(context, key)
        if (encoded.isEmpty()) return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        return decode(bytes)
    }

    /** Keep imported images self-contained and bounded for the cross-process preference mirror. */
    fun importImage(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val data = input.readNBytes(8 * 1024 * 1024 + 1)
            require(data.size <= 8 * 1024 * 1024) { "请选择不超过 8 MB 的图片。" }
            data
        } ?: error("无法读取图片。")
        return encodeImage(bytes)
    }

    fun encodeImage(bytes: ByteArray): String {
        var bitmap = decode(bytes) ?: error("无法识别图片，请选择 PNG、JPEG 或 WebP。")
        try {
            while (true) {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
                val encoded = output.toByteArray()
                if (encoded.size <= 96 * 1024) return Base64.encodeToString(encoded, Base64.NO_WRAP)
                val width = (bitmap.width * .75f).toInt().coerceAtLeast(1)
                val height = (bitmap.height * .75f).toInt().coerceAtLeast(1)
                require(width != bitmap.width || height != bitmap.height) { "无法处理这张图片。" }
                val smaller = Bitmap.createScaledBitmap(bitmap, width, height, true)
                bitmap.recycle(); bitmap = smaller
            }
        } finally { bitmap.recycle() }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
