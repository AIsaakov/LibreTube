package com.github.libretube.helpers

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.github.libretube.BuildConfig
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only diagnostics for investigating playback stops and memory growth without a computer
 * attached. Writes to <external files dir>/diagnostics, which can be pulled with adb, and posts a
 * notification whenever an unexpected stop is recorded. Not intended for upstream.
 */
object DebugDiagnostics {
    private const val TAG = "LTDiag"
    private const val SAMPLE_INTERVAL_MS = 2 * 60 * 1000L
    private const val HEAP_DUMP_THRESHOLD_BYTES = 400L * 1024 * 1024
    private const val MAX_STACK_LINES = 25
    private const val MAX_LOG_BYTES = 2L * 1024 * 1024
    private const val CHANNEL_ID = "diagnostics"
    private const val LAST_EXIT_FILE = "last-exit-timestamp"

    private lateinit var appContext: Context
    private var dir: File? = null
    private var handler: Handler? = null
    private var heapDumped = false
    private var notificationId = 9000

    @Volatile
    var currentVideoId: String? = null

    @Volatile
    private var network = "unknown"

    @Volatile
    private var screenOn = true

    @Volatile
    private var sessionStartMs = 0L

    /** Set once a stop of the current session was requested, so the following idle state is expected. */
    @Volatile
    private var stopRequested = false

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val shortTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

    fun start(context: Context) {
        if (!BuildConfig.DEBUG || dir != null) return
        appContext = context.applicationContext
        dir = context.getExternalFilesDir("diagnostics")?.apply { mkdirs() }

        log("process start pid=${Process.myPid()} version=${BuildConfig.VERSION_NAME}")

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log("uncaught exception on thread ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        NotificationManagerCompat.from(appContext).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Debug diagnostics")
                .build()
        )

        val thread = HandlerThread("LTDiag").apply { start() }
        handler = Handler(thread.looper).also {
            it.post(::logPreviousExits)
            it.post(::sampleMemory)
        }

