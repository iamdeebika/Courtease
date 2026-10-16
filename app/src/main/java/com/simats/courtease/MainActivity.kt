package com.simats.courtease

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.simats.courtease.navigation.AppNavigation
import com.simats.courtease.ui.theme.CourtEaseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CourtEaseTheme {
                AppNavigation()
            }
        }
    }
}