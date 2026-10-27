package com.simats.courtease

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.simats.courtease.data.CaseRepository
import com.simats.courtease.navigation.AppNavigation
import com.simats.courtease.ui.theme.CourtEaseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Navy top bar draws behind the status bar, so keep the status icons light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        CaseRepository.init(applicationContext)
        setContent {
            CourtEaseTheme {
                AppNavigation()
            }
        }
    }
}