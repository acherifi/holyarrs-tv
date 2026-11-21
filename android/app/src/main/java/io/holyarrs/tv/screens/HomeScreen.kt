package io.holyarrs.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.holyarrs.tv.app.LocalApp
import io.holyarrs.tv.app.Section
import io.holyarrs.tv.core.Asks
import io.holyarrs.tv.core.Ask
import io.holyarrs.tv.core.Health
import io.holyarrs.tv.core.Home
import io.holyarrs.tv.core.HomeCard
import io.holyarrs.tv.core.Kind
import io.holyarrs.tv.core.Radii
import io.holyarrs.tv.core.Route
import io.holyarrs.tv.core.Space
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.Tiles
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.parseDate
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.ui.ErrorCard
import io.holyarrs.tv.ui.Focusable
import io.holyarrs.tv.ui.LoadingView
import io.holyarrs.tv.ui.Pill
import io.holyarrs.tv.ui.RemoteImage
import io.holyarrs.tv.ui.Shelf
import io.holyarrs.tv.ui.bytes
import io.holyarrs.tv.ui.lift
import io.holyarrs.tv.ui.ring
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** Home: greeting, the overview tiles and the rows the server orders. */
@Composable
fun HomeScreen() {
    val app = LocalApp.current
    var home by remember { mutableStateOf<Home?>(null) }
    var health by remember { mutableStateOf<Health?>(null) }
    var asks by remember { mutableStateOf<List<Ask>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        val api = app.api
        try {
            coroutineScope {
                val h = async { api.get<Home>("/home") }
                val hl = async { runCatching { api.get<Health>("/health") }.getOrNull() }
                val a = async { runCatching { api.get<Asks>("/asks?limit=200") }.getOrNull() }
                home = h.await()
                health = hl.await()
                asks = a.await()?.asks.orEmpty()
                error = null
            }
        } catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(app.libraryVersion, app.api) { load() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 60.pt),
        verticalArrangement = Arrangement.spacedBy(50.pt),
    ) {
        val h = home
        Header(newCount(h))
        when {
            h != null -> { HomeTiles(h, health, asks); HomeRows(h) }
            error != null -> ErrorCard(error!!) { scope.launch { load() } }
            else -> LoadingView()
        }
    }
}

private fun newCount(home: Home?): Int? {
    home ?: return null
    val cutoff = Instant.now().minus(Duration.ofDays(14))
    val fresh = home.arrived.orEmpty().count { (parseDate(it.at) ?: Instant.EPOCH).isAfter(cutoff) }
    return fresh + (home.new_seasons?.size ?: 0)
}

@Composable
private fun Header(newCount: Int?) {
    val app = LocalApp.current
    val p = Theme.p
    val hour = LocalTime.now().hour
    val part = when { hour < 5 -> "Good evening"; hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
    val name = app.me?.name?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
    Row(Modifier.padding(horizontal = Space.gutter).focusGroup(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.pt)) {
            Text(if (name != null) "$part, $name" else part, style = Typo.largeTitle, color = p.text)
            if (newCount != null && newCount > 0) Text("New today: $newCount titles.", style = Typo.callout, color = p.textMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.pt)) {
            Pill("Search", { app.select(Section.SEARCH) }, icon = Icons.Outlined.Search)
            Pill("Explore", { app.select(Section.EXPLORE) }, icon = Icons.Outlined.Explore)
        }
    }
}

