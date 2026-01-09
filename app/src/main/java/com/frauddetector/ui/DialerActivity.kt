package com.frauddetector.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.frauddetector.R

/**
 * Dialer activity for making outgoing calls.
 * Required for default dialer functionality.
 */
class DialerActivity : androidx.appcompat.app.AppCompatActivity() {
    
    private lateinit var phoneNumberInput: EditText
    private lateinit var callButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)
        
        phoneNumberInput = findViewById(R.id.phone_number_input)
        callButton = findViewById(R.id.call_button)
        
        // Handle ACTION_DIAL intent
        intent?.data?.let { uri ->
            val phoneNumber = uri.schemeSpecificPart
            phoneNumberInput.setText(phoneNumber)
        }
        
        callButton.setOnClickListener {
            val phoneNumber = phoneNumberInput.text.toString()
            if (phoneNumber.isNotEmpty()) {
                makeCall(phoneNumber)
            }
        }
    }
    
    private fun makeCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        startActivity(intent)
    }
}
