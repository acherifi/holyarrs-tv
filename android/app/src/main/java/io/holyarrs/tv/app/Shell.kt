package io.holyarrs.tv.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.holyarrs.tv.core.Kind
import io.holyarrs.tv.core.Radii
import io.holyarrs.tv.core.Route
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.screens.CalendarScreen
import io.holyarrs.tv.screens.DownloadsScreen
import io.holyarrs.tv.screens.ExploreMode
import io.holyarrs.tv.screens.ExploreScreen
import io.holyarrs.tv.screens.HomeScreen
import io.holyarrs.tv.screens.LibraryScreen
import io.holyarrs.tv.screens.LibraryTitleResolver
import io.holyarrs.tv.screens.PersonScreen
import io.holyarrs.tv.screens.RequestsScreen
import io.holyarrs.tv.screens.SearchScreen
import io.holyarrs.tv.screens.SettingsScreen
import io.holyarrs.tv.screens.TitleScreen
import io.holyarrs.tv.ui.Focusable
import kotlinx.coroutines.delay

/** Width of the collapsed icon rail. */
val RAIL = 128.pt

/**
 * The web app's frame: a sidebar on the left (collapsed to an icon rail while you
 * browse, expanded with labels when focus moves into it) and the page.
 */
@Composable
fun Shell() {
    val app = LocalApp.current
    val p = Theme.p
    var sidebarOpen by remember { mutableStateOf(false) }
    var pageHasFocus by remember { mutableStateOf(false) }
    // A page has nothing focused until it loads, and Compose meanwhile puts focus on
    // the first thing on screen (the menu). So after every page change, and when
    // focus is lost, focus goes into the page as soon as it has something to
    // focus, unless you press a key in the menu meanwhile.
    var focusTicket by remember { mutableStateOf(0) }
    var lastKey by remember { mutableStateOf(0L) }
    val pageFocus = remember { FocusRequester() }
    val stack = app.stack()
    val pageKey = app.section to stack.size

    // Back: pop the page; on a section's first page, go to the menu (the sidebar).
    // With the menu focused, back leaves the app as usual.
    BackHandler(enabled = !sidebarOpen) {
        if (!app.back()) runCatching { app.sidebarFocus.requestFocus() }
    }

    LaunchedEffect(pageKey, focusTicket) {
        val started = System.currentTimeMillis()
        // Let the old page fade out first, or focus lands on it and is lost with it.
        delay(300)
        repeat(40) {
            if (pageHasFocus) return@LaunchedEffect
            if (sidebarOpen && lastKey > started) return@LaunchedEffect
            runCatching { pageFocus.requestFocus() }
            delay(150)
        }
    }
    LaunchedEffect(pageHasFocus, sidebarOpen) {
        if (pageHasFocus || sidebarOpen) return@LaunchedEffect
        delay(400)
        focusTicket++
    }

    Box(Modifier.fillMaxSize().background(p.bg).onPreviewKeyEvent { lastKey = System.currentTimeMillis(); false }) {
        Box(
            Modifier.fillMaxSize().padding(start = RAIL)
                .onFocusChanged { pageHasFocus = it.hasFocus }
                .focusRequester(pageFocus)
                .focusGroup(),
        ) {
            Pages(app.section, stack)
        }
        val width by animateDpAsState(if (sidebarOpen) 400.pt else RAIL, tween(240), label = "sidebar")
        Sidebar(
            open = sidebarOpen,
            modifier = Modifier.width(width).fillMaxHeight()
                .shadow(if (sidebarOpen) 24.dp else 0.dp)
                .onFocusChanged { sidebarOpen = it.hasFocus }
                .focusGroup(),
        ) { focusTicket++ }
    }
}

