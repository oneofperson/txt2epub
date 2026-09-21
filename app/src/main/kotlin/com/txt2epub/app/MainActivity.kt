package com.txt2epub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.txt2epub.app.ui.AppRoot
import com.txt2epub.app.ui.theme.Txt2EpubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val incoming = intent?.data
        setContent {
            Txt2EpubTheme {
                AppRoot(incomingUri = remember { incoming })
            }
        }
    }
}
