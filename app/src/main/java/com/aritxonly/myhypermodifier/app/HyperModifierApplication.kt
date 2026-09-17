package com.aritxonly.myhypermodifier

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** Binds the module app to LSPosed so the UI can report the real configured scopes. */
class HyperModifierApplication : Application(), XposedServiceHelper.OnServiceListener {
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        ModuleFrameworkState.onServiceBound(
            apiVersion = service.apiVersion,
            frameworkName = service.frameworkName,
            frameworkVersion = service.frameworkVersion,
            frameworkVersionCode = service.frameworkVersionCode,
            scope = service.scope,
        )
    }

    override fun onServiceDied(service: XposedService) {
        ModuleFrameworkState.onServiceDied()
    }

    override fun onTerminate() {
        ModuleFrameworkState.onServiceDied()
        super.onTerminate()
    }
}
