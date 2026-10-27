package com.sweepiesoftie.earmark.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sweepiesoftie.earmark.ui.theme.EarmarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EarmarkTheme {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                    Text("Earmark", style = MaterialTheme.typography.displaySmall)
                }
            }
        }
    }
}
