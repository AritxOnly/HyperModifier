package com.aritxonly.myhypermodifier

import kotlin.math.abs

internal data class PiliPlusNavigationCandidate(
    val nodeId: Int,
    val label: String,
    val selected: Boolean,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

internal data class PiliPlusNavigationBar(
    val tabs: List<PiliPlusNavigationCandidate>,
    val selectedLabel: String,
)

internal object PiliPlusNavigationPolicy {
    private val roleOrder = listOf(HOME_LABEL, DYNAMICS_LABEL, MINE_LABEL)

    fun roleForLabel(raw: String): String? {
        val label = normalize(raw)
        return roleOrder.firstOrNull { role ->
            label == role ||
                label.startsWith("$role ", ignoreCase = false) ||
                label.startsWith("$role,") ||
                label.startsWith("$role，") ||
                label.startsWith("$role:") ||
                label.startsWith("$role：")
        }
    }

    fun resolve(
        candidates: List<PiliPlusNavigationCandidate>,
        screenHeightPx: Int,
        density: Float,
        previousSelectedLabel: String? = null,
    ): PiliPlusNavigationBar? {
        if (screenHeightPx <= 0 || candidates.size < 2) return null
        val valid = candidates.asSequence()
            .mapNotNull { candidate ->
                val role = roleForLabel(candidate.label) ?: return@mapNotNull null
                if (candidate.right <= candidate.left || candidate.bottom <= candidate.top) {
                    return@mapNotNull null
                }
                candidate.copy(label = role)
            }
            .groupBy { it.label }
            .mapNotNull { (_, sameRole) -> sameRole.maxByOrNull { it.bottom } }
            .sortedBy { it.left }
        if (valid.size < 2) return null

        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val rowTolerance = 36f * safeDensity
        val lowestCenter = valid.maxOf { it.centerY }
        val highestCenter = valid.minOf { it.centerY }
        if (abs(lowestCenter - highestCenter) > rowTolerance) return null
        if (lowestCenter < screenHeightPx * MINIMUM_BOTTOM_ROW_FRACTION ||
            screenHeightPx - lowestCenter > 180f * safeDensity
        ) return null

        val selected = valid.firstOrNull { it.selected }?.label
            ?: previousSelectedLabel?.takeIf { previous -> valid.any { it.label == previous } }
            ?: valid.first().label
        return PiliPlusNavigationBar(valid, selected)
    }

    private fun normalize(value: String): String = value
        .replace("\u200B", "")
        .replace('\n', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")

    private const val MINIMUM_BOTTOM_ROW_FRACTION = 0.58f
    private const val HOME_LABEL = "首页"
    private const val DYNAMICS_LABEL = "动态"
    private const val MINE_LABEL = "我的"
}