@Composable
private fun HomeTiles(home: Home, health: Health?, asks: List<Ask>) {
    val app = LocalApp.current
    val p = Theme.p
    val lib = home.library.orEmpty()
    val titles = lib.values.sumOf { it.titles ?: 0 }
    val size = lib.values.sumOf { it.size ?: 0L }
    val movies = lib["movie"]?.titles ?: 0
    val shows = lib["show"]?.titles ?: 0
    val artists = lib["artist"]?.titles ?: 0
    val problems = health?.sources.orEmpty().count { (it.state ?: "") !in setOf("ok", "enabled") } +
        health?.downloaders.orEmpty().count { it.state != "ok" }
    val mine = asks.filter { it.state != "available" }
    val turned = mine.count { it.state == "turned_down" }
    val weekEnd = Instant.now().plus(Duration.ofDays(7))
    val week = home.soon.orEmpty().filter { (parseDate(it.at) ?: Instant.MAX).isBefore(weekEnd) }

    val tiles = mutableListOf<@Composable RowScope.() -> Unit>()
    home.arrived?.firstOrNull()?.let { hero -> tiles += { HeroTile(hero, Modifier.weight(1f)) } }
    if (app.me?.canManage != false) tiles += {
        Tile(
            Icons.Filled.Warning, Tiles.amber, "Needs attention", "$problems", Modifier.weight(1f),
            valueColor = if (problems > 0) p.wait else p.text,
            line = if (problems == 0) "Everything is working" else "$problems ${if (problems == 1) "source has" else "sources have"} a problem",
        ) { app.select(Section.DOWNLOADS) }
    }
    tiles += {
        Tile(Icons.Outlined.Checklist, Tiles.orange, "Your asks", "${mine.size}", Modifier.weight(1f),
            line = if (turned > 0) "turned down" else "waiting or on the way") { app.select(Section.REQUESTS) }
    }
    tiles += {
        Tile(Icons.Outlined.CalendarMonth, Tiles.purple, "Calendar", null, Modifier.weight(1f),
            line = "Air dates and releases", bigLine = "What's next") { app.select(Section.CALENDAR) }
    }
    tiles += {
        Tile(Icons.Filled.LibraryBooks, Tiles.indigo, "Library", "$titles", Modifier.weight(1f),
            line = "$movies movies · $shows show${if (shows == 1) "" else "s"} · $artists artists") { app.select(Section.MOVIES) }
    }
    tiles += {
        Tile(Icons.Filled.Inventory2, Tiles.cyan, "On disk", bytes(size), Modifier.weight(1f),
            line = "Across your library folders") { app.select(Section.DOWNLOADS) }
    }
    tiles += {
        Tile(Icons.Outlined.EventNote, Tiles.teal, "Coming this week", "${week.size}", Modifier.weight(1f),
            line = if (week.isEmpty()) "Popular releases landing this week" else week.take(2).joinToString(", ") { it.name }) { app.select(Section.CALENDAR) }
    }
    tiles += {
        Tile(Icons.Filled.AutoAwesome, Tiles.pink, "New for you", "${newCount(home) ?: 0}", Modifier.weight(1f),
            line = "arrived in the last two weeks") { app.select(Section.MOVIES) }
    }

    Column(Modifier.padding(horizontal = Space.gutter).focusGroup(), verticalArrangement = Arrangement.spacedBy(13.pt)) {
        for (row in tiles.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(13.pt)) {
                row.forEach { it() }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val TILE_H: Dp = 232.pt

@Composable
private fun HeroTile(hero: HomeCard, modifier: Modifier) {
    val app = LocalApp.current
    Focusable({ hero.title_id?.let { app.open(Route.LibraryTitle(it)) } }, modifier) { focused ->
        Box(Modifier.fillMaxWidth().height(TILE_H).lift(focused, Radii.panel, 1.04f).clip(RoundedCornerShape(Radii.panel)).ring(focused, Radii.panel)) {
            RemoteImage(hero.backdrop, Modifier.fillMaxSize(), "w780", hero.name)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
            Column(Modifier.align(Alignment.BottomStart).padding(13.pt), verticalArrangement = Arrangement.spacedBy(3.pt)) {
                if (hero.logo != null) {
                    RemoteImage(hero.logo, Modifier.sizeIn(maxWidth = 260.pt, maxHeight = 64.pt).height(64.pt).width(260.pt), showInitials = false, fit = true, alignment = Alignment.BottomStart)
                } else Text(hero.name, style = Typo.title2, color = Color.White)
                Text(hero.line ?: "", style = Typo.footnote, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
            }
            Row(
                Modifier.padding(9.pt).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.pt, vertical = 3.pt),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.pt),
            ) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(9.pt), tint = Color.White)
                Text("New today", style = Typo.caption, color = Color.White)
            }
        }
    }
}

