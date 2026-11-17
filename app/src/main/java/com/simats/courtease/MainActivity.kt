package com.simats.courtease

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.simats.courtease.data.ServerConfig
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.navigation.AppNavigation
import com.simats.courtease.push.PushSetup
import com.simats.courtease.ui.components.PushRegistrar
import com.simats.courtease.ui.components.SyncLoop
import com.simats.courtease.ui.theme.CourtEaseStyle
import com.simats.courtease.ui.theme.CourtEaseTheme

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        ServerConfig.init(applicationContext)
        Store.init(applicationContext)
        Session.restore(applicationContext)
        PushSetup.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            CourtEaseTheme {
                CourtEaseStyle {
                    SyncLoop()
                    PushRegistrar()
                    AppNavigation()
                }
            }
        }
    }
}