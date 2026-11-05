package com.aritxonly.myhypermodifier

import kotlin.math.abs

/** Geometry-only navigation policy, kept independent from Android views for JVM regression tests. */
internal data class EcommerceTabCandidate(
    val label: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val clickable: Boolean,
    val selected: Boolean,
)

internal data class ResolvedEcommerceBar(
    val tabs: List<EcommerceTabCandidate>,
    val selectedIndex: Int,
)

internal object EcommerceNavigationPolicy {
    fun labelsMatch(label: String, expected: Collection<String>): Boolean {
        val candidate = normalize(label)
        if (candidate.isBlank()) return false
        return expected.any { wanted ->
            val target = normalize(wanted)
            candidate == target ||
                candidate.startsWith("$target ") ||
                candidate.startsWith("$target,") ||
                candidate.startsWith("$target，")
        }
    }

    fun resolve(
        candidates: List<EcommerceTabCandidate>,
        screenHeightPx: Int,
        density: Float,
        minimumTabs: Int,
    ): ResolvedEcommerceBar? {
        if (screenHeightPx <= 0 || candidates.size < minimumTabs) return null
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val usable = candidates.filter { tab ->
            tab.clickable &&
                tab.right - tab.left >= 12f * safeDensity &&
                tab.bottom - tab.top >= 12f * safeDensity &&
                tab.centerY >= screenHeightPx * MINIMUM_BOTTOM_ROW_FRACTION
        }
        if (usable.size < minimumTabs) return null

        val lowest = usable.maxOf { it.centerY }
        val sameRow = usable.filter {
            abs(it.centerY - lowest) <= ROW_TOLERANCE_DP * safeDensity
        }
        val distinct = sameRow
            .groupBy { normalize(it.label) }
            .values
            .map { group -> group.maxBy { it.centerY } }
            .sortedBy { it.centerX }
        if (distinct.size < minimumTabs) return null

        val selected = distinct.indexOfFirst { it.selected }
            .takeIf { it >= 0 }
            ?: 0
        return ResolvedEcommerceBar(distinct, selected)
    }

    /**
     * Fallback for host versions that draw their tab labels without exposing text, or replace
     * labels with icons/canvas content. It only accepts a wide, bottom-anchored row of distinct
     * clickable items, so ordinary product cards and a single floating action are not enough.
     */
    fun resolveGeometry(
        candidates: List<EcommerceTabCandidate>,
        screenWidthPx: Int,
        screenHeightPx: Int,
        density: Float,
        minimumTabs: Int,
    ): ResolvedEcommerceBar? {
        if (screenWidthPx <= 0 || screenHeightPx <= 0 || candidates.size < minimumTabs) return null
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val minimumSize = 20f * safeDensity
        val usable = candidates.filter { tab ->
            val width = tab.right - tab.left
            val height = tab.bottom - tab.top
            tab.clickable &&
                width >= minimumSize &&
                height >= minimumSize &&
                width <= screenWidthPx * MAX_GEOMETRIC_ITEM_WIDTH_FRACTION &&
                height <= screenHeightPx * MAX_GEOMETRIC_ITEM_HEIGHT_FRACTION &&
                tab.centerY >= screenHeightPx * MINIMUM_GEOMETRIC_ROW_FRACTION
        }
        if (usable.size < minimumTabs) return null

        val rows = ArrayList<MutableList<EcommerceTabCandidate>>()
        val tolerance = ROW_TOLERANCE_DP * safeDensity
        usable.sortedBy { it.centerY }.forEach { tab ->
            val row = rows.firstOrNull {
                abs(it.map { candidate -> candidate.centerY }.average() - tab.centerY) <= tolerance
            }
            if (row == null) rows += mutableListOf(tab) else row += tab
        }

        val bestRow = rows
            .asSequence()
            .map(::dedupeGeometricTabs)
            .filter { it.size >= minimumTabs }
            .filter { row ->
                val span = row.last().centerX - row.first().centerX
                span >= screenWidthPx * MINIMUM_GEOMETRIC_ROW_SPAN_FRACTION
            }
            .maxByOrNull { row -> row.map { it.centerY }.average() }
            ?: return null
        val selected = bestRow.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0
        return ResolvedEcommerceBar(bestRow, selected)
    }

    fun normalize(value: String): String = value
        .replace("\u200B", "")
        .replace('\n', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun dedupeGeometricTabs(row: List<EcommerceTabCandidate>): List<EcommerceTabCandidate> {
        val distinct = ArrayList<EcommerceTabCandidate>()
        row.sortedWith(compareBy<EcommerceTabCandidate> { it.centerX }.thenByDescending { it.selected })
            .forEach { candidate ->
                val duplicate = distinct.any { existing ->
                    abs(existing.centerX - candidate.centerX) <=
                        maxOf(8, minOf(existing.width, candidate.width) / 2)
                }
                if (!duplicate) distinct += candidate
            }
        return distinct
    }

    private val EcommerceTabCandidate.centerX: Int get() = (left + right) / 2
    private val EcommerceTabCandidate.centerY: Int get() = (top + bottom) / 2
    private val EcommerceTabCandidate.width: Int get() = right - left

    private const val MINIMUM_BOTTOM_ROW_FRACTION = 0.58f
    private const val MINIMUM_GEOMETRIC_ROW_FRACTION = 0.70f
    private const val MINIMUM_GEOMETRIC_ROW_SPAN_FRACTION = 0.42f
    private const val MAX_GEOMETRIC_ITEM_WIDTH_FRACTION = 0.55f
    private const val MAX_GEOMETRIC_ITEM_HEIGHT_FRACTION = 0.22f
    private const val ROW_TOLERANCE_DP = 28f
}
