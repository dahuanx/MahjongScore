package com.zhouge.mahjongscore

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zhouge.mahjongscore.net.GameClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class UpdateInfo(
    val version: String = "",
    val versionCode: Int = 0,
    val size: Long = 0,
    val notes: String = "",
    val url: String = "",
)

enum class UpdatePhase { IDLE, CHECKING, LATEST, AVAILABLE, DOWNLOADING, NEED_PERMISSION, ERROR }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val info: UpdateInfo? = null,
    val progress: Int = 0,
    val message: String = "",
    /** 手动点「检查更新」触发时为 true；自动检查无更新/失败时静默，不打扰 */
    val manual: Boolean = false,
)

/**
 * 版本检查与自更新。
 *
 * - 每天首次启动自动检查一次（[dailyCheck]），有新版本弹窗提示
 * - 设置页可手动检查（[check] manual = true）
 * - 下载 APK 到应用私有外部目录，交给系统安装器安装
 *
 * 注意：Android 不允许普通应用静默安装，最后一步必须由用户在系统安装界面确认。
 */
object UpdateManager {
    private const val PREFS = "mj_update"
    private const val KEY_DAY = "last_check_day"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    /** 已下载但还等着安装的包。授权流程跳出去再回来时靠它续装，不重复下载。 */
    private var pendingApk: File? = null

    // ---------- 本机版本 ----------

