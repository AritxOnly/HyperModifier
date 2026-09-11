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
 * Root reads its SQLite snapshot, while this process writes the stream into its own cache. This
 * avoids asking a root shell to write across the app's SELinux boundary.
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
        if (!copyRootFile(LSPOSED_DATABASE, destination)) return false
        // LSPosed enables WAL.  These sidecars are optional, but including them when present
        // makes the read reflect a configuration edit that has not yet been checkpointed.
        copyRootFile("$LSPOSED_DATABASE-wal", "$destination-wal")
        copyRootFile("$LSPOSED_DATABASE-shm", "$destination-shm")
        return true
    }

    private fun copyRootFile(source: String, destination: String): Boolean {
        val command = "[ -r '$source' ] || exit 44; cat '$source'"
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            File(destination).outputStream().use { output ->
                process.inputStream.copyTo(output)
            }
            val completed = process.waitFor(4, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val succeeded = completed && process.exitValue() == 0
            if (!succeeded) File(destination).delete()
            succeeded
        } catch (_: Exception) {
            File(destination).delete()
            false
        }
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
