package com.ks.beam.update

import com.ks.beam.ui.SettingsStore
import com.ks.utility.appupdate.AppUpdate
import com.ks.utility.appupdate.GitHubRepo
import com.ks.utility.appupdate.ReleaseAsset
import com.ks.utility.appupdate.Sha256
import com.ks.utility.appupdate.UpdateOutcome
import com.ks.utility.appupdate.UpdatePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** The platform's part: where downloads go and how the verified file reaches the installer. */
interface UpdateInstaller {
    /** A folder of KS Beam's own, emptied before each download. */
    fun downloadDir(): File

    /** True when the system lets KS Beam install the file (Android's "install unknown apps"). */
    fun canInstall(): Boolean = true

    /** Hands the verified file to the system; returns where, for the sentence shown. */
    fun open(file: File): OpenedOn

    fun openPermissionSettings() {}
}

/**
 * Checks KS Beam's GitHub releases and installs a newer version (ks-utility's
 * `specs/app-update.md`): one unauthenticated GET of the release list (platform tags), at most
 * once a day by itself (10 s after start, then hourly if due), never by itself for a build from
 * source; the download's SHA-256 must match the one GitHub lists, or nothing is opened. Each
 * platform asks for its own file ([platform]): the phone app never installs the TV build.
 */
class GitHubUpdater(
    override val currentVersion: String,
    private val platform: UpdatePlatform,
    private val settings: SettingsStore,
    private val installer: UpdateInstaller,
    repo: String = REPO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val fetch: (String) -> String = ::get,
) : UpdateService {
    private val repo = requireNotNull(GitHubRepo.parse(repo)) { "Not owner/name: $repo" }
    override val isDevBuild = AppUpdate.parseVersion(currentVersion) == null || AppUpdate.isDevVersion(currentVersion)

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    override val status: StateFlow<UpdateStatus> = _status
    private val _auto = MutableStateFlow(settings.getString(AUTO_KEY) != "false")
    override val autoCheck: StateFlow<Boolean> = _auto
    private val _later = MutableStateFlow(settings.getString(LATER_KEY))
    override val laterVersion: StateFlow<String?> = _later

    private var found: Pair<String, ReleaseAsset>? = null
    private var job: Job? = null

    /** Starts the automatic schedule. */
    fun start() {
        scope.launch {
            delay(AppUpdate.STARTUP_DELAY_MS)
            while (isActive) {
                checkIfDue()
                delay(AppUpdate.RECHECK_EVERY_MS)
            }
        }
    }

    /** Ends the schedule and any download (the activity or window is gone). */
    fun close() = scope.cancel()

    private fun checkIfDue() {
        val last = settings.getString(LAST_CHECK_KEY)?.toLongOrNull()
        if (job?.isActive != true && AppUpdate.isCheckDue(_auto.value, currentVersion, last, System.currentTimeMillis())) check(silent = true)
    }

    override fun setAutoCheck(enabled: Boolean) {
        settings.putString(AUTO_KEY, enabled.toString())
        _auto.value = enabled
    }

    override fun later() {
        val version = (status.value as? UpdateStatus.Available)?.version ?: return
        settings.putString(LATER_KEY, version)
        _later.value = version
    }

    override fun checkNow() {
        if (job?.isActive == true) return
        job = scope.launch { check(silent = false) }
    }

    /** One check; [silent] for the automatic one, which shows only an update found. */
    internal fun check(silent: Boolean) {
        if (!silent) _status.value = UpdateStatus.Checking
        val body = try {
            fetch(repo.releasesApiUrl)
        } catch (e: IOException) {
            if (!silent) _status.value = UpdateStatus.Failed(UpdateFailure.Check, e.message ?: e.javaClass.simpleName)
            return
        }
        settings.putString(LAST_CHECK_KEY, System.currentTimeMillis().toString())
        when (val outcome = AppUpdate.evaluateReleases(body, repo, currentVersion, platform)) {
            null -> if (!silent) _status.value = UpdateStatus.Failed(UpdateFailure.Unreadable)
            is UpdateOutcome.UpToDate -> if (!silent) _status.value = UpdateStatus.UpToDate(outcome.version)
            is UpdateOutcome.NoInstaller -> if (!silent) _status.value = UpdateStatus.NoInstaller(outcome.version)
            is UpdateOutcome.Available -> {
                found = outcome.version to outcome.asset
                _status.value = UpdateStatus.Available(outcome.version, outcome.asset.size)
            }
        }
    }

    override fun install() {
        val (version, asset) = found ?: return
        if (job?.isActive == true) return
        if (!installer.canInstall()) {
            _status.value = UpdateStatus.NeedsPermission(asset.size)
            return
        }
        job = scope.launch {
            _status.value = UpdateStatus.Downloading(version)
            val dir = installer.downloadDir().apply { deleteRecursively(); mkdirs() }
            val file = File(dir, asset.name)
            try {
                download(asset.url, file)
            } catch (e: IOException) {
                file.delete()
                _status.value = UpdateStatus.Failed(UpdateFailure.Download, e.message ?: e.javaClass.simpleName)
                return@launch
            }
            if (!Sha256.matches(Sha256.hex(file), asset.sha256)) {
                file.delete()
                _status.value = UpdateStatus.Failed(UpdateFailure.Damaged)
                return@launch
            }
            _status.value = try {
                UpdateStatus.Opened(installer.open(file))
            } catch (e: Exception) {
                UpdateStatus.Failed(UpdateFailure.Open, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override fun openInstallPermissionSettings() = installer.openPermissionSettings()

    companion object {
        const val REPO = "kumasumit/ks-beam"
        private const val AUTO_KEY = "update.auto"
        private const val LAST_CHECK_KEY = "update.lastCheck"
        private const val LATER_KEY = "update.later"

        /** The release list, with ks-utility's headers and its 1 MiB limit. */
        private fun get(url: String): String {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                GitHubRepo.parse(REPO)!!.requestHeaders.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                val code = connection.responseCode
                if (code != 200) throw IOException("HTTP $code")
                return connection.inputStream.use { input -> readAtMost(input, AppUpdate.MAX_RESPONSE_BYTES).decodeToString() }
            } finally {
                connection.disconnect()
            }
        }

        private fun download(url: String, to: File) {
            var current = URI(url).toURL()
            // GitHub redirects release downloads to its file host; follow a few redirects over HTTPS only.
            repeat(5) {
                val c = current.openConnection() as HttpURLConnection
                try {
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 15_000
                    c.readTimeout = 60_000
                    c.setRequestProperty("User-Agent", "ks-beam")
                    when (val code = c.responseCode) {
                        200 -> {
                            c.inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
                            return
                        }
                        301, 302, 303, 307, 308 -> {
                            val next = URI(current.toString()).resolve(c.getHeaderField("Location") ?: throw IOException("HTTP $code"))
                            if (next.scheme != "https") throw IOException("Refused redirect to ${next.scheme}")
                            current = next.toURL()
                        }
                        else -> throw IOException("HTTP $code")
                    }
                } finally {
                    c.disconnect()
                }
            }
            throw IOException("Too many redirects")
        }
    }
}

/** Up to [max] bytes of [input] (`readNBytes` is Android 13+ only; KS Beam runs from Android 8). */
internal fun readAtMost(input: java.io.InputStream, max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < max) {
        val n = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
