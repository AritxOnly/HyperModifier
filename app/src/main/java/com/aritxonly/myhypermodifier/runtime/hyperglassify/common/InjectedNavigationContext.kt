package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.Configuration
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.ParcelFileDescriptor
import android.view.ContextThemeWrapper
import java.io.File

/**
 * Compose's accessibility provider reads its library strings through View.context.
 * Alipay's bundle Resources cannot resolve those IDs (and may assign them to other types).
 * Scope module resources/theme to our View; window and other services still belong to Activity.
 * No foreign Resources hooks, static Activity references, or per-frame package lookups.
 */
internal class InjectedNavigationContext internal constructor(
    activity: Activity,
    private val moduleResources: Resources,
) : ContextThemeWrapper(activity, android.R.style.Theme_DeviceDefault) {
    constructor(activity: Activity) : this(activity, resourcesFor(activity))

    @Suppress("DEPRECATION")
    override fun getResources(): Resources {
        val host = baseContext.resources
        if (moduleResources.configuration != host.configuration) {
            moduleResources.updateConfiguration(host.configuration, host.displayMetrics)
        }
        return moduleResources
    }

    override fun getAssets(): AssetManager = resources.assets

    companion object {
        private lateinit var apkPath: String
        // One APK provider per process, with no Activity/Context retained. Opening the APK
        // directly also works when the host cannot see the module through PackageManager.
        private val loader by lazy {
            val provider = ParcelFileDescriptor.open(File(apkPath), ParcelFileDescriptor.MODE_READ_ONLY).use {
                ResourcesProvider.loadFromApk(it)
            }
            ResourcesLoader().apply { addProvider(provider) }
        }

        @JvmStatic fun initialize(moduleApkPath: String) { apkPath = moduleApkPath }

        @Suppress("DEPRECATION")
        private fun resourcesFor(activity: Activity): Resources {
            val host = activity.resources
            return Resources(Resources.getSystem().assets, host.displayMetrics, Configuration(host.configuration))
                .apply { addLoaders(loader) }
        }
    }
}
