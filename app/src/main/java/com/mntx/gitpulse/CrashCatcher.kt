package com.mntx.gitpulse

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last uncaught exception on disk so a crash can be reported without a debugger.
 *
 * The app has no backend and no analytics on purpose, so this is deliberately local: it writes
 * one small text file, and [lastReport] is shown from Settings ("Copy crash report") where the
 * user decides whether to share it. Survives process death, cleared after a successful read.
 */
object CrashCatcher {

    private const val FILE_NAME = "last-crash.txt"
    private const val MAX_BYTES = 64 * 1024
    private const val TAG = "GitPulse"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { printer ->
                printer.println("time: ${stamp()}")
                printer.println("thread: ${thread.name}")
                printer.println("version: ${BuildConfig.APP_VERSION_LABEL} (${BuildConfig.VERSION_CODE})")
                printer.println("android: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.MODEL})")
                error.printStackTrace(printer)
            }
        }.toString()
        File(context.filesDir, FILE_NAME).writeText(stack.take(MAX_BYTES))
        Log.e(TAG, "uncaught exception recorded", error)
    }

    /** Reads and clears the last report, so it is shown once. */
    fun lastReport(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val text = file.readText()
        file.delete()
        text.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
