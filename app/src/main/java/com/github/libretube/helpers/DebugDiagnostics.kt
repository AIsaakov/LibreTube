package com.github.libretube.helpers

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import com.github.libretube.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only diagnostics for investigating playback stops and memory growth without a computer
 * attached. Writes to <external files dir>/diagnostics, which can be pulled with adb.
 * Not intended for upstream.
 */
object DebugDiagnostics {
    private const val TAG = "LTDiag"
    private const val SAMPLE_INTERVAL_MS = 2 * 60 * 1000L
    private const val HEAP_DUMP_THRESHOLD_BYTES = 400L * 1024 * 1024
    private const val MAX_STACK_LINES = 25

    private var dir: File? = null
    private var handler: Handler? = null
    private var heapDumped = false

    @Volatile
    var currentVideoId: String? = null

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun start(context: Context) {
        if (!BuildConfig.DEBUG || dir != null) return
        dir = context.getExternalFilesDir("diagnostics")?.apply { mkdirs() }

        log("process start pid=${Process.myPid()} version=${BuildConfig.VERSION_NAME}")

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log("uncaught exception on thread ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        val thread = HandlerThread("LTDiag").apply { start() }
        handler = Handler(thread.looper).also { it.post(::sampleMemory) }
    }

    fun log(message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        Log.w(TAG, message, throwable)
        val stack = throwable?.let {
            Log.getStackTraceString(it).lines().take(MAX_STACK_LINES).joinToString("\n")
        }
        append("events.log", listOfNotNull("${now()} $message", stack).joinToString("\n"))
    }

    private fun sampleMemory() {
        val runtime = Runtime.getRuntime()
        val javaUsed = runtime.totalMemory() - runtime.freeMemory()
        val info = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        fun statMb(name: String) = (info.getMemoryStat(name)?.toLongOrNull() ?: 0L) / 1024

        append(
            "memory.log",
            "${now()} pid=${Process.myPid()} javaUsedMb=${javaUsed / 1024 / 1024} " +
                "javaMaxMb=${runtime.maxMemory() / 1024 / 1024} " +
                "javaHeapPssMb=${statMb("summary.java-heap")} " +
                "nativeHeapPssMb=${statMb("summary.native-heap")} " +
                "graphicsPssMb=${statMb("summary.graphics")} " +
                "totalPssMb=${statMb("summary.total-pss")} " +
                "videoId=$currentVideoId"
        )

        val threshold = minOf(HEAP_DUMP_THRESHOLD_BYTES, runtime.maxMemory() * 3 / 4)
        if (!heapDumped && javaUsed > threshold) {
            heapDumped = true
            val file = File(dir, "heap-${System.currentTimeMillis()}.hprof")
            log("java heap at ${javaUsed / 1024 / 1024} MB, writing heap dump to ${file.name}")
            runCatching { Debug.dumpHprofData(file.absolutePath) }
                .onFailure { log("heap dump failed", it) }
        }

        handler?.postDelayed(::sampleMemory, SAMPLE_INTERVAL_MS)
    }

    @Synchronized
    private fun append(fileName: String, line: String) {
        val dir = dir ?: return
        runCatching { File(dir, fileName).appendText(line + "\n") }
    }

    private fun now() = synchronized(timeFormat) { timeFormat.format(Date()) }
}
