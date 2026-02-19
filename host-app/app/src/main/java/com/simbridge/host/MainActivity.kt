package com.simbridge.host

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.simbridge.host.data.Prefs
import com.simbridge.host.service.BridgeService
import com.simbridge.host.ui.nav.AppNavigation
import com.simbridge.host.ui.theme.SimBridgeTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private var bridgeService: BridgeService? = null
    private var serviceBound = false
    private var serviceState = mutableStateOf<BridgeService?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bridgeService = (binder as BridgeService.LocalBinder).service
            serviceBound = true
            serviceState.value = bridgeService
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bridgeService = null
            serviceBound = false
            serviceState.value = null
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — service will handle gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        requestPermissions()

        setContent {
            SimBridgeTheme {
                val navController = rememberNavController()
                val service by serviceState

                AppNavigation(
                    navController = navController,
                    prefs = prefs,
                    service = service,
                    onStartService = ::startBridgeService,
                    onStopService = ::stopBridgeService,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to existing service if running
        val intent = Intent(this, BridgeService::class.java)
        bindService(intent, serviceConnection, 0)
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun startBridgeService() {
        val intent = Intent(this, BridgeService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun stopBridgeService() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        stopService(Intent(this, BridgeService::class.java))
        bridgeService = null
        serviceState.value = null
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
