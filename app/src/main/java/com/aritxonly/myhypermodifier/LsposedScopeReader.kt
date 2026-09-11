package com.aritxonly.myhypermodifier

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import java.io.File
import java.util.concurrent.TimeUnit

/** Snapshot of the scope toggles saved by LSPosed for this module and Android user. */
sealed interface LsposedScopeConfiguration {
    data object Loading : LsposedScopeConfiguration
    data class Available(val enabledPackages: Set<String>) : LsposedScopeConfiguration
    data object Unavailable : LsposedScopeConfiguration
}

/**
 * LSPosed keeps module enablement and target packages in its root-owned configuration database.
 * Copying its read-only SQLite snapshot into this app's cache lets the companion app report the
 * same state shown in LSPosed Manager, without treating a target process restart as activation.
 */
object LsposedScopeReader {
    private const val MODULE_PACKAGE = "com.aritxonly.myhypermodifier"
    private const val LSPOSED_DATABASE = "/data/adb/lspd/config/modules_config.db"
    private const val ANDROID_USER_RANGE = 100_000

    fun read(context: Context): LsposedScopeConfiguration {
        val databaseCopy = File.createTempFile("hypermodifier-lspd-", ".db", context.cacheDir)
        val copyPath = databaseCopy.absolutePath
        return try {
            if (!databaseCopy.delete() || !copyDatabaseAsRoot(copyPath)) {
                LsposedScopeConfiguration.Unavailable
            } else {
                readEnabledPackages(databaseCopy, Process.myUid() / ANDROID_USER_RANGE)
            }
        } catch (_: Exception) {
            LsposedScopeConfiguration.Unavailable
        } finally {
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                File(copyPath + suffix).delete()
            }
        }
    }

    private fun copyDatabaseAsRoot(destination: String): Boolean {
        val uid = Process.myUid()
        val command = """
            source='$LSPOSED_DATABASE'
            target='$destination'
            [ -r "${'$'}source" ] || exit 2
            cp "${'$'}source" "${'$'}target" || exit 3
            [ -f "${'$'}source-wal" ] && cp "${'$'}source-wal" "${'$'}target-wal"
            [ -f "${'$'}source-shm" ] && cp "${'$'}source-shm" "${'$'}target-shm"
            chown $uid:$uid "${'$'}target" "${'$'}target-wal" "${'$'}target-shm" 2>/dev/null || chown $uid:$uid "${'$'}target"
            chmod 600 "${'$'}target" "${'$'}target-wal" "${'$'}target-shm" 2>/dev/null || chmod 600 "${'$'}target"
        """.trimIndent()
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val completed = process.waitFor(4, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        return completed && process.exitValue() == 0
    }

    private fun readEnabledPackages(databaseFile: File, userId: Int): LsposedScopeConfiguration {
        val database = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            val enabledPackages = buildSet {
                database.rawQuery(
                    """
                    SELECT scope.app_pkg_name
                    FROM scope INNER JOIN modules ON scope.mid = modules.mid
                    WHERE modules.module_pkg_name = ?
                      AND modules.enabled = 1
                      AND scope.user_id = ?
                    """.trimIndent(),
                    arrayOf(MODULE_PACKAGE, userId.toString()),
                ).use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            LsposedScopeConfiguration.Available(enabledPackages)
        } catch (_: Exception) {
            LsposedScopeConfiguration.Unavailable
        } finally {
            database.close()
        }
    }
}
