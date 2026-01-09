package com.frauddetector.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import com.frauddetector.R

/**
 * Activity displayed during active calls.
 * Shows real-time transcription and scam detection status.
 */
class InCallActivity : AppCompatActivity() {
    
    private lateinit var callerInfoText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var statusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_call)
        
        callerInfoText = findViewById(R.id.caller_info)
        transcriptText = findViewById(R.id.transcript_text)
        statusText = findViewById(R.id.status_text)
        
        val callId = intent.getStringExtra("CALL_ID") ?: "Unknown"
        callerInfoText.text = "Call ID: $callId"
        statusText.text = "Analyzing call..."
        
        // TODO: Connect to RealtimeAnalysisService to show live updates
    }
}
