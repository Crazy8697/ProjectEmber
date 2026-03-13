package com.projectember.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.projectember.mobile.navigation.EmberNavGraph
import com.projectember.mobile.ui.theme.ProjectEmberTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProjectEmberTheme {
                EmberNavGraph()
            }
        }
    }
}
