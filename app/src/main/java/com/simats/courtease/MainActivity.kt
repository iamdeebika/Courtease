package com.simats.courtease

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.navigation.AppNavigation
import com.simats.courtease.ui.theme.CourtEaseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Session.restore(applicationContext)
        setContent {
            CourtEaseTheme {
                AppNavigation()
            }
        }
    }
}