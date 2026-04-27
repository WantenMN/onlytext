package org.wanten.onlytext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.wanten.onlytext.ui.MainScreen
import org.wanten.onlytext.ui.theme.OnlyTextTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnlyTextTheme {
                MainScreen()
            }
        }
    }
}
