package com.aritxonly.myhypermodifier

import org.json.JSONObject

/** Portable, versioned configuration only; callers supply the current supported key/type schema. */
internal object SettingsBackupCodec {
    const val MAX_BYTES = 4 * 1024 * 1024
    private const val FORMAT = "com.aritxonly.myhypermodifier.config"

    fun encode(values: Map<String, Any>): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", 1)
        put("settings", JSONObject(values))
    }.toString(2).also {
        require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "配置超过备份大小限制" }
    }

    fun decode(text: String, schema: Map<String, Any>): Map<String, Any> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份文件过大" }
        val root = JSONObject(text)
        require(root.get("format") == FORMAT && root.get("version") == 1) { "不支持的备份格式或版本" }
        val values = root.getJSONObject("settings")
        require(values.length() > 0) { "备份中没有配置" }
        return buildMap {
            values.keys().forEach { key ->
                val expected = schema[key] ?: error("备份包含不支持的配置：$key")
                val value = values.get(key)
                val converted: Any = when (expected) {
                    is Boolean -> { require(value is Boolean) { "配置类型错误：$key" }; value }
                    is String -> { require(value is String) { "配置类型错误：$key" }; value }
                    is Int -> {
                        require(value is Int || value is Long) { "配置类型错误：$key" }
                        val number = (value as Number).toLong()
                        require(number in Int.MIN_VALUE..Int.MAX_VALUE) { "配置数值越界：$key" }
                        number.toInt()
                    }
                    is Float -> {
                        require(value is Number) { "配置类型错误：$key" }
                        value.toFloat().also { require(it.isFinite()) { "配置数值越界：$key" } }
                    }
                    else -> error("不支持的配置类型：$key")
                }
                put(key, converted)
            }
        }
    }
}
