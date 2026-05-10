package com.camryobd

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.camryobd.ui.ConnectionFragment
import com.camryobd.ui.DTCFragment
import com.camryobd.ui.DashboardFragment
import com.camryobd.ui.BatteryFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ConnectionFragment())
                .commit()
        }

        findViewById<BottomNavigationView>(R.id.bottom_nav)
            ?.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_connect -> { switchFragment(ConnectionFragment()); true }
                    R.id.nav_dashboard -> { switchFragment(DashboardFragment()); true }
                    R.id.nav_battery -> { switchFragment(BatteryFragment()); true }
                    R.id.nav_dtc -> { switchFragment(DTCFragment()); true }
                    else -> false
                }
            }
    }

    private fun switchFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun switchToDashboard() {
        findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_dashboard
    }
}
