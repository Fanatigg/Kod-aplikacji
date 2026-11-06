package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.ScannerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ScannerViewModel

class MainActivity : ComponentActivity() {
    private val scannerViewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(applicationContext)
        } catch (_: Throwable) {
        }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ScannerScreen(viewModel = scannerViewModel)
            }
        }
    }
}
