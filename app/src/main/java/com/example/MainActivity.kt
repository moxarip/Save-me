package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.GameViewModel
import com.example.ui.DrawToSaveApp
import com.example.ui.theme.DrawToSaveTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DrawToSaveTheme {
                    DrawToSaveApp(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPauseLifecycle()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumeLifecycle()
    }
}
