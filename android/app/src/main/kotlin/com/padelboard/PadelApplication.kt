package com.padelboard

import android.app.Application
import android.util.Log

/**
 * Custom Application class.
 * - Registers a global uncaught exception handler to log crashes gracefully.
 * - Prevents silent app deaths that are hard to debug.
 */
class PadelApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupGlobalExceptionHandler()
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log the crash so it appears in logcat with a clear tag
            Log.e("PadelScore_CRASH", "Uncaught exception on thread: ${thread.name}", throwable)

            // Delegate to default handler (which shows the system crash dialog and restarts)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
