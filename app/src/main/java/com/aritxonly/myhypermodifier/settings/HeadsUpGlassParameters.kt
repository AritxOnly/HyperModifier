package com.aritxonly.myhypermodifier

/**
 * The raw [android.view.View.setMiGlass] payloads used by this SystemUI build for heads-up
 * notifications. Xiaomi does not expose names for these slots, so they intentionally remain
 * indexed until the framework implementation is available for a semantic mapping.
 */
internal object HeadsUpGlassParameters {
    const val COUNT = 42

    data class Definition(
        val label: String,
        val summary: String,
        val valueRange: ClosedFloatingPointRange<Float>,
    )

    data class Group(val title: String, val indices: IntRange)

    val groups = listOf(
        Group("混合与明度", 0..9),
        Group("内层着色", 10..18),
        Group("边缘与反射", 19..24),
        Group("方向光与折射", 25..32),
        Group("背景与遮罩", 33..41),
    )

    val definitions = listOf(
        Definition("亮度采样 0", "blend.luminanceValues[0]", 0f..2f),
        Definition("亮度采样 1", "blend.luminanceValues[1]", 0f..2f),
        Definition("亮度采样 2", "blend.luminanceValues[2]", 0f..2f),
        Definition("亮度采样 3", "blend.luminanceValues[3]", 0f..2f),
        Definition("亮度混合量", "blend.luminanceAmount", 0f..2f),
        Definition("混合饱和度", "blend.saturation", 0f..3f),
        Definition("混合亮度", "blend.brightness", -1f..1f),
        Definition("暗化程度", "blend.darker", 0f..1f),
        Definition("暗化范围起点", "blend.darkerRange[0]", -1f..1f),
        Definition("暗化范围终点", "blend.darkerRange[1]", -1f..1f),
        Definition("内层底部值", "inner.bottom", 0f..1f),
        Definition("内层颜色 0", "inner.color[0] · 白底关键", 0f..2f),
        Definition("内层颜色 1", "inner.color[1] · 白底关键", 0f..2f),
        Definition("内层颜色 2", "inner.color[2] · 白底关键", 0f..2f),
        Definition("内层颜色 3", "inner.color[3] · 白底关键", 0f..2f),
        Definition("内层白色系数", "inner.colorWhite", 0f..2f),
        Definition("内层颜色混合", "inner.colorMix", 0f..2f),
        Definition("内层颜色曲线", "inner.colorPow", 0.1f..3f),
        Definition("固定材质控制位", "Xiaomi 固定写入 1，建议保持不变", 0f..1f),
        Definition("边缘强度", "shape.edge", 0f..100f),
        Definition("边缘曲线", "shape.edgePow", 0.1f..8f),
        Definition("材质厚度", "shape.thickness", 0f..120f),
        Definition("反射偏移", "shape.reflectOffset", 0f..1_000f),
        Definition("反射提亮", "reflect.lighten", 0f..2f),
        Definition("反射强度", "reflect.strength", 0f..2f),
        Definition("方向光分量 0", "directionalLight.color[0]", -1f..1f),
        Definition("方向光分量 1", "directionalLight.color[1]", -1f..1f),
        Definition("方向光分量 2", "directionalLight.color[2]", -1f..1f),
        Definition("方向光底部值", "directionalLight.bottom", 0f..2f),
        Definition("方向光白色系数", "directionalLight.colorWhite", 0f..2f),
        Definition("方向光颜色混合", "directionalLight.colorMix", 0f..2f),
        Definition("方向光颜色曲线", "directionalLight.colorPow", 0.1f..3f),
        Definition("折射率", "refract.ior", 1f..5f),
        Definition("背景饱和度", "blurBg.saturation", 0f..3f),
        Definition("背景亮度", "blurBg.brightness", -1f..1f),
        Definition("背景灼烧", "blurBg.burn", 0f..1f),
        Definition("保留位 0", "Xiaomi 未公开，建议保持 0", 0f..1f),
        Definition("保留位 1", "Xiaomi 未公开，建议保持 0", 0f..1f),
        Definition("保留位 2", "Xiaomi 未公开，建议保持 0", 0f..1f),
        Definition("保留位 3", "Xiaomi 未公开，建议保持 0", 0f..1f),
        Definition("按压环强度 0", "mask.pressRingStrength[0]", 0f..1f),
        Definition("按压环强度 1", "mask.pressRingStrength[1]", 0f..1f),
    ).also { require(it.size == COUNT) }

    val regularDefault = floatArrayOf(
        0.5f, 1f, 0f, 0.8f, 0.5f, 1.2f, 0f, 0.2f, 0f, 0f, 0.03f,
        1f, 1f, 1f, 1.5f, 0f, 0.6f, 0.6f, 1f, 62f, 3.8f, 80f, 600f,
        1f, 0.8f, -0.4f, 0.6f, -0.8f, 1.2f, 0.6f, 0.8f, 1.15f, 3f,
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
    )

    val darkDefault = floatArrayOf(
        0.8f, 1f, 0f, 1f, 0.2f, 2f, 0.14f, 0.1f, 0f, 0f, 0.02f,
        0.27f, 0.27f, 0.27f, 0.6f, 0f, 0.2f, 1.2f, 1f, 72f, 3.8f, 80f,
        600f, 1f, 0.8f, -0.4f, 0.6f, -0.8f, 1.5f, 1f, 0.8f, 1.15f, 3f,
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
    )

    val regularSerialized: String = serialize(regularDefault)
    val darkSerialized: String = serialize(darkDefault)

    fun parseSerializedOrDefault(serialized: String?, fallback: FloatArray): FloatArray =
        parse(serialized?.split(',')) ?: fallback.copyOf()

    fun parse(values: List<String>?): FloatArray? {
        if (values == null || values.size != COUNT) return null
        return FloatArray(COUNT) { index ->
            val value = values[index].trim().toFloatOrNull()
            if (value == null || value.isNaN() || value.isInfinite()) return null
            value
        }
    }

    fun serialize(values: FloatArray): String {
        require(values.size == COUNT) { "Expected $COUNT MiGlass parameters" }
        return values.joinToString(",") { it.toString() }
    }

    fun displayValues(serialized: String?, fallback: FloatArray): List<String> =
        parseSerializedOrDefault(serialized, fallback).map { it.toString() }
}
