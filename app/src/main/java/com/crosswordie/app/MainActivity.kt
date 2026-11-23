package com.crosswordie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crosswordie.app.ui.game.GameScreen
import com.crosswordie.app.ui.game.GameViewModel
import com.crosswordie.app.ui.theme.CrosswordieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            CrosswordieTheme {
                val gameViewModel: GameViewModel = viewModel(
                    factory = GameViewModel.provideFactory(applicationContext)
                )
                GameScreen(viewModel = gameViewModel)
            }
        }
    }
}
