package com.aritxonly.myhypermodifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Receives a bounded, authenticated failure summary from a scoped process. */
class HookDiagnosticReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPORT) return
        try {
            val expected = ModifierSettingsStore.diagnosticToken(context)
            val supplied = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
            if (expected.isEmpty() || !MessageDigest.isEqual(
                    expected.toByteArray(Charsets.UTF_8), supplied.toByteArray(Charsets.UTF_8)
                )) return
            HookDiagnosticStore.add(
                context,
                intent.getStringExtra(EXTRA_PACKAGE).orEmpty(),
                intent.getStringExtra(EXTRA_FEATURE).orEmpty(),
                intent.getStringExtra(EXTRA_DETAIL).orEmpty(),
                intent.getBooleanExtra(EXTRA_WARNING, true),
            )
        } catch (error: Throwable) {
            Log.w("MyHyperModifier", "Could not receive Hook diagnostic", error)
        }
    }

    companion object {
        const val ACTION_REPORT = "com.aritxonly.myhypermodifier.HOOK_DIAGNOSTIC"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_FEATURE = "feature"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_WARNING = "warning"
    }
}

internal data class HookDiagnostic(
    val time: Long,
    val packageName: String,
    val feature: String,
    val detail: String,
    val warning: Boolean,
)

internal object HookDiagnosticStore {
    private const val TAG = "MyHyperModifier"
    const val PREFS = "hook_diagnostics"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 120
    private const val KEY_AUTO_CLEAN = "auto_clean"

    fun autoClean(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_CLEAN, true)

    fun setAutoClean(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_CLEAN, enabled).apply()
        if (enabled) prune(context)
    }

    @Synchronized
    fun prune(context: Context): Boolean {
        if (!autoClean(context)) return true
        return runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val source = prefs.getString(KEY_EVENTS, "[]").orEmpty()
            val items = JSONArray(source)
            val retained = JSONArray()
            val now = System.currentTimeMillis()
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                if (!HookDiagnosticPolicy.expired(item.optLong("time"), now)) retained.put(item)
            }
            if (retained.length() == items.length()) true
            else prefs.edit().putString(KEY_EVENTS, retained.toString()).commit()
        }.getOrElse {
            Log.w(TAG, "Could not clean Hook diagnostics", it)
            false
        }
    }

    fun events(context: Context): List<HookDiagnostic> = runCatching {
        val source = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, "[]").orEmpty()
        val array = JSONArray(source)
        val now = System.currentTimeMillis()
        val clean = autoClean(context)
        (array.length() - 1 downTo 0).map { index ->
            val item = array.getJSONObject(index)
            HookDiagnostic(
                item.optLong("time"), item.optString("package"),
                item.optString("feature"), item.optString("detail"),
                item.optBoolean("warning", true),
            )
        }.filter { !clean || !HookDiagnosticPolicy.expired(it.time, now) }
    }.getOrElse { error ->
        Log.w(TAG, "Could not read Hook diagnostics", error)
        emptyList()
    }

    @Synchronized
    fun add(context: Context, packageName: String, feature: String, detail: String, warning: Boolean) {
        if (packageName.isBlank() || feature.isBlank()) return
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existing = JSONArray(prefs.getString(KEY_EVENTS, "[]"))
            val now = System.currentTimeMillis()
            val clean = autoClean(context)
            val items = mutableListOf<JSONObject>()
            for (index in 0 until existing.length()) {
                val item = existing.getJSONObject(index)
                if (clean && HookDiagnosticPolicy.expired(item.optLong("time"), now)) continue
                if (!warning && !item.optBoolean("warning", true) &&
                    item.optString("package") == packageName && item.optString("feature") == feature) continue
                items.add(item)
            }
            // Evict chronologically so a recovered feature cannot revert to an older failure.
            while (items.size >= MAX_EVENTS) {
                items.removeAt(0)
            }
            items.add(JSONObject().put("time", System.currentTimeMillis())
                .put("package", packageName.take(100))
                .put("feature", feature.take(100))
                .put("detail", detail.take(240))
                .put("warning", warning))
            val next = JSONArray()
            items.forEach { next.put(it) }
            if (!prefs.edit().putString(KEY_EVENTS, next.toString()).commit()) {
                Log.w(TAG, "Could not persist Hook diagnostic")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Could not save Hook diagnostic", error)
        }
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_EVENTS).apply()
    }
}
