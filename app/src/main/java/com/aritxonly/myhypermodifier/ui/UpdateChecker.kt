package com.aritxonly.myhypermodifier

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateCheckResult {
    data class Available(val versionName: String, val releaseUrl: String) : UpdateCheckResult
    data object Latest : UpdateCheckResult
    data object Unavailable : UpdateCheckResult
}

/** Reads the public GitHub release feed; APK download and installation remain user-controlled. */
object UpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/AritxOnly/HyperModifier/releases/latest"

    fun check(): UpdateCheckResult = try {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "HyperModifier-UpdateChecker")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return UpdateCheckResult.Unavailable
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val versionName = release.getString("tag_name").removePrefix("v")
            val releaseUrl = release.getString("html_url")
            if (compareVersions(versionName, BuildConfig.VERSION_NAME) > 0) {
                UpdateCheckResult.Available(versionName, releaseUrl)
            } else {
                UpdateCheckResult.Latest
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        UpdateCheckResult.Unavailable
    }

    /** Compares normal numeric version segments and ignores a leading `v` or build metadata. */
    private fun compareVersions(left: String, right: String): Int {
        val leftParts = numericSegments(left)
        val rightParts = numericSegments(right)
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val difference = (leftParts.getOrElse(index) { 0 }).compareTo(
                rightParts.getOrElse(index) { 0 },
            )
            if (difference != 0) return difference
        }
        return 0
    }

    private fun numericSegments(value: String): List<Int> = value
        .removePrefix("v")
        .substringBefore('+')
        .substringBefore('-')
        .split('.')
        .mapNotNull(String::toIntOrNull)
}
