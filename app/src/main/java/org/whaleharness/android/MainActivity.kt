package org.whaleharness.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whaleharness.theme.WhaleHarnessTheme
import org.whaleharness.android.ui.WhaleHarnessApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhaleHarnessTheme {
                WhaleHarnessApp()
            }
        }
    }
}
