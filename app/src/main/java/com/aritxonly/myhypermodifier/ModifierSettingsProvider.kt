package com.aritxonly.myhypermodifier

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/** Exposes module appearance values to each LSPosed target process after a scope restart. */
class ModifierSettingsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
        ModifierSettingsStore.METHOD_GET -> ModifierSettingsStore.toBundle(
            ModifierSettingsStore.load(requireNotNull(context)),
        )
        ModifierSettingsStore.METHOD_GET_SCOPE_STATUS -> ModifierSettingsStore
            .scopeRuntimeStatus(requireNotNull(context))
            .toBundle()
        ModifierSettingsStore.METHOD_REPORT_SCOPE_HEARTBEAT -> {
            ModifierSettingsStore.reportScopeHeartbeat(
                requireNotNull(context),
                extras?.getString(ModifierSettingsStore.EXTRA_SCOPE),
            )
            Bundle()
        }
        else -> super.call(method, arg, extras)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
