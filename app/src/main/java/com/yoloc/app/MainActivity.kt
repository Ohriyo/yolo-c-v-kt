package com.yoloc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yoloc.app.screens.dev.InferenceScreen
import com.yoloc.app.theme.YoloCTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YoloCTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Until Step 5 adds YoloCRoot and navigation, debug builds open the Model test
                    // screen directly (pipeline first, verified by the golden test, before screens).
                    if (BuildConfig.DEBUG) {
                        InferenceScreen(Modifier.padding(innerPadding))
                    } else {
                        Greeting(
                            name = "Android",
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    YoloCTheme {
        Greeting("Android")
    }
}
