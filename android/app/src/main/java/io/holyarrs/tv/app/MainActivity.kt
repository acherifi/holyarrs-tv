package io.holyarrs.tv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import io.holyarrs.tv.core.HolyTheme
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // adb shell am start -n io.holyarrs.tv/.app.MainActivity --ez demo true --es screen home
        val demo = intent.getBooleanExtra("demo", false)
        val screen = intent.getStringExtra("screen")
        val dark = if (intent.hasExtra("dark")) intent.getBooleanExtra("dark", true) else null
        setContent {
            val app = remember { AppState(applicationContext, demo, screen) }
            LaunchedEffect(Unit) {
                if (dark != null) app.setDarkMode(dark)
                app.loadMe()
                app.applyStartScreen()
            }
            CompositionLocalProvider(LocalApp provides app) {
                HolyTheme(app.dark) { Root() }
            }
        }
    }
}

@Composable
fun Root() {
    val app = LocalApp.current
    val p = Theme.p
    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (app.configured) Shell() else SettingsScreen(firstRun = true)
        AnimatedVisibility(
            app.toast != null, Modifier.align(Alignment.TopCenter).padding(top = 40.pt),
            enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut(),
        ) {
            Text(
                app.toast ?: "", style = Typo.headline, color = p.text,
                modifier = Modifier.shadow(10.dp, CircleShape).clip(CircleShape).background(p.surface1)
                    .padding(horizontal = 30.pt, vertical = 18.pt),
            )
        }
    }
}
