package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.providers.SpreadsheetViewModel
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SpreadsheetEditorScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SpreadsheetViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            MyApplicationTheme(darkTheme = isDarkMode) {
                var currentScreen by remember { mutableStateOf("FILE_MANAGER") }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            "FILE_MANAGER" -> {
                                FileManagerScreen(
                                    viewModel = viewModel,
                                    onSpreadsheetOpened = { currentScreen = "SPREADSHEET_EDITOR" },
                                    onNavigateSettings = { currentScreen = "SETTINGS" }
                                )
                            }
                            "SPREADSHEET_EDITOR" -> {
                                SpreadsheetEditorScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = "FILE_MANAGER" }
                                )
                            }
                            "SETTINGS" -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = "FILE_MANAGER" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
