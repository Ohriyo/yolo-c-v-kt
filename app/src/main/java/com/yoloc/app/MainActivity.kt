package com.yoloc.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.yoloc.app.screens.YoloCRoot
import com.yoloc.app.screens.intro.SplashHandoff
import com.yoloc.app.store.SettingsStore
import com.yoloc.app.util.AppLanguages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as YoloCApp).container
        // Redesign 2026-10-31: the splash stays only while the startup settings load (YoloCRoot
        // waits for them too). No extra delay; a failed open shows the startup error screen.
        val settings = container.settingsStore
        splash.setKeepOnScreenCondition {
            settings.state.value.status.let { it == SettingsStore.Status.Idle || it == SettingsStore.Status.Loading }
        }
        val intro = if (opensIntro(savedInstanceState)) SplashHandoff() else null
        introSeen = true
        if (intro != null) splash.setOnExitAnimationListener(intro::takeOver)
        // Redesign 2026-10-28: the activity starts again after a language change, so this puts the
        // stored class names into that language. A failed open is the startup screen's to report.
        val database = container.database
        lifecycleScope.launch {
            try {
                database.syncCatalog(this@MainActivity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("main", "Could not update class names for the language", e)
            }
        }
        setContent { YoloCRoot(intro = intro) }
    }

    /**
     * The launch intro plays on a cold start from the launcher only: not when the activity starts
     * again (language change, rotation, process restore) or reopens in a running app, and not from
     * a link or a share.
     */
    private fun opensIntro(savedInstanceState: Bundle?): Boolean =
        !introSeen &&
            savedInstanceState == null &&
            intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            intent.data == null

    private companion object {
        /** Once per process. */
        var introSeen = false
    }
}
