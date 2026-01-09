package com.frauddetector

import android.app.Application
import android.util.Log

/**
 * Application class for FraudCallDetector.
 * Handles app-wide initialization.
 */
class FraudDetectorApplication : Application() {
    
    companion object {
        private const val TAG = "FraudDetectorApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        com.frauddetector.utils.AppLogger.init(this)
        com.frauddetector.utils.AppLogger.d(TAG, "Application created")
        
        // TODO: Initialize any app-wide resources here
        // e.g., crash reporting, analytics, etc.
    }
}
