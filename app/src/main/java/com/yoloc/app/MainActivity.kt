package com.yoloc.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.yoloc.app.screens.YoloCRoot
import com.yoloc.app.util.AppLanguages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Redesign 2026-10-28: the activity starts again after a language change, so this puts the
        // stored class names into that language. A failed open is the startup screen's to report.
        val database = (application as YoloCApp).container.database
        lifecycleScope.launch {
            try {
                database.syncCatalog(this@MainActivity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("main", "Could not update class names for the language", e)
            }
        }
        setContent { YoloCRoot() }
    }
}
