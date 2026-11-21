package io.holyarrs.tv.app

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import coil.ImageLoader
import io.holyarrs.tv.core.Api
import io.holyarrs.tv.core.Kind
import io.holyarrs.tv.core.Me
import io.holyarrs.tv.core.Route
import io.holyarrs.tv.core.WatchLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Section(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    EXPLORE("Explore", Icons.Outlined.Explore),
    POPULAR("New & Popular", Icons.Outlined.AutoAwesome),
    MOVIES("Movies", Icons.Outlined.Movie),
    SHOWS("TV shows", Icons.Outlined.Tv),
    CALENDAR("Calendar", Icons.Outlined.CalendarMonth),
    REQUESTS("Requests", Icons.Outlined.Checklist),
    DOWNLOADS("Downloads", Icons.Outlined.Download),
    SEARCH("Search", Icons.Outlined.Search),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

val LocalApp = staticCompositionLocalOf<AppState> { error("no app state") }

/** Everything the app shares: where HolyArrs is, who is signed in, Jellyfin hand-off. */
class AppState(private val context: Context, val demo: Boolean, val startScreen: String?) {
    private val prefs = context.getSharedPreferences("holyarrs", Context.MODE_PRIVATE)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var server by mutableStateOf(prefs.getString("server", "") ?: "")
        private set
    var apiKey by mutableStateOf(prefs.getString("apiKey", "") ?: "")
        private set
    var dark by mutableStateOf(prefs.getBoolean("dark", true))
        private set

    var section by mutableStateOf(Section.HOME)
    val stacks = mutableStateMapOf<Section, List<Route>>()
    var me by mutableStateOf<Me?>(null)
    var api by mutableStateOf(Api(server, apiKey, demo, context))
        private set
    var toast by mutableStateOf<String?>(null)
        private set
    /** Bumped after an add, ask or follow so lists reload. */
    var libraryVersion by mutableIntStateOf(0)

    /** The sidebar row of the current section, for "back to the menu". */
    val sidebarFocus = FocusRequester()

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(Api.client)
        .crossfade(true)
        .build()

    val configured get() = api.configured

    fun connect(server: String, key: String) {
        prefs.edit().putString("server", server).putString("apiKey", key).apply()
        this.server = server
        apiKey = key
        api = Api(server, key, demo, context)
        me = null
        stacks.clear()
        scope.launch { loadMe() }
    }

    fun setDarkMode(on: Boolean) {
        prefs.edit().putBoolean("dark", on).apply()
        dark = on
    }

    suspend fun loadMe() {
        if (!configured) return
        me = runCatching { api.get<Me>("/auth/me") }.getOrNull()
    }

    fun stack(s: Section = section) = stacks[s].orEmpty()

    fun open(route: Route) {
        stacks[section] = stack() + route
    }

    /** Pops the current section's page; false when already on its first page. */
    fun back(): Boolean {
        val st = stack()
        if (st.isEmpty()) return false
        stacks[section] = st.dropLast(1)
        return true
    }

    fun select(s: Section) {
        if (section == s) stacks[s] = emptyList()
        section = s
    }

    private var toastJob: Job? = null

    fun show(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(3500)
            if (toast == message) toast = null
        }
    }

    // Jellyfin hand-off

    /**
     * Opens the title in Jellyfin for Android TV. HolyArrs gives the item id (the web
     * app's "Open in Jellyfin" link); the Jellyfin app opens an item when its start
     * screen gets the id as "ItemId", the way its home-screen channels do.
     */
    suspend fun openInJellyfin(titleId: Int) {
        try {
            val links = api.get<WatchLinks>("/media-servers/watch-links?title=$titleId")
            val link = links.links?.firstOrNull { it.kind == "jellyfin" } ?: links.links?.firstOrNull()
            if (link == null) { show("Jellyfin doesn't have this title yet."); return }
            val raw = Regex("[?&]id=([0-9a-fA-F-]+)").find(link.url)?.groupValues?.get(1)
            if (raw == null) { show("Couldn't read the Jellyfin link."); return }
            val id = dashed(raw)
            if (demo) { show("Would open Jellyfin item $id"); return }
            val pkg = JELLYFIN_PACKAGES.firstOrNull { installed(it) }
            if (pkg == null) { show("Install Jellyfin for Android TV from the Play Store to play it."); return }
            val intent = Intent()
                .setComponent(ComponentName(pkg, "org.jellyfin.androidtv.ui.startup.StartupActivity"))
                .putExtra("ItemId", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    ?: show("Couldn't open Jellyfin.")
            }
        } catch (e: Exception) {
            show(e.message ?: "Couldn't open Jellyfin.")
        }
    }

    private fun installed(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    /** `-screen <name>` opens a given screen at launch (used for emulator screenshots). */
    fun applyStartScreen() {
        when (startScreen ?: return) {
            "explore" -> section = Section.EXPLORE
            "popular" -> section = Section.POPULAR
            "movies" -> section = Section.MOVIES
            "shows" -> section = Section.SHOWS
            "search" -> section = Section.SEARCH
            "requests" -> section = Section.REQUESTS
            "calendar" -> section = Section.CALENDAR
            "downloads" -> section = Section.DOWNLOADS
            "settings" -> section = Section.SETTINGS
            "title-movie", "add-sheet" -> { section = Section.EXPLORE; open(Route.Title(Kind.MOVIE, "1218925")) }
            "title-show" -> { section = Section.SHOWS; open(Route.Title(Kind.SHOW, "424")) }
            "person" -> { section = Section.EXPLORE; open(Route.Person("182287", "John Barrowman")) }
            else -> section = Section.HOME
        }
    }

    companion object {
        val JELLYFIN_PACKAGES = listOf("org.jellyfin.androidtv", "org.jellyfin.androidtv.debug")

        fun dashed(id: String): String {
            val h = id.replace("-", "")
            if (h.length != 32) return id
            return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
        }
    }
}
