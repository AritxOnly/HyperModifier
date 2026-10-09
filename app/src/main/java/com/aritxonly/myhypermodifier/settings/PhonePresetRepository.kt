package com.aritxonly.myhypermodifier

import android.content.Context
import org.json.JSONObject
import java.io.File
import android.net.Uri
import java.util.zip.ZipFile
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

internal data class PhonePreset(val id: String, val name: String, val url: String, val sha256: String, val file: String)

/** GitHub supplies originals; only the selected bounded thumbnail crosses into the hooked process. */
internal object PhonePresetRepository {
    const val DEFAULT_URL = "https://raw.githubusercontent.com/AritxOnly/HyperModifier/refs/heads/codex/phone-image-presets/phone-presets/manifest.json"
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_PACKAGE_BYTES = 128 * 1024 * 1024

    fun validUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "raw.githubusercontent.com" && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

    fun parseCatalog(text: String, manifestUrl: String): List<PhonePreset> {
        require(validUrl(manifestUrl)) { "请输入 GitHub raw 清单地址。" }
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1) { "不支持这个预设清单版本。" }
        val items = root.getJSONArray("presets")
        require(items.length() in 1..100) { "预设清单数量不正确。" }
        val result = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val id = item.getString("id")
            require(id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}")) && id != "auto" && id != "custom")
            val name = item.getString("name")
            require(name.length in 1..80)
            val path = item.getString("file")
            require(path.matches(Regex("images/[a-zA-Z0-9_-]+\\.(png|webp|jpg|jpeg)"))) { "图片路径不正确。" }
            val url = URI(manifestUrl).resolve(path).toString()
            require(validUrl(url)) { "图片地址必须来自 GitHub raw。" }
            val hash = item.getString("sha256").lowercase()
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "图片缺少有效的校验值。" }
            PhonePreset(id, name, url, hash, path)
        }
        require(result.map { it.id }.distinct().size == result.size) { "预设 ID 重复。" }
        return result
    }

    @Synchronized
    fun catalog(context: Context, url: String, refresh: Boolean = false): List<PhonePreset> {
        require(validUrl(url)) { "请输入 GitHub raw 清单地址。" }
        val file = catalogFile(context, url)
        if (!refresh && file.isFile) return parseCatalog(file.readText(), url)
        val bytes = download(url, 256 * 1024)
        val result = parseCatalog(bytes.toString(Charsets.UTF_8), url)
        writeAtomically(file, bytes)
        return result
    }

    fun ensureSelected(context: Context, settings: ModifierSettings, refresh: Boolean = false): String? {
        if (settings.aboutPhoneImageSource == "custom") return null
        val key = PhoneImageSettings.profileKey(settings.aboutPhoneImageSource, PhoneProductPolicy.currentAsset())
        if (key == "auto") return null // Unknown hardware keeps the generic illustration.
        val preset = catalog(context, settings.aboutPhonePresetUrl, refresh).firstOrNull { it.id == key }
            ?: error("清单中找不到当前机型的预设图片。")
        val encoded = PhoneImageSettings.encodeImage(ensureImage(context, preset))
        val latest = ModifierSettingsStore.load(context)
        val latestKey = PhoneImageSettings.profileKey(latest.aboutPhoneImageSource, PhoneProductPolicy.currentAsset())
        if (latestKey == key && latest.aboutPhonePresetUrl == settings.aboutPhonePresetUrl) {
            ModifierSettingsStore.cachePhonePreset(context, key, encoded)
        }
        return key
    }

    /** Cache every original for use without a connection, including later model selections. */
    fun downloadAll(context: Context, url: String): Int {
        val items = catalog(context, url)
        items.forEach { ensureImage(context, it) }
        return items.size
    }

    fun cachedCatalog(context: Context, url: String): List<PhonePreset> = runCatching {
        parseCatalog(catalogFile(context, url).readText(), url)
    }.getOrDefault(emptyList())

    /** Validate the complete package before publishing its manifest. No ZIP paths are extracted. */
    @Synchronized
    fun importPackage(context: Context, uri: Uri, url: String): List<PhonePreset> {
        val archive = File.createTempFile("phone-presets-", ".zip", context.cacheDir)
        val staged = mutableListOf<Pair<PhonePreset, File>>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_PACKAGE_BYTES) { "预设包不能超过 128 MB。" }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("无法读取预设包。")
            val manifest: ByteArray
            val items: List<PhonePreset>
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                require(entries.size <= 120 && entries.map { it.name }.distinct().size == entries.size) {
                    "预设包文件数量不正确或存在重复文件。"
                }
                require(entries.all { !it.name.startsWith("/") && !it.name.contains("\\") &&
                    it.name.split('/').none { part -> part == ".." } }) { "预设包包含无效路径。" }
                val entry = zip.getEntry("manifest.json") ?: error("预设包缺少 manifest.json。")
                manifest = zip.getInputStream(entry).use { it.readNBytes(256 * 1024 + 1) }
                require(manifest.size <= 256 * 1024) { "预设清单过大。" }
                items = parseCatalog(manifest.toString(Charsets.UTF_8), url)
                var total = 0L
                items.forEach { preset ->
                    val imageEntry = zip.getEntry(preset.file) ?: error("预设包缺少 ${preset.name} 的图片。")
                    val bytes = zip.getInputStream(imageEntry).use { it.readNBytes(MAX_IMAGE_BYTES + 1) }
                    total += bytes.size
                    require(bytes.size <= MAX_IMAGE_BYTES && total <= MAX_PACKAGE_BYTES) { "预设包图片过大。" }
                    require(hash(bytes) == preset.sha256) { "${preset.name} 的图片校验失败。" }
                    // Also reject checksum-valid files that Android cannot decode.
                    PhoneImageSettings.encodeImage(bytes)
                    val file = File.createTempFile("phone-image-", ".png", context.cacheDir)
                    staged.add(preset to file)
                    file.writeBytes(bytes)
                }
            }
            staged.forEach { (preset, file) -> writeAtomically(imageFile(context, preset), file.readBytes()) }
            writeAtomically(catalogFile(context, url), manifest)
            return items
        } finally {
            archive.delete()
            staged.forEach { it.second.delete() }
        }
    }

    private fun ensureImage(context: Context, preset: PhonePreset): ByteArray {
        val file = imageFile(context, preset)
        if (file.isFile && file.length() <= MAX_IMAGE_BYTES) {
            val bytes = file.readBytes()
            if (hash(bytes) == preset.sha256) return bytes
        }
        val bytes = download(preset.url, MAX_IMAGE_BYTES)
        require(hash(bytes) == preset.sha256) { "图片校验失败，请重试。" }
        PhoneImageSettings.encodeImage(bytes)
        writeAtomically(file, bytes)
        return bytes
    }

    private fun catalogFile(context: Context, url: String) =
        File(directory(context), "manifest-${hash(url.toByteArray()).take(16)}.json")
    private fun imageFile(context: Context, preset: PhonePreset) =
        File(directory(context), "${preset.id}-${preset.sha256}.png")

    private fun directory(context: Context): File = File(context.filesDir, "phone-presets").apply { mkdirs() }
    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun download(url: String, limit: Int): ByteArray {
        require(validUrl(url))
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        try {
            require(connection.responseCode == 200) { "GitHub 下载失败（${connection.responseCode}）。" }
            require(connection.contentLengthLong <= limit) { "文件超过大小限制。" }
            return connection.inputStream.use {
                val bytes = it.readNBytes(limit + 1)
                require(bytes.size <= limit) { "文件超过大小限制。" }
                bytes
            }
        } finally { connection.disconnect() }
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val atomic = android.util.AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }
}
