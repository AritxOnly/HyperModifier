package com.aritxonly.myhypermodifier

import org.json.JSONObject

internal data class NavigationBarGeometry(
    val leftMarginDp: Float = 0f,
    val maximumWidthDp: Float = 0f,
    val heightDp: Float = 0f,
) {
    fun normalized(): NavigationBarGeometry = copy(
        leftMarginDp = leftMarginDp.coerceIn(0f, 160f),
        maximumWidthDp = if (maximumWidthDp <= 0f) 0f
        else maximumWidthDp.coerceIn(160f, 600f),
        heightDp = if (heightDp <= 0f) 0f else heightDp.coerceIn(36f, 80f),
    )

    fun isEmpty(): Boolean = leftMarginDp == 0f && maximumWidthDp == 0f && heightDp == 0f
}

internal data class NavigationBarGeometryTarget(
    val key: String,
    val label: String,
)

internal object NavigationBarGeometryTargets {
    val all = listOf(
        NavigationBarGeometryTarget("QQ", "QQ"),
        NavigationBarGeometryTarget("微信", "微信"),
        NavigationBarGeometryTarget("Xiaomi Health", "小米运动健康"),
        NavigationBarGeometryTarget("Market", "应用商店"),
        NavigationBarGeometryTarget("MiHome", "米家"),
        NavigationBarGeometryTarget("Amap", "高德地图"),
        NavigationBarGeometryTarget("Alipay classic", "支付宝"),
        NavigationBarGeometryTarget("Douyin", "抖音"),
        NavigationBarGeometryTarget("Douyin Lite", "抖音极速版"),
        NavigationBarGeometryTarget("快手", "快手"),
        NavigationBarGeometryTarget("快手极速版", "快手极速版"),
        NavigationBarGeometryTarget("QQ Music", "QQ音乐"),
        NavigationBarGeometryTarget("Xiaomi Community", "小米社区"),
        NavigationBarGeometryTarget("Bilibili", "哔哩哔哩"),
        NavigationBarGeometryTarget("PiliPlus", "PiliPlus"),
        NavigationBarGeometryTarget("Spotify", "Spotify"),
        NavigationBarGeometryTarget("拼多多", "拼多多"),
        NavigationBarGeometryTarget("淘宝", "淘宝"),
        NavigationBarGeometryTarget("京东", "京东"),
        NavigationBarGeometryTarget("闲鱼", "闲鱼"),
        NavigationBarGeometryTarget("抖音商城", "抖音商城"),
        NavigationBarGeometryTarget("菜鸟", "菜鸟"),
    )
    val defaultKey: String = all.first().key
}

internal data class NavigationBarGeometryParameters(
    val syncAll: Boolean = false,
    val selectedTarget: String = NavigationBarGeometryTargets.defaultKey,
    val global: NavigationBarGeometry = NavigationBarGeometry(),
    val targets: Map<String, NavigationBarGeometry> = emptyMap(),
) {
    fun normalized(): NavigationBarGeometryParameters = copy(
        selectedTarget = selectedTarget.takeIf { key ->
            NavigationBarGeometryTargets.all.any { it.key == key }
        } ?: NavigationBarGeometryTargets.defaultKey,
        global = global.normalized(),
        targets = targets
            .filterKeys { key -> NavigationBarGeometryTargets.all.any { it.key == key } }
            .mapValues { (_, value) -> value.normalized() }
            .filterValues { value -> !value.isEmpty() },
    )

    fun activeGeometry(): NavigationBarGeometry =
        if (syncAll) global else targets[selectedTarget] ?: NavigationBarGeometry()

    fun withActiveGeometry(value: NavigationBarGeometry): NavigationBarGeometryParameters {
        val normalizedValue = value.normalized()
        return if (syncAll) {
            copy(global = normalizedValue)
        } else {
            copy(targets = if (normalizedValue.isEmpty()) {
                targets - selectedTarget
            } else {
                targets + (selectedTarget to normalizedValue)
            })
        }
    }

    fun encode(): String {
        val value = normalized()
        return JSONObject()
            .put("version", 1)
            .put("syncAll", value.syncAll)
            .put("selectedTarget", value.selectedTarget)
            .put("global", value.global.toJson())
            .put("targets", JSONObject().apply {
                value.targets.forEach { (key, geometry) -> put(key, geometry.toJson()) }
            })
            .toString()
    }

    companion object {
        val Default = NavigationBarGeometryParameters()

        fun decode(raw: String): NavigationBarGeometryParameters {
            if (raw.isBlank() || raw.length > 32_768) return Default
            return runCatching {
                val json = JSONObject(raw)
                require(json.optInt("version", 0) == 1)
                val targetsJson = json.optJSONObject("targets") ?: JSONObject()
                val targets = buildMap {
                    targetsJson.keys().forEach { key ->
                        targetsJson.optJSONObject(key)?.let { put(key, decodeGeometry(it)) }
                    }
                }
                NavigationBarGeometryParameters(
                    syncAll = json.optBoolean("syncAll", false),
                    selectedTarget = json.optString(
                        "selectedTarget",
                        NavigationBarGeometryTargets.defaultKey,
                    ),
                    global = json.optJSONObject("global")?.let(::decodeGeometry)
                        ?: NavigationBarGeometry(),
                    targets = targets,
                ).normalized()
            }.getOrDefault(Default)
        }

        private fun decodeGeometry(json: JSONObject) = NavigationBarGeometry(
            leftMarginDp = json.finiteFloat("leftMarginDp"),
            maximumWidthDp = json.finiteFloat("maximumWidthDp"),
            heightDp = json.finiteFloat("heightDp"),
        ).normalized()

        private fun JSONObject.finiteFloat(key: String): Float {
            val value = optDouble(key, 0.0)
            return if (value.isFinite()) value.toFloat() else 0f
        }
    }
}

private fun NavigationBarGeometry.toJson(): JSONObject = JSONObject()
    .put("leftMarginDp", leftMarginDp)
    .put("maximumWidthDp", maximumWidthDp)
    .put("heightDp", heightDp)
