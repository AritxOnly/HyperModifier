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
        Definition("明度曲线采样点 0", "与其余三个采样点共同决定明度混合曲线；系统未公开各点公式。", 0f..2f),
        Definition("明度曲线采样点 1", "明度混合曲线的第二个采样值，不是独立的不透明度。", 0f..2f),
        Definition("明度曲线采样点 2", "明度混合曲线的第三个采样值，建议与其余采样点一起小幅调整。", 0f..2f),
        Definition("明度曲线采样点 3", "明度混合曲线的第四个采样值；具体响应取决于系统材质。", 0f..2f),
        Definition("明度混合强度", "控制明度采样参与材质混合的量，不改变背景模糊半径。", 0f..2f),
        Definition("材质饱和度", "对应 HyperChanger 的饱和度偏移；调高增强材质混合后的色彩，调低更灰。", 0f..3f),
        Definition("混合亮度", "对应亮度偏移；调高提亮混合层，调低变暗，不影响文字颜色。", -1f..1f),
        Definition("压暗强度", "对应压暗偏移；调整材质的暗化项，不是整页背景的压暗开关。", 0f..1f),
        Definition("压暗范围起点", "暗化生效范围的起点，应与终点配合调整。", -1f..1f),
        Definition("压暗范围终点", "暗化生效范围的终点，不是第二个压暗强度。", -1f..1f),
        Definition("内层底部系数", "玻璃内层的 bottom 控制项；系统未公开视觉响应公式。", 0f..1f),
        Definition("内层红色分量", "玻璃内层着色的红色通道；与绿色、蓝色共同决定底色。", 0f..2f),
        Definition("内层绿色分量", "玻璃内层着色的绿色通道，不改变卡片前景文字的颜色。", 0f..2f),
        Definition("内层蓝色分量", "玻璃内层着色的蓝色通道；三通道相同时形成中性色底色。", 0f..2f),
        Definition("内层不透明度", "对应不透明度偏移；调整内层颜色的 alpha 项，不是整个 View 的透明度。", 0f..2f),
        Definition("内层白色系数", "玻璃内层的白色混合项，与 RGB 底色分开控制。", 0f..2f),
        Definition("内层颜色混合强度", "控制内层着色的混合项，不改变底色 RGB 分量。", 0f..2f),
        Definition("内层颜色曲线", "内层着色的幂次曲线；改变颜色过渡的分布，不是模糊强度。", 0.1f..3f),
        Definition("固定材质控制位", "Xiaomi 固定写入 1，建议保持不变", 0f..1f),
        Definition("边缘强度", "玻璃形状的 edge 项；不改变卡片圆角或实际尺寸。", 0f..100f),
        Definition("边缘过渡曲线", "边缘效果的幂次分布，影响边缘到内部的过渡。", 0.1f..8f),
        Definition("边缘厚度", "沿用 HyperChanger 的命名，对应 shape.thickness；是光学厚度，不是布局 padding。", 0f..120f),
        Definition("反射偏移", "玻璃形状的反射偏移项，不移动卡片或内容布局。", 0f..1_000f),
        Definition("反射提亮", "调整反射部分的提亮项，与整体混合亮度分开控制。", 0f..2f),
        Definition("反射强度", "对应反射强度偏移，控制玻璃反射项的强弱。", 0f..2f),
        Definition("方向光红色分量", "方向光颜色的红色通道；系统允许负值，不能按普通 RGB 颜色范围理解。", -1f..1f),
        Definition("方向光绿色分量", "方向光颜色的绿色通道，与内层底色独立。", -1f..1f),
        Definition("方向光蓝色分量", "方向光颜色的蓝色通道，与红、绿分量共同控制方向光颜色。", -1f..1f),
        Definition("方向光强度", "沿用 HyperChanger 的命名，对应 directionalLight.bottom，不改变光的 RGB 分量。", 0f..2f),
        Definition("方向光白色系数", "方向光的白色混合项，与方向光颜色分量分开控制。", 0f..2f),
        Definition("方向光颜色混合强度", "方向光着色的混合项，不是背景饱和度。", 0f..2f),
        Definition("方向光颜色曲线", "方向光颜色的幂次分布，影响着色过渡。", 0.1f..3f),
        Definition("折射率", "对应折射偏移；调节玻璃折射项，不是背景模糊半径。", 1f..5f),
        Definition("背景饱和度", "对应背景饱和度偏移；作用于玻璃采样的背景，不影响图标或文字。", 0f..3f),
        Definition("背景亮度", "对应背景亮度偏移；作用于玻璃后的背景，与混合亮度分开控制。", -1f..1f),
        Definition("背景烧灼强度", "对应烧灼偏移，即 blurBg.burn 项；具体视觉响应取决于系统材质，不是模糊。", 0f..1f),
        Definition("保留位 0", "Xiaomi 未公开，建议保持 0", 0f..1f),
        Definition("保留位 1", "Xiaomi 未公开，建议保持 0", 0f..1f),
        Definition("保留位 2", "Xiaomi 未公开，建议保持 0", 0f..1f),
        Definition("保留位 3", "Xiaomi 未公开，建议保持 0", 0f..1f),
        Definition("按压环强度 0", "按压环遮罩的第一项强度；只有系统使用该遮罩时才可见。", 0f..1f),
        Definition("按压环强度 1", "按压环遮罩的第二项强度；不改变按钮的点击区域。", 0f..1f),
    ).also { require(it.size == COUNT) }

    val regularDefault = HeadsUpGlassDefaults.regular().also { require(it.size == COUNT) }
    val darkDefault = HeadsUpGlassDefaults.dark().also { require(it.size == COUNT) }

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