        registerNetworkCallback()
        registerScreenReceiver()
    }

    fun log(message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        Log.w(TAG, message, throwable)
        val stack = throwable?.let {
            Log.getStackTraceString(it).lines().take(MAX_STACK_LINES).joinToString("\n")
        }
        append("events.log", listOfNotNull("${now()} $message", stack).joinToString("\n"))
    }

    /**
     * Records player state changes, play/pause reasons and failed loads for [player], each with the
     * session context, and notifies about playback errors.
     */
    @OptIn(UnstableApi::class)
    fun attachPlayer(player: ExoPlayer, isAudioOnly: () -> Boolean) {
        if (!BuildConfig.DEBUG) return
        sessionStartMs = System.currentTimeMillis()
        stopRequested = false

        fun context() = "mode=${if (isAudioOnly()) "audio" else "video"} " +
            "pos=${player.currentPosition / 1000}s " +
            "session=${(System.currentTimeMillis() - sessionStartMs) / 1000}s " +
            "net=$network screen=${if (screenOn) "on" else "off"} videoId=$currentVideoId"

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                log("player state ${playbackStateName(playbackState)} ${context()}")
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                log("player ${if (playWhenReady) "play" else "pause"} reason=${playWhenReadyReasonName(reason)} ${context()}")
            }

            override fun onPlayerError(error: PlaybackException) {
                val httpStatus = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                log("player error ${error.errorCodeName} (${error.errorCode}) httpStatus=$httpStatus ${context()}", error)
                notifyUnexpectedStop("Playback error: ${error.errorCodeName}" + (httpStatus?.let { " (HTTP $it)" } ?: ""))
            }
        })

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onLoadError(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
                error: IOException,
                wasCanceled: Boolean
            ) {
                // only the host is logged, since stream URLs contain signatures
                val httpStatus = (error as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                log(
                    "load error trackType=${mediaLoadData.trackType} host=${loadEventInfo.uri.host} " +
                        "httpStatus=$httpStatus canceled=$wasCanceled " +
                        "${error.javaClass.simpleName}: ${error.message?.take(200)} ${context()}"
                )
            }
        })
    }

    /** Call when a stop of the playback session is requested, e.g. by the user or the system. */
    fun onStopRequested() {
        stopRequested = true
    }

    /** Call when the player went idle and the session is torn down because of it. */
    fun onIdleTeardown(error: PlaybackException?) {
        log("idle teardown playerError=${error?.errorCodeName} stopRequested=$stopRequested")
        // errors are notified by onPlayerError already
        if (!stopRequested && error == null) notifyUnexpectedStop("Playback stopped without an error")
    }

    @SuppressLint("MissingPermission")
    private fun notifyUnexpectedStop(text: String) {
        val time = synchronized(shortTimeFormat) { shortTimeFormat.format(Date()) }
        runCatching {
            val manager = NotificationManagerCompat.from(appContext)
            if (!manager.areNotificationsEnabled()) {
                log("notification not shown, notifications are disabled: $text")
                return
            }
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("LibreTube Debug: logged at $time")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            manager.notify(notificationId++, notification)
        }.onFailure { Log.w(TAG, "failed to post notification", it) }
    }

    private fun logPreviousExits() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = appContext.getSystemService<ActivityManager>() ?: return
        val marker = File(dir, LAST_EXIT_FILE)
        val lastSeen = runCatching { marker.readText().trim().toLong() }.getOrDefault(0L)

        val exits = runCatching {
            activityManager.getHistoricalProcessExitReasons(appContext.packageName, 0, 10)
        }.getOrDefault(emptyList()).filter { it.timestamp > lastSeen }.sortedBy { it.timestamp }

        exits.forEach { exit ->
            val time = synchronized(timeFormat) { timeFormat.format(Date(exit.timestamp)) }
            log(
                "previous process exit at $time pid=${exit.pid} reason=${exitReasonName(exit.reason)} " +
                    "status=${exit.status} importance=${exit.importance} " +
                    "pssMb=${exit.pss / 1024} rssMb=${exit.rss / 1024} description=${exit.description}"
            )
            if (exit.reason in abnormalExitReasons) {
                notifyUnexpectedStop("Previous session ended: ${exitReasonName(exit.reason)} at $time")
            }
        }
        exits.lastOrNull()?.let { runCatching { marker.writeText(it.timestamp.toString()) } }
    }

    private val abnormalExitReasons = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
    )

    private fun exitReasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        // added in API 33 and 34
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "UNKNOWN($reason)"
    }

    private fun playbackStateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> state.toString()
    }

    private fun playWhenReadyReasonName(reason: Int) = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
        Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "SUPPRESSED_TOO_LONG"
        else -> reason.toString()
    }

    private fun registerNetworkCallback() {
        val connectivityManager = appContext.getSystemService<ConnectivityManager>() ?: return
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(net: Network, capabilities: NetworkCapabilities) {
                    val type = when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                        else -> "other"
                    }
                    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    setNetwork(if (validated) type else "$type-unvalidated")
                }

                override fun onLost(net: Network) = setNetwork("none")
            }, handler!!)
        }.onFailure { log("failed to register network callback", it) }
    }

    private fun setNetwork(value: String) {
        if (value == network) return
        log("network $network -> $value")
        network = value
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            appContext,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    screenOn = intent.action == Intent.ACTION_SCREEN_ON
                    log("screen ${if (screenOn) "on" else "off"}")
                }
            },
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
        runCatching {
            val file = File(dir, fileName)
            // keep one older copy, so that days of logging can't fill the storage
            if (file.length() > MAX_LOG_BYTES) file.renameTo(File(dir, "$fileName.1"))
            file.appendText(line + "\n")
        }
    }

    private fun now() = synchronized(timeFormat) { timeFormat.format(Date()) }
}
