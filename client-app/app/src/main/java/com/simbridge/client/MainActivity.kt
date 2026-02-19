package com.simbridge.client

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
import com.simbridge.client.data.Prefs
import com.simbridge.client.service.ClientService
import com.simbridge.client.ui.nav.AppNavigation
import com.simbridge.client.ui.theme.SimBridgeClientTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private var clientService: ClientService? = null
    private var serviceBound = false
    private var serviceState = mutableStateOf<ClientService?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            clientService = (binder as ClientService.LocalBinder).service
            serviceBound = true
            serviceState.value = clientService
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            clientService = null
            serviceBound = false
            serviceState.value = null
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        requestPermissions()

        setContent {
            SimBridgeClientTheme {
                val navController = rememberNavController()
                val service by serviceState

                AppNavigation(
                    navController = navController,
                    prefs = prefs,
                    service = service,
                    onStartService = ::startClientService,
                    onStopService = ::stopClientService,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, ClientService::class.java), serviceConnection, 0)
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun startClientService() {
        val intent = Intent(this, ClientService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun stopClientService() {
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false }
        stopService(Intent(this, ClientService::class.java))
        clientService = null
        serviceState.value = null
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
