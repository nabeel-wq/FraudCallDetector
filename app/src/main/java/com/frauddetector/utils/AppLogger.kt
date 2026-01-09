package com.frauddetector.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

/**
 * Singleton logger that writes to both Logcat and a local file.
 * Useful for debugging when Logcat is unavailable or cleared.
 */
object AppLogger {
    private const val LOG_FILE_NAME = "app_logs.txt"
    private const val MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB
    
    private var logFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    fun init(context: Context) {
        try {
            logFile = File(context.filesDir, LOG_FILE_NAME)
            
            // Rotate log if too large
            if (logFile?.exists() == true && logFile!!.length() > MAX_LOG_SIZE_BYTES) {
                logFile!!.delete()
            }
            
            log("AppLogger", "Logger initialized. Log file: ${logFile?.absolutePath}", Log.INFO)
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to initialize logger", e)
        }
    }
    
    fun d(tag: String, message: String) {
        log(tag, message, Log.DEBUG)
    }
    
    fun i(tag: String, message: String) {
        log(tag, message, Log.INFO)
    }
    
    fun w(tag: String, message: String) {
        log(tag, message, Log.WARN)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        log(tag, msg, Log.ERROR)
    }
    
    private fun log(tag: String, message: String, level: Int) {
        // 1. Write to Logcat
        when (level) {
            Log.DEBUG -> Log.d(tag, message)
            Log.INFO -> Log.i(tag, message)
            Log.WARN -> Log.w(tag, message)
            Log.ERROR -> Log.e(tag, message)
            else -> Log.v(tag, message)
        }
        
        // 2. Write to file (asynchronously)
        scope.launch {
            try {
                logFile?.let { file ->
                    val timestamp = dateFormat.format(Date())
                    val levelStr = when (level) {
                        Log.DEBUG -> "D"
                        Log.INFO -> "I"
                        Log.WARN -> "W"
                        Log.ERROR -> "E"
                        else -> "V"
                    }
                    
                    FileWriter(file, true).use { writer ->
                        writer.append("$timestamp $levelStr/$tag: $message\n")
                    }
                }
            } catch (e: Exception) {
                // Fail silently to avoid infinite recursion
            }
        }
    }
    
    fun getLogFilePath(): String? = logFile?.absolutePath
    
    fun clearLogs() {
        scope.launch {
            try {
                logFile?.delete()
                logFile?.createNewFile()
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to clear logs", e)
            }
        }
    }
}
