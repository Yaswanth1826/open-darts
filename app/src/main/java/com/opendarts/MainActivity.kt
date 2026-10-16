package com.opendarts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.opendarts.ui.home.OpenDartsApp
import com.opendarts.ui.theme.OpenDartsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpenDartsTheme { OpenDartsApp() } }
    }
}
