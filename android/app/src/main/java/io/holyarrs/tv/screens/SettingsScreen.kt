package io.holyarrs.tv.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.holyarrs.tv.app.AppState
import io.holyarrs.tv.app.BrandMark
import io.holyarrs.tv.app.LocalApp
import io.holyarrs.tv.core.Api
import io.holyarrs.tv.core.Me
import io.holyarrs.tv.core.Radii
import io.holyarrs.tv.core.Space
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.ui.Pill
import io.holyarrs.tv.ui.PillKind
import io.holyarrs.tv.ui.SearchField
import io.holyarrs.tv.ui.SegmentBar
import kotlinx.coroutines.launch

/** Connection settings: HolyArrs address + API key, appearance, Jellyfin hand-off. */
@Composable
fun SettingsScreen(firstRun: Boolean) {
    val app = LocalApp.current
    val p = Theme.p
    val context = LocalContext.current
    var server by remember { mutableStateOf(app.server) }
    var key by remember { mutableStateOf(app.apiKey) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun connect() {
        if (testing) return
        testing = true
        scope.launch {
            try {
                val me = Api(server, key, false, context).get<Me>("/auth/me")
                app.connect(server, key)
                status = "Connected as ${me.name ?: "?"}."
            } catch (e: Exception) { status = e.message }
            testing = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter, vertical = 70.pt).widthIn(max = 1300.pt),
        verticalArrangement = Arrangement.spacedBy(20.pt),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.pt)) {
            BrandMark(Modifier.size(80.pt))
            Text(if (firstRun) "Welcome to HolyArrs" else "Settings", style = Typo.largeTitle, color = p.text)
        }
        Panel("HolyArrs", "Your server's address and an API key (Settings → Sign-in → API keys in the web app, scopes read and write).") {
            Field("Address") { SearchField(server, { server = it }, "http://192.168.1.10:6262", icon = null) }
            Field("API key") { SearchField(key, { key = it }, "hak_…", icon = null, secret = true) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.pt)) {
                Pill(if (testing) "Connecting…" else "Connect", ::connect, kind = PillKind.ACCENT)
                status?.let { Text(it, style = Typo.callout, color = p.textMuted) }
            }
        }
        if (!firstRun) {
            Panel("Appearance", "The web app's light or dark look.") {
                SegmentBar(
                    listOf(Triple(true, "Dark", Icons.Outlined.DarkMode), Triple(false, "Light", Icons.Outlined.LightMode)),
                    app.dark, { app.setDarkMode(it) },
                )
            }
            val installed = remember { jellyfinInstalled(context) }
            Panel(
                "Jellyfin",
                "\"Open in Jellyfin\" opens the title in Jellyfin for Android TV, signed in on this TV with your Jellyfin account.",
            ) {
                Text(
                    if (installed) "Jellyfin for Android TV is installed." else "Install Jellyfin for Android TV from the Play Store to play from here.",
                    style = Typo.body, color = if (installed) p.ok else p.wait,
                )
            }
            app.me?.let { me ->
                Panel("Account", "Signed in to HolyArrs with an API key.") {
                    Text("${me.name ?: ""} · ${(me.role ?: "").replaceFirstChar { it.uppercase() }}", style = Typo.body, color = p.text)
                }
            }
        }
    }
}

private fun jellyfinInstalled(context: Context) =
    AppState.JELLYFIN_PACKAGES.any { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }

@Composable
private fun Panel(title: String, line: String, content: @Composable ColumnScope.() -> Unit) {
    val p = Theme.p
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.panel)).background(p.surface1)
            .border(1.dp, p.line, RoundedCornerShape(Radii.panel)).padding(20.pt).focusGroup(),
        verticalArrangement = Arrangement.spacedBy(11.pt),
    ) {
        Text(title, style = Typo.title2, color = p.text)
        Text(line, style = Typo.callout, color = p.textMuted)
        content()
    }
}

@Composable
private fun Field(label: String, field: @Composable () -> Unit) {
    val p = Theme.p
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.pt)) {
        Text(label, style = Typo.subhead, color = p.textMuted, modifier = Modifier.width(260.pt))
        field()
    }
}
