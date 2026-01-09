package com.frauddetector

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.frauddetector.ui.CallHistoryFragment
import com.frauddetector.ui.SettingsFragment
import com.frauddetector.utils.DefaultDialerManager
import com.frauddetector.utils.PermissionManager
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Main activity with bottom navigation.
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var permissionManager: PermissionManager
    private lateinit var dialerManager: DefaultDialerManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        permissionManager = PermissionManager(this)
        dialerManager = DefaultDialerManager(this)
        
        // Setup bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_history -> {
                    loadFragment(CallHistoryFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
        
        // Load home fragment by default
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
        
        // Check permissions and default dialer status
        checkPermissionsAndDialerStatus()
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    private fun checkPermissionsAndDialerStatus() {
        // Check if app is default dialer
        if (!dialerManager.isDefaultDialer()) {
            dialerManager.requestDefaultDialer()
        }
        
        // Request necessary permissions
        permissionManager.requestAllPermissions()
    }
}

/**
 * Home fragment showing app status.
 */
class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
}
