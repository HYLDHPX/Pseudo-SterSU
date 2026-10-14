package me.weishu.kernelsu.stealth

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.core.content.edit
import com.topjohnwu.superuser.ShellUtils
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.withNewRootShell
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

const val STEALTH_MODE_ENABLED_KEY = "stealth_mode_enabled"
const val STEALTH_MODE_CODE_KEY = "stealth_mode_code"
const val DEFAULT_STEALTH_MODE_CODE = "*#*#4211#*#*"
const val STEALTH_MODE_DISABLED_ACTION = "me.weishu.kernelsu.action.STEALTH_MODE_DISABLED"
const val STEALTH_MODE_CHANGED_ACTION = "me.weishu.kernelsu.action.STEALTH_MODE_CHANGED"
const val STEALTH_RECOVERY_URL = "http://127.0.0.1:10241/"

private const val SETTINGS_PREFS = "settings"
private const val STEALTH_CODE_PATH = "/data/adb/ksu/hansu_code"
private const val STEALTH_MODE_PATH = "/data/adb/ksu/hansu_mode"
private const val RECOVERY_MAX_BODY_BYTES = 4096

enum class StealthCodeValidation { Valid, Invalid, TooLong }

data class StealthRecoveryPreflight(
    val rootShellAvailable: Boolean = false,
    val recoveryCommandAvailable: Boolean = false,
    val dataDirectoryWritable: Boolean = false,
    val portAvailable: Boolean = false,
    val modeDisabled: Boolean = false,
) {
    val ready: Boolean get() = rootShellAvailable && recoveryCommandAvailable &&
        dataDirectoryWritable && portAvailable && modeDisabled
}

data class StealthModeRootState(
    val enabled: Boolean,
    val codeBackedUp: Boolean,
    val code: String?,
)

object StealthModeStore {
    // A stale preference alone must not hide the Manager after /data/adb is lost.
    internal fun shouldHideManager(localEnabled: Boolean, rootStateVerified: Boolean): Boolean =
        localEnabled && rootStateVerified

    fun isEnabled(context: Context = ksuApp): Boolean = preferences(context)
        .getBoolean(STEALTH_MODE_ENABLED_KEY, false)

    fun code(context: Context = ksuApp): String = normalizeCode(
        preferences(context).getString(STEALTH_MODE_CODE_KEY, DEFAULT_STEALTH_MODE_CODE).orEmpty()
    ) ?: DEFAULT_STEALTH_MODE_CODE

    fun normalizeCode(value: String): String? {
        val trimmed = value.trim()
        return trimmed.takeIf {
            it.isNotEmpty() && '\u0000' !in it && '\n' !in it && '\r' !in it
        }
    }

    fun validateCode(value: String): StealthCodeValidation {
        val normalized = normalizeCode(value) ?: return StealthCodeValidation.Invalid
        return if (recoveryRequestBodyBytes(normalized) <= RECOVERY_MAX_BODY_BYTES) {
            StealthCodeValidation.Valid
        } else {
            StealthCodeValidation.TooLong
        }
    }

    // Match the UTF-8 size of JSON.stringify({ code }) sent by the recovery page.
    internal fun recoveryRequestBodyBytes(code: String): Int {
        var size = 11 // {"code":""}
        var index = 0
        while (index < code.length && size <= RECOVERY_MAX_BODY_BYTES) {
            val char = code[index]
            size += when {
                char == '"' || char == '\\' || char == '\b' || char == '\t' || char == '\u000c' -> 2
                char.code < 0x20 -> 6
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                Character.isHighSurrogate(char) && index + 1 < code.length &&
                    Character.isLowSurrogate(code[index + 1]) -> {
                    index++
                    4
                }
                Character.isSurrogate(char) -> 6
                else -> 3
            }
            index++
        }
        return size
    }

    fun secretCodeHost(value: String): String? {
        val normalized = normalizeCode(value) ?: return null
        if (normalized.startsWith("*#*#") && normalized.endsWith("#*#*")) {
            return normalized.substring(4, normalized.length - 4)
                .takeIf(String::isNotEmpty)
                ?: normalized
        }
        return normalized
    }

    fun matchesSecretCode(host: String?, configuredCode: String): Boolean {
        return host != null && host == secretCodeHost(configuredCode)
    }

    fun matchesRequestedCode(requestedCode: String?, configuredCode: String): Boolean {
        val requested = requestedCode?.let(::secretCodeHost) ?: return false
        val configured = secretCodeHost(configuredCode) ?: return false
        return requested == configured
    }

