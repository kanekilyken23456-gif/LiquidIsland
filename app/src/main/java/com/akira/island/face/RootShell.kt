package com.akira.island.face

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal root access through the device's own `su` (Magisk, KernelSU, APatch...).
 * Commands are fixed constants of this app: nothing user-controlled is ever interpolated into them.
 */
internal object RootShell {
    private const val SU_TIMEOUT_MS = 15_000L // the first call may show the root-manager grant prompt

    private fun start(command: String): Process =
        ProcessBuilder("su", "-c", command).redirectError(File("/dev/null")).start()

    /** True when `su` runs and really is uid 0. Blocking: call from a background thread. */
    fun hasRoot(): Boolean = runCatching {
        val p = start("id -u")
        try {
            p.outputStream.close()
            p.waitFor(SU_TIMEOUT_MS, TimeUnit.MILLISECONDS) &&
                p.inputStream.bufferedReader().readText().trim() == "0"
        } finally { p.destroy() }
    }.getOrDefault(false)

    /** Runs a fixed command and returns its stdout, or null on failure/timeout. Blocking. */
    fun output(command: String, timeoutMs: Long = 4_000L): String? = runCatching {
        val p = start(command)
        try {
            p.outputStream.close()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS) || p.exitValue() != 0) null
            else p.inputStream.bufferedReader().readText()
        } finally { p.destroy() }
    }.getOrNull()

    /** A long-running command. `exec` replaces the shell so that killing it also ends the command. */
    fun stream(command: String): Process = start("exec $command")
}
