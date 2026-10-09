package com.aritxonly.myhypermodifier

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.zip.ZipFile

/** Called on IO only; only modules with an accessible settings Activity are eligible. */
internal object LspModuleRepository {
    fun installed(context: Context): List<HomeModuleEntryConfig.Entry> {
        val pm = context.packageManager
        return pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            .mapNotNull { info -> runCatching {
                val app = info.applicationInfo ?: return@runCatching null
                if (!app.enabled || info.packageName == context.packageName || info.packageName == "org.lsposed.manager") return@runCatching null
                val legacy = runCatching { app.metaData?.getBoolean("xposedmodule", false) == true }.getOrDefault(false)
                val marker = legacy || (listOf(app.sourceDir) + app.splitSourceDirs.orEmpty()).any { path ->
                    runCatching { ZipFile(path).use { zip -> HomeModuleEntryConfig.MARKERS.any { zip.getEntry(it) != null } } }.getOrDefault(false)
                }
                if (!marker) return@runCatching null
                val component = listOf("de.robv.android.xposed.category.MODULE_SETTINGS", Intent.CATEGORY_INFO, Intent.CATEGORY_LAUNCHER)
                    .firstNotNullOfOrNull { category ->
                        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).setPackage(info.packageName).addCategory(category), 0)
                            .map { it.activityInfo }
                            .firstOrNull { activity ->
                                activity.exported && activity.enabled &&
                                    (activity.permission == null || pm.checkPermission(activity.permission, context.packageName) == PackageManager.PERMISSION_GRANTED)
                            }?.let { ComponentName(it.packageName, it.name) to category }
                    } ?: return@runCatching null
                HomeModuleEntryConfig.Entry(info.packageName, component.first.className, app.loadLabel(pm).toString(), "middle", component.second)
            }.getOrNull() }.sortedBy { it.title.lowercase() }
    }
}
