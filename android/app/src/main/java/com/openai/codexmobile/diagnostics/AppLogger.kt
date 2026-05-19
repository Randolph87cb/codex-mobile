package com.openai.codexmobile.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant

interface AppLogger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable? = null)
    fun readRecentLogs(maxChars: Int = DEFAULT_READ_CHARS): String
    fun clear()
    fun installCrashHandler()

    companion object {
        const val DEFAULT_READ_CHARS: Int = 20_000
    }
}

class FileAppLogger(context: Context) : AppLogger {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val logDirectory = File(appContext.filesDir, "logs")
    private val logFile = File(logDirectory, LOG_FILE_NAME)

    override fun debug(tag: String, message: String) = write(AppLogLevel.DEBUG, tag, message)

    override fun info(tag: String, message: String) = write(AppLogLevel.INFO, tag, message)

    override fun warn(tag: String, message: String) = write(AppLogLevel.WARN, tag, message)

    override fun error(tag: String, message: String, throwable: Throwable?) {
        write(AppLogLevel.ERROR, tag, message, throwable)
    }

    override fun readRecentLogs(maxChars: Int): String = synchronized(lock) {
        if (!logFile.exists()) {
            return@synchronized "暂无日志。"
        }

        val content = logFile.readText(Charsets.UTF_8).trim()
        if (content.isBlank()) {
            return@synchronized "暂无日志。"
        }

        if (content.length <= maxChars) {
            content
        } else {
            "...\n${content.takeLast(maxChars)}"
        }
    }

    override fun clear() {
        synchronized(lock) {
            ensureLogDirectory()
            logFile.writeText("", Charsets.UTF_8)
        }
        info(LOGGER_TAG, "已清空应用日志。")
    }

    override fun installCrashHandler() {
        synchronized(lock) {
            if (crashHandlerInstalled) {
                return
            }
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                error(
                    tag = "AppCrash",
                    message = "线程 ${thread.name} 发生未捕获异常，应用即将退出。",
                    throwable = throwable,
                )
                previousHandler?.uncaughtException(thread, throwable)
            }
            crashHandlerInstalled = true
        }
    }

    private fun write(
        level: AppLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val throwableText = throwable?.stackTraceToString()?.trim()
        val line = buildString {
            append(Instant.now().toString())
            append(" ")
            append(level.label)
            append("/")
            append(tag)
            append(" ")
            append(message.trim())
            if (!throwableText.isNullOrBlank()) {
                append("\n")
                append(throwableText)
            }
        }

        synchronized(lock) {
            ensureLogDirectory()
            logFile.appendText("$line\n", Charsets.UTF_8)
            trimIfNeeded()
        }

        Log.println(level.priority, tag, buildString {
            append(message)
            if (!throwableText.isNullOrBlank()) {
                append('\n')
                append(throwableText)
            }
        })
    }

    private fun ensureLogDirectory() {
        if (!logDirectory.exists()) {
            logDirectory.mkdirs()
        }
    }

    private fun trimIfNeeded() {
        if (!logFile.exists() || logFile.length() <= MAX_FILE_BYTES) {
            return
        }

        val retained = logFile.readText(Charsets.UTF_8).takeLast(TRIMMED_CHAR_COUNT)
        logFile.writeText(retained, Charsets.UTF_8)
    }

    private enum class AppLogLevel(
        val label: String,
        val priority: Int,
    ) {
        DEBUG("DEBUG", Log.DEBUG),
        INFO("INFO", Log.INFO),
        WARN("WARN", Log.WARN),
        ERROR("ERROR", Log.ERROR),
    }

    private companion object {
        const val LOG_FILE_NAME = "codex-mobile.log"
        const val LOGGER_TAG = "AppLogger"
        const val MAX_FILE_BYTES = 512 * 1024L
        const val TRIMMED_CHAR_COUNT = 300_000

        @Volatile
        var crashHandlerInstalled: Boolean = false
    }
}