/**
 * The section's first page and the pages opened from it. Pages under the top one
 * stay alive but are not laid out (so they can't take focus): Back shows them
 * again exactly as you left them, scroll position and focused card included.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Pages(section: Section, stack: List<Route>) {
    val levels = listOf<Route?>(null) + stack
    Layout(
        content = {
            levels.forEachIndexed { i, route ->
                key(section, i, route) {
                    Box(Modifier.fillMaxSize().focusRestorer().focusGroup()) {
                        if (route == null) Screen(section) else RouteScreen(route)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val top = measurables.last().measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) { top.place(0, 0) }
    }
}

@Composable
private fun Screen(s: Section) {
    when (s) {
        Section.HOME -> HomeScreen()
        Section.EXPLORE -> ExploreScreen(ExploreMode.EXPLORE)
        Section.POPULAR -> ExploreScreen(ExploreMode.POPULAR)
        Section.MOVIES -> LibraryScreen(Kind.MOVIE)
        Section.SHOWS -> LibraryScreen(Kind.SHOW)
        Section.CALENDAR -> CalendarScreen()
        Section.REQUESTS -> RequestsScreen()
        Section.DOWNLOADS -> DownloadsScreen()
        Section.SEARCH -> SearchScreen()
        Section.SETTINGS -> SettingsScreen(firstRun = false)
    }
}

@Composable
private fun RouteScreen(route: Route) {
    when (route) {
        is Route.Title -> TitleScreen(route.kind, route.tmdb)
        is Route.LibraryTitle -> LibraryTitleResolver(route.id)
        is Route.Person -> PersonScreen(route.id, route.name)
    }
}

private val groups: List<Pair<String?, List<Section>>> = listOf(
    null to listOf(Section.SEARCH, Section.HOME, Section.EXPLORE, Section.POPULAR),
    "Library" to listOf(Section.MOVIES, Section.SHOWS),
    "Your stuff" to listOf(Section.CALENDAR, Section.REQUESTS),
    "Activity" to listOf(Section.DOWNLOADS),
    "System" to listOf(Section.SETTINGS),
)

@Composable
private fun Sidebar(open: Boolean, modifier: Modifier, enterPage: () -> Unit) {
    val app = LocalApp.current
    val p = Theme.p
    val line = p.line
    Column(modifier.background(p.sidebarBG).drawWithContent {
        drawContent()
        drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
    }) {
        Row(
            Modifier.padding(start = 38.pt, top = 44.pt, bottom = 18.pt),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.pt),
        ) {
            BrandMark(Modifier.size(52.pt))
            if (open) Text("HolyArrs", style = Typo.geist(19f, FontWeight.Bold), color = p.text, maxLines = 1)
        }
        Column(Modifier.padding(horizontal = 16.pt), verticalArrangement = Arrangement.spacedBy(1.pt)) {
            for ((label, sections) in groups) {
                if (label != null) {
                    Text(
                        if (open) label else " ", style = Typo.footnote, color = p.textMuted, maxLines = 1,
                        modifier = Modifier.padding(start = 20.pt, top = 10.pt, bottom = 1.pt),
                    )
                }
                for (s in sections) SidebarRow(s, open) {
                    app.select(s)
                    enterPage()
                }
            }
        }
        Spacer(Modifier.weight(1f))
        app.me?.let { me ->
            Row(
                Modifier.padding(start = 36.pt, bottom = 36.pt, top = 10.pt),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.pt),
            ) {
                Box(Modifier.size(56.pt).clip(CircleShape).background(Color(0xFF3E5B2C)), contentAlignment = Alignment.Center) {
                    Text((me.name ?: "?").take(1).uppercase(), style = Typo.headline, color = Color.White)
                }
                if (open) Column {
                    Text(me.name ?: "", style = Typo.headline, color = p.text, maxLines = 1)
                    Text((me.role ?: "").replaceFirstChar { it.uppercase() }, style = Typo.footnote, color = p.textMuted)
                }
            }
        }
    }
}

@Composable
private fun SidebarRow(s: Section, open: Boolean, onClick: () -> Unit) {
    val app = LocalApp.current
    val p = Theme.p
    val selected = app.section == s
    Focusable(onClick, focusRequester = if (selected) app.sidebarFocus else null) { focused ->
        Row(
            Modifier.then(if (open) Modifier.fillMaxWidth() else Modifier.width(96.pt)).height(56.pt)
                .clip(RoundedCornerShape(Radii.btn))
                .background(if (focused) p.surface3 else if (selected) p.surface2 else Color.Transparent)
                .padding(horizontal = 22.pt),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.pt),
        ) {
            Icon(
                s.icon, s.label, Modifier.size(26.pt),
                tint = if (selected) p.accent else if (focused) p.text else p.textMuted,
            )
            if (open) Text(s.label, style = Typo.geist(15f, FontWeight.SemiBold), color = p.text, maxLines = 1)
        }
    }
}

/** HolyArrs' diamond mark (static/brand/mark-color.svg). */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val half = s / 2
        rotate(45f, Offset(half, half)) {
            val side = s * 0.65f
            val o = (s - side) / 2
            drawRoundRect(
                Color(0xFF1C1D21), topLeft = Offset(o, o),
                size = androidx.compose.ui.geometry.Size(side, side),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.06f),
            )
        }
        // Red half: the right side of the diamond, with the web mark's curved edge.
        val clip = Path().apply {
            moveTo(half, half - s * 0.46f); lineTo(half + s * 0.46f, half); lineTo(half, half + s * 0.46f); lineTo(half - s * 0.46f, half); close()
        }
        val red = Path().apply {
            moveTo(s * 0.5f, 0f)
            cubicTo(s * 0.42f, s * 0.18f, s * 0.46f, s * 0.32f, s * 0.58f, s * 0.41f)
            cubicTo(s * 0.70f, s * 0.50f, s * 0.66f, s * 0.70f, s * 0.54f, s)
            lineTo(s * 1.1f, s); lineTo(s * 1.1f, 0f); close()
        }
        val both = Path().apply { op(clip, red, androidx.compose.ui.graphics.PathOperation.Intersect) }
        drawPath(both, Color(0xFFF23A2E))
        val h = s * 0.22f
        val star = Path().apply {
            moveTo(half, half - h)
            quadraticTo(half + h * 0.2f, half - h * 0.2f, half + h, half)
            quadraticTo(half + h * 0.2f, half + h * 0.2f, half, half + h)
            quadraticTo(half - h * 0.2f, half + h * 0.2f, half - h, half)
            quadraticTo(half - h * 0.2f, half - h * 0.2f, half, half - h)
            close()
        }
        drawPath(star, Color.White)
    }
}
