package com.code7.techupdates

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.code7.techupdates.ui.AppRoot
import com.code7.techupdates.ui.theme.Code7Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val stories = (application as TechUpdatesApp).container.storyRepository
        setContent {
            Code7Theme {
                AppRoot(storyLookup = stories::story)
            }
        }
    }
}