/** One overview tile: coloured icon, title, chevron, big number, small line. */
@Composable
private fun Tile(
    icon: ImageVector,
    color: Color,
    title: String,
    value: String?,
    modifier: Modifier,
    valueColor: Color = Theme.p.text,
    line: String,
    bigLine: String? = null,
    onClick: () -> Unit,
) {
    val p = Theme.p
    Focusable(onClick, modifier) { focused ->
        Column(
            Modifier.fillMaxWidth().height(TILE_H).lift(focused, Radii.panel, 1.04f)
                .clip(RoundedCornerShape(Radii.panel)).background(p.surface1)
                .border(androidx.compose.ui.unit.Dp(1f), p.line, RoundedCornerShape(Radii.panel))
                .ring(focused, Radii.panel).padding(13.pt),
            verticalArrangement = Arrangement.spacedBy(7.pt),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.pt)) {
                Box(Modifier.size(44.pt).clip(RoundedCornerShape(5.pt)).background(color), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.pt), tint = Color.White)
                }
                Text(title, style = Typo.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.pt), tint = p.textFaint)
            }
            Spacer(Modifier.weight(1f))
            if (value != null) Text(value, style = Typo.geist(40f, FontWeight.Bold), color = valueColor, maxLines = 1)
            if (bigLine != null) Text(bigLine, style = Typo.title2, color = p.text)
            Text(line, style = Typo.footnote, color = p.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeRows(home: Home) {
    val order = home.rows ?: listOf("arrived", "new_season", "following", "soon")
    for (key in order) {
        when (key) {
            "arrived" -> CardRow(arrivedTitle(home.arrived.orEmpty()), "New in the last two weeks", home.arrived.orEmpty())
            "new_season" -> CardRow("New seasons", null, home.new_seasons.orEmpty())
            "following" -> CardRow("Following", "Titles you follow", home.following.orEmpty())
            "soon" -> CardRow("Coming soon", "The next 30 days", home.soon.orEmpty())
        }
    }
}

private fun arrivedTitle(cards: List<HomeCard>): String {
    val newest = cards.mapNotNull { parseDate(it.at) }.maxOrNull() ?: return "Recently added"
    val age = Duration.between(newest, Instant.now()).toHours() / 24.0
    return when { age < 1 -> "New today"; age < 7 -> "New this week"; age < 14 -> "Just arrived"; else -> "Recently added" }
}

@Composable
private fun CardRow(title: String, subtitle: String?, cards: List<HomeCard>) {
    if (cards.isEmpty()) return
    Shelf(title, subtitle) {
        items(cards, key = { it.key }) { HomeWideCard(it) }
    }
}

@Composable
private fun HomeWideCard(card: HomeCard) {
    val app = LocalApp.current
    val p = Theme.p
    val w = 560.pt
    Focusable({ card.title_id?.let { app.open(Route.LibraryTitle(it)) } }) { focused ->
        Column(Modifier.width(w).lift(focused), verticalArrangement = Arrangement.spacedBy(5.pt)) {
            Box(Modifier.width(w).height(315.pt).clip(RoundedCornerShape(Radii.card)).ring(focused)) {
                RemoteImage(card.backdrop, Modifier.fillMaxSize(), "w780", card.name)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))))
                if (card.logo != null) {
                    RemoteImage(card.logo, Modifier.align(Alignment.BottomStart).padding(12.pt).width(260.pt).height(84.pt), showInitials = false, fit = true, alignment = Alignment.BottomStart)
                } else Text(card.name, style = Typo.title2, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(12.pt))
            }
            Column {
                Text(card.name, style = Typo.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(card.kind.word, card.line).joinToString(" · "), style = Typo.footnote, color = p.textMuted, maxLines = 1)
            }
        }
    }
}