    fun setEnabledBlocking(
        enabled: Boolean,
        requestedCode: String = code(),
        context: Context = ksuApp,
    ): Result<Unit> = runCatching {
        val normalizedCode = normalizeCode(requestedCode) ?: error("invalid secret code")
        if (enabled) check(validateCode(normalizedCode) == StealthCodeValidation.Valid) {
            "secret code is too long for the recovery page"
        }
        val recoveryCommand = shellQuote("${context.applicationInfo.nativeLibraryDir}/libksud.so") +
            " stealth-recovery"
        val temporaryCodePath = "$STEALTH_CODE_PATH.tmp.${Process.myPid()}"
        val previousCodePath = "$STEALTH_CODE_PATH.previous.${Process.myPid()}"
        val command = buildString {
            append("umask 077; mkdir -p /data/adb/ksu && ")
            append("printf '%s\\n' ")
            append(shellQuote(normalizedCode))
            append(" > ")
            append(temporaryCodePath)
            append(" && chmod 600 ")
            append(temporaryCodePath)
            append(" && mv -f ")
            append(temporaryCodePath)
            append(" ")
            append(STEALTH_CODE_PATH)
            if (enabled) {
                append(" && : > ")
                append(STEALTH_MODE_PATH)
                append(" && chmod 600 ")
                append(STEALTH_MODE_PATH)
            } else {
                append(" && rm -f ")
                append(STEALTH_MODE_PATH)
            }
        }
        val (changed, recoveryStarted) = withNewRootShell {
            check(isRoot) { "root shell unavailable" }
            if (enabled) {
                check(ShellUtils.fastCmdResult(this, "test ! -f $STEALTH_MODE_PATH")) {
                    "stealth mode is already enabled"
                }
            }
            val hadPreviousCode = enabled && ShellUtils.fastCmdResult(
                this,
                "test -e $STEALTH_CODE_PATH",
            )
            if (hadPreviousCode) {
                check(ShellUtils.fastCmdResult(this, "cp -p $STEALTH_CODE_PATH $previousCodePath")) {
                    "could not back up previous stealth code"
                }
            }
            val changed = ShellUtils.fastCmdResult(this, command)
            if (changed && enabled) {
                val started = ShellUtils.fastCmdResult(this, "$recoveryCommand start")
                if (started) {
                    if (hadPreviousCode && !ShellUtils.fastCmdResult(this, "rm -f $previousCodePath")) {
                        Log.w("StealthModeStore", "could not remove previous stealth code backup")
                    }
                } else {
                    val restoreCode = if (hadPreviousCode) {
                        "mv -f $previousCodePath $STEALTH_CODE_PATH"
                    } else {
                        "rm -f $STEALTH_CODE_PATH"
                    }
                    val modeCleared = ShellUtils.fastCmdResult(
                        this,
                        "rm -f $STEALTH_MODE_PATH $temporaryCodePath",
                    )
                    check(modeCleared) { "recovery startup failed; recovery fallback was retained" }
                    val restored = ShellUtils.fastCmdResult(this, restoreCode)
                    val stopped = ShellUtils.fastCmdResult(this, "$recoveryCommand stop")
                    check(restored && stopped) { "recovery startup failed and rollback was incomplete" }
                }
                changed to started
            } else if (enabled) {
                val restoreCode = if (hadPreviousCode) {
                    "mv -f $previousCodePath $STEALTH_CODE_PATH"
                } else {
                    "rm -f $STEALTH_CODE_PATH"
                }
                val modeCleared = ShellUtils.fastCmdResult(
                    this,
                    "rm -f $STEALTH_MODE_PATH $temporaryCodePath",
                )
                check(modeCleared) { "recovery startup failed; recovery fallback was retained" }
                val restored = ShellUtils.fastCmdResult(this, restoreCode)
                val stopped = ShellUtils.fastCmdResult(this, "$recoveryCommand stop")
                check(restored && stopped) { "recovery startup failed and rollback was incomplete" }
                changed to false
            } else {
                val stopped = !changed || ShellUtils.fastCmdResult(this, "$recoveryCommand stop")
                changed to stopped
            }
        }
        if (!enabled) {
            check(changed) { "root command failed" }
            updateLocalState(context, false, normalizedCode)
            if (!recoveryStarted) Log.w("StealthModeStore", "recovery service did not stop after disabling stealth")
            return@runCatching
        }
        check(changed && recoveryStarted) { "could not start the local stealth recovery service" }
        updateLocalState(context, enabled, normalizedCode)
    }

