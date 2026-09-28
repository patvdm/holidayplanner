package com.pat.app.holidayplanner.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.pat.app.holidayplanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val TAG = "AppUpdater"
private const val FOLDER = "updates"
private const val APK_SUFFIX = ".apk"
private const val HEX_MASK = 0xff

/** A newer version, downloaded, checked, and waiting for one tap. */
data class ReadyUpdate(val versionName: String, val versionCode: Long, val file: File)

/** What one check found. */
enum class UpdateCheck {
    UP_TO_DATE,
    READY,

    /** Newer, but not downloaded because downloading was not allowed just now (mobile data). */
    WAITING,
    FAILED,
    NOT_SET_UP,
}

/**
 * Keeps both phones on the latest published version. A newer APK is downloaded, its checksum and
 * package checked, and then offered; installing hands it to Android's own installer.
 *
 * Android only lets an app update itself without asking once it is its own installer of record,
 * which it becomes after the first update it installs. Until then every update asks for one tap.
 */
class AppUpdater(
    private val context: Context,
    private val server: UpdateServer,
) {

    private val folder = File(context.filesDir, FOLDER)
    private val ready = MutableStateFlow(readyOnDisk())

    /** The update waiting to be installed, if there is one. */
    val state: StateFlow<ReadyUpdate?> = ready.asStateFlow()

    /** Debug builds are signed with a different key, so a release could never install over them. */
    private val enabled: Boolean get() = !BuildConfig.DEBUG && server.isConfigured

    suspend fun check(allowDownload: Boolean): UpdateCheck {
        if (!enabled) return UpdateCheck.NOT_SET_UP
        val release = server.latest().getOrElse {
            Log.w(TAG, "could not ask for the latest version: ${it.message}")
            return UpdateCheck.FAILED
        }
        return if (release == null || release.versionCode <= BuildConfig.VERSION_CODE) {
            forgetDownloads(keepCode = null)
            ready.value = null
            UpdateCheck.UP_TO_DATE
        } else {
            fetch(release, allowDownload)
        }
    }

    private suspend fun fetch(release: Release, allowDownload: Boolean): UpdateCheck {
        val target = File(folder, "${release.versionCode}$APK_SUFFIX")
        val have = withContext(Dispatchers.IO) { target.exists() && sha256(target) == release.sha256 }
        if (!have && !allowDownload) return UpdateCheck.WAITING
        if (!have && !download(release, target)) return UpdateCheck.FAILED
        forgetDownloads(keepCode = release.versionCode)
        ready.value = describe(target)
        return if (ready.value == null) UpdateCheck.FAILED else UpdateCheck.READY
    }

    private suspend fun download(release: Release, target: File): Boolean = withContext(Dispatchers.IO) {
        folder.mkdirs()
        val partial = File(folder, "${release.versionCode}.part")
        val ok = server.download(release, partial).isSuccess && sha256(partial) == release.sha256
        if (ok) partial.renameTo(target) else partial.delete()
        if (!ok) Log.w(TAG, "download of ${release.versionCode} failed or did not match its checksum")
        ok && target.exists()
    }

    /**
     * Hands the waiting APK to Android's installer. With [interactive] set, Android's confirmation
     * is shown if it wants one; without, a confirmation is left for the next time the app opens.
     */
    suspend fun install(interactive: Boolean) {
        val update = ready.value ?: return
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("update$APK_SUFFIX", 0, update.file.length()).use { out ->
                    update.file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val status = Intent(context, UpdateStatusReceiver::class.java)
                    .setPackage(context.packageName)
                    .putExtra(UpdateStatusReceiver.EXTRA_INTERACTIVE, interactive)
                // Mutable because the installer writes the outcome into it.
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    status,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        }
    }

    /** A download left from an earlier run still counts, so a phone offline at start can install it. */
    private fun readyOnDisk(): ReadyUpdate? {
        if (BuildConfig.DEBUG) return null
        return folder.listFiles { file -> file.name.endsWith(APK_SUFFIX) }
            .orEmpty()
            .mapNotNull(::describe)
            .filter { it.versionCode > BuildConfig.VERSION_CODE }
            .maxByOrNull { it.versionCode }
    }

    /** Reads the version from the APK itself, and refuses anything that is not this app. */
    private fun describe(file: File): ReadyUpdate? {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return null
        if (info.packageName != context.packageName) return null
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else 0L
        return ReadyUpdate(versionName = info.versionName.orEmpty(), versionCode = code, file = file)
    }

    private suspend fun forgetDownloads(keepCode: Long?) = withContext(Dispatchers.IO) {
        folder.listFiles()?.filter { it.name != "$keepCode$APK_SUFFIX" }?.forEach { it.delete() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and HEX_MASK) }
    }
}
