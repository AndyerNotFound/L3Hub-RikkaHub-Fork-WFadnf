package com.l3hub.database.wfadnf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.l3hub.database.wfadnf.core.HubController
import com.l3hub.database.wfadnf.ui.HubApp
import com.l3hub.database.wfadnf.ui.HubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HubTheme {
                val controller = remember { HubController.of(applicationContext) }
                LaunchedEffect(Unit) { controller.start() }
                HubApp(controller)
            }
        }
    }
}
