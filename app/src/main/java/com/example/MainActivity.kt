package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jkword.data.local.AppThemeMode
import com.example.jkword.data.local.ThemePreferences
import com.example.jkword.editor.EditorViewModel
import com.example.jkword.ui.editor.EditorScreen
import com.example.jkword.ui.home.HomeScreen
import com.example.jkword.ui.home.HomeViewModel
import com.example.ui.theme.LocalSetTheme
import com.example.ui.theme.LocalThemeMode
import com.example.ui.theme.LocalToggleTheme
import com.example.ui.theme.MyApplicationTheme

sealed class Screen {
    object Home : Screen()
    data class Editor(val documentId: Long) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themePrefs = ThemePreferences.getInstance(this)

        setContent {
            val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> systemInDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            CompositionLocalProvider(
                LocalThemeMode provides themeMode,
                LocalToggleTheme provides { themePrefs.toggleNextTheme() },
                LocalSetTheme provides { mode -> themePrefs.setThemeMode(mode) }
            ) {
                MyApplicationTheme(darkTheme = isDark) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                        val homeViewModel: HomeViewModel = viewModel()
                        val editorViewModel: EditorViewModel = viewModel()

                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                is Screen.Home -> {
                                    HomeScreen(
                                        viewModel = homeViewModel,
                                        onOpenDocument = { docId ->
                                            currentScreen = Screen.Editor(docId)
                                        }
                                    )
                                }
                                is Screen.Editor -> {
                                    EditorScreen(
                                        viewModel = editorViewModel,
                                        documentId = screen.documentId,
                                        onNavigateBack = {
                                            currentScreen = Screen.Home
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
