package com.nil.mopitube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nil.mopitube.navigation.AppNav // <-- Import AppNav
import com.nil.mopitube.theme.MopiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MopiTheme {
                // The root of the UI is AppNav again
                AppNav()
            }
        }
    }
}
