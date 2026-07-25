package com.HondaDSP

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val LOG_TAG = "Honda DSP"
private const val LOG_DIRECTORY = "logs"
private const val LOG_FILE_NAME = "hondadsp.log"
private const val USB_LOG_ROOT = "/storage/USB3"
private val LOG_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
@Volatile
private var logContext: Context? = null

fun log(context: Context?, msg: String) {
    if (context != null) {
        logContext = context.applicationContext
    }
    log(msg)
}

fun initializeLogging(context: Context) {
    logContext = context.applicationContext
}

fun log(msg: String) {
    Log.d(LOG_TAG, msg)
    appendLogToFile(logContext, msg)
}

private fun appendLogToFile(context: Context?, msg: String) {
    if (context == null) return

    runCatching {
        val logFile = resolveLogFile(context)
        val line = "${LocalDateTime.now().format(LOG_TIMESTAMP_FORMATTER)} $msg\n"
        logFile.appendText(line)
    }.onFailure {
        Log.e(LOG_TAG, "Failed to append to log file", it)
    }
}

private fun resolveLogFile(context: Context): File {
    val candidates = listOf(
        File(USB_LOG_ROOT, "$LOG_DIRECTORY/$LOG_FILE_NAME"),
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "$LOG_DIRECTORY/$LOG_FILE_NAME"
        )
    )

    for (candidate in candidates) {
        val parent = candidate.parentFile ?: continue
        if ((parent.exists() || parent.mkdirs()) && parent.canWrite()) {
            return candidate
        }
    }

    return candidates.last()
}