    fun saveCodeBlocking(
        requestedCode: String,
        context: Context = ksuApp,
    ): Result<Unit> = runCatching {
        val normalizedCode = normalizeCode(requestedCode) ?: error("invalid secret code")
        check(validateCode(normalizedCode) == StealthCodeValidation.Valid) {
            "secret code is too long for the recovery page"
        }
        check(!readRootState().getOrThrow().enabled) {
            "stealth mode must be disabled before changing its code in the Manager"
        }
        val temporaryCodePath = "$STEALTH_CODE_PATH.tmp.${Process.myPid()}"
        val command = "umask 077; [ ! -f $STEALTH_MODE_PATH ] && " +
            "mkdir -p /data/adb/ksu && " +
            "printf '%s\\n' ${shellQuote(normalizedCode)} > $temporaryCodePath && " +
            "chmod 600 $temporaryCodePath && mv -f $temporaryCodePath $STEALTH_CODE_PATH"
        val saved = withNewRootShell {
            check(isRoot) { "root shell unavailable" }
            ShellUtils.fastCmdResult(this, command)
        }
        check(saved) { "could not back up stealth code" }
        val persisted = readRootState().getOrThrow()
        check(!persisted.enabled && persisted.code == normalizedCode) {
            "stealth code backup verification failed"
        }
        updateLocalState(context, false, normalizedCode)
    }

    fun checkRecoveryChannel(context: Context = ksuApp): StealthRecoveryPreflight {
        val recoveryCommand = shellQuote("${context.applicationInfo.nativeLibraryDir}/libksud.so") +
            " stealth-recovery status >/dev/null 2>&1"
        val rootChecks = runCatching {
            withNewRootShell {
                if (!isRoot) {
                    StealthRecoveryPreflight()
                } else {
                    StealthRecoveryPreflight(
                        rootShellAvailable = true,
                        recoveryCommandAvailable = ShellUtils.fastCmdResult(this, recoveryCommand),
                        dataDirectoryWritable = ShellUtils.fastCmdResult(
                            this,
                            "test -d /data/adb && test -w /data/adb",
                        ),
                        modeDisabled = ShellUtils.fastCmdResult(this, "test ! -f $STEALTH_MODE_PATH"),
                    )
                }
            }
        }.onFailure { Log.w("StealthModeStore", "recovery preflight failed", it) }
            .getOrDefault(StealthRecoveryPreflight())
        val portAvailable = runCatching {
            ServerSocket().use { socket ->
                socket.bind(InetSocketAddress("127.0.0.1", 10241))
            }
            true
        }.getOrDefault(false)
        return rootChecks.copy(portAvailable = portAvailable)
    }

    fun readRootState(): Result<StealthModeRootState> = runCatching {
        val output = withNewRootShell {
            check(isRoot) { "root shell unavailable" }
            ShellUtils.fastCmd(
                this,
                "if [ -f $STEALTH_MODE_PATH ]; then echo enabled; else echo disabled; fi; " +
                    "if [ -f $STEALTH_CODE_PATH ]; then sed -n '1p' $STEALTH_CODE_PATH; fi",
            )
        }.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val mode = output.firstOrNull()
        check(mode == "enabled" || mode == "disabled") { "invalid root stealth state" }
        val enabled = mode == "enabled"
        val rootCode = output.getOrNull(1)?.let(::normalizeCode)
        StealthModeRootState(
            enabled = enabled,
            codeBackedUp = rootCode != null,
            code = rootCode,
        )
    }

    fun updateLocalState(
        context: Context,
        enabled: Boolean,
        normalizedCode: String = code(context),
    ) {
        preferences(context).edit(commit = true) {
            putBoolean(STEALTH_MODE_ENABLED_KEY, enabled)
            putString(STEALTH_MODE_CODE_KEY, normalizeCode(normalizedCode) ?: DEFAULT_STEALTH_MODE_CODE)
        }
    }

    fun reconcileFromRootAsync(context: Context = ksuApp) {
        val appContext = context.applicationContext
        thread(name = "stealth-mode-reconcile", isDaemon = true) {
            reconcileFromRoot(appContext)
        }
    }

    fun reconcileFromRoot(context: Context = ksuApp): Result<StealthModeRootState> {
        val appContext = context.applicationContext
        return readRootState().onSuccess { rootState ->
            updateLocalState(
                appContext,
                rootState.enabled,
                rootState.code ?: code(appContext),
            )
        }
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    internal fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
