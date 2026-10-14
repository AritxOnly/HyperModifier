package com.aritxonly.myhypermodifier

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

internal object ModuleLauncher {
    const val CODE = "4976"
    private fun alias(context: Context) = ComponentName(context, "${context.packageName}.LauncherAlias")

    fun isHidden(context: Context): Boolean = context.packageManager.getComponentEnabledSetting(alias(context)) ==
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setHidden(context: Context, hidden: Boolean) {
        if (hidden == isHidden(context)) return
        context.packageManager.setComponentEnabledSetting(
            alias(context),
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }
}

/** Invoked by the system dialer; no phone permission or resident listener is needed. */
class ModuleSecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.telephony.action.SECRET_CODE" &&
            intent.action != "android.provider.Telephony.SECRET_CODE") return
        if (intent.data?.scheme != "android_secret_code" || intent.data?.host != ModuleLauncher.CODE) return
        context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ))
    }
}