    fun versionName(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    fun versionCode(ctx: Context): Int = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION") pi.versionCode
        }
    }.getOrDefault(0)

    // ---------- 检查 ----------

    /** 每天第一次打开时自动跑一次；当天已检查过就直接跳过 */
    fun dailyCheck(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        if (sp.getString(KEY_DAY, "") == today) return
        sp.edit().putString(KEY_DAY, today).apply()
        check(ctx, manual = false)
    }

    fun check(ctx: Context, manual: Boolean) {
        val busy = _state.value.phase == UpdatePhase.CHECKING ||
            _state.value.phase == UpdatePhase.DOWNLOADING
        if (busy) return
        _state.value = UpdateState(phase = UpdatePhase.CHECKING, manual = manual)
        val app = ctx.applicationContext
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchVersion() } }
            val cur = versionCode(app)
            result.onSuccess { info ->
                _state.value = when {
                    info.versionCode > cur -> UpdateState(UpdatePhase.AVAILABLE, info, 0, "", manual)
                    manual -> UpdateState(UpdatePhase.LATEST, info, 0, "", true)
                    else -> UpdateState()   // 自动检查：已是最新，静默收场
                }
            }.onFailure { e ->
                _state.value = if (manual) {
                    UpdateState(UpdatePhase.ERROR, null, 0, e.message ?: "网络异常", true)
                } else {
                    UpdateState()           // 自动检查失败不打扰，明天再试
                }
            }
        }
    }

    private fun fetchVersion(): UpdateInfo {
        val req = Request.Builder().url(GameClient.HTTP_BASE + "/app/version").build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) error("服务器还没启用更新服务，需要服务端升到 0.6.0")
            if (!resp.isSuccessful) error("服务端返回 ${resp.code}")
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) error("服务端无响应内容")
            val info = json.decodeFromString<UpdateInfo>(body)
            if (info.version.isEmpty() || info.url.isEmpty()) {
                error("服务端还没放更新包")
            }
            return info
        }
    }

    // ---------- 下载与安装 ----------

    fun download(ctx: Context, info: UpdateInfo) {
        if (_state.value.phase == UpdatePhase.DOWNLOADING) return
        val app = ctx.applicationContext
        val manual = _state.value.manual
        // 同一个包之前下过（比如授权中断过一次），直接装，不重新下载
        val cached = cachedApk(app, info)
        if (cached != null) {
            pendingApk = cached
            _state.value = UpdateState(UpdatePhase.DOWNLOADING, info, 100, "", manual)
            install(app, cached)
            return
        }
        _state.value = UpdateState(UpdatePhase.DOWNLOADING, info, 0, "", manual)
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchApk(app, info) } }
            result.onSuccess { file ->
                pendingApk = file
                install(app, file)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    phase = UpdatePhase.ERROR,
                    message = "下载失败：${e.message ?: "未知错误"}",
                )
            }
        }
    }

    private fun updateDir(ctx: Context): File {
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
        return File(base, "update").apply { mkdirs() }
    }

    private fun apkFile(ctx: Context, info: UpdateInfo) =
        File(updateDir(ctx), "mahjongscore-${info.version}.apk")

    /** 磁盘上已有同版本、大小对得上的包，就认为可直接复用 */
    private fun cachedApk(ctx: Context, info: UpdateInfo): File? {
        val f = apkFile(ctx, info)
        if (!f.isFile || f.length() <= 0L) return null
        if (info.size > 0 && f.length() != info.size) return null   // 下了一半的残包，重下
        return f
    }

    private fun fetchApk(ctx: Context, info: UpdateInfo): File {
        val dir = updateDir(ctx)
        val out = apkFile(ctx, info)
        // 只清别的版本，别把当前这个包删了
        dir.listFiles()?.forEach { if (it.name != out.name) it.delete() }
        val req = Request.Builder().url(info.url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("服务端返回 ${resp.code}")
            val body = resp.body ?: error("空响应")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            _state.value = _state.value.copy(progress = (read * 100 / total).toInt())
                        }
                    }
                }
            }
        }
        if (out.length() <= 0L) error("下载内容为空")
        return out
    }

    private fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    /** 跳系统「安装未知应用」授权页。必须由用户手动去开，代码没法代劳。 */
    fun openInstallSettings(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun install(ctx: Context, file: File) {
        pendingApk = file
        if (!canInstall(ctx)) {
            // 包已经下好了，只是没授权。切成"等授权"状态，别丢掉文件、别报错
            _state.value = _state.value.copy(phase = UpdatePhase.NEED_PERMISSION, progress = 100)
            openInstallSettings(ctx)
            return
        }
        launchInstaller(ctx, file)
    }

    /**
     * 用户从「安装未知应用」设置页回来后继续装。
     * 已授权 → 直接用磁盘上那个包，不重新下载；未授权 → 再把人送回设置页。
     */
    fun resumeInstall(ctx: Context) {
        val file = pendingApk
            ?: _state.value.info?.let { cachedApk(ctx, it) }
            ?: run {
                _state.value = _state.value.copy(
                    phase = UpdatePhase.ERROR,
                    message = "安装包不在了，请在设置页重新点一次检查更新",
                )
                return
            }
        if (!canInstall(ctx)) {
            openInstallSettings(ctx)
            _state.value = _state.value.copy(phase = UpdatePhase.NEED_PERMISSION)
            return
        }
        launchInstaller(ctx, file)
    }

    /** 回到前台时调用：如果正卡在等授权，且用户已经允许了，自动续装 */
    fun onResume(ctx: Context) {
        if (_state.value.phase != UpdatePhase.NEED_PERMISSION) return
        if (!canInstall(ctx)) return
        resumeInstall(ctx)
    }

    private fun launchInstaller(ctx: Context, file: File) {
        val uri = runCatching {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        }.getOrElse { e ->
            _state.value = _state.value.copy(
                phase = UpdatePhase.ERROR,
                message = "安装包路径异常：${e.message ?: "未知错误"}",
            )
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { ctx.startActivity(intent) }
            .onSuccess {
                pendingApk = null
                _state.value = UpdateState()
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    phase = UpdatePhase.ERROR,
                    message = "无法启动安装器：${e.message ?: "未知错误"}",
                )
            }
    }

    fun dismiss() {
        // 保留磁盘上的安装包：用户下次点「立即更新」会直接复用，不再重下一遍
        pendingApk = null
        _state.value = UpdateState()
    }

    /** 「0.4 MB」这类人类可读大小 */
    fun sizeText(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024L * 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    }
}
