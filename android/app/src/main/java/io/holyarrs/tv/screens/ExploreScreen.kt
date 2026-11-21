package io.holyarrs.tv.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.holyarrs.tv.app.LocalApp
import io.holyarrs.tv.core.Details
import io.holyarrs.tv.core.ExploreHead
import io.holyarrs.tv.core.ExploreNew
import io.holyarrs.tv.core.ExploreRow
import io.holyarrs.tv.core.ExploreTail
import io.holyarrs.tv.core.Item
import io.holyarrs.tv.core.RailDay
import io.holyarrs.tv.core.Radii
import io.holyarrs.tv.core.Space
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.fmt
import io.holyarrs.tv.core.parseDay
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.ui.ErrorCard
import io.holyarrs.tv.ui.Focusable
import io.holyarrs.tv.ui.LoadingView
import io.holyarrs.tv.ui.Pill
import io.holyarrs.tv.ui.PillKind
import io.holyarrs.tv.ui.PosterCard
import io.holyarrs.tv.ui.RemoteImage
import io.holyarrs.tv.ui.SegmentBar
import io.holyarrs.tv.ui.Shelf
import io.holyarrs.tv.ui.WideCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ExploreMode { EXPLORE, POPULAR }

/** Explore and New & Popular: the type switch, the hero and server-built rows. */
@Composable
fun ExploreScreen(mode: ExploreMode) {
    val app = LocalApp.current
    var type by remember { mutableStateOf("all") }
    var hero by remember { mutableStateOf<List<Item>>(emptyList()) }
    var rows by remember { mutableStateOf<List<ExploreRow>>(emptyList()) }
    var rail by remember { mutableStateOf<List<RailDay>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    suspend fun reload() {
        loaded = false; error = null; page = 0; more = false
        try {
            when (mode) {
                ExploreMode.EXPLORE -> {
                    var head = app.api.get<ExploreHead>("/explore/head?type=$type")
                    var tries = 0
                    // The server may still be building the rows: ask again like the web does.
                    while (head.building == true && head.head.isNullOrEmpty() && tries < 40) {
                        delay(3000)
                        head = app.api.get("/explore/head?type=$type")
                        tries++
                    }
                    hero = head.hero.orEmpty()
                    rows = head.head.orEmpty().filter { it.type != "top_services" }
                    rail = emptyList()
                    more = true
                }
                ExploreMode.POPULAR -> {
                    val n = app.api.get<ExploreNew>("/explore/new?type=$type&page=0")
                    hero = n.hero.orEmpty()
                    rows = n.rows.orEmpty()
                    rail = n.rail.orEmpty()
                    more = n.more ?: false
                }
            }
        } catch (e: Exception) { error = e.message }
        loaded = true
    }

    suspend fun loadMore() {
        if (!more || loadingMore) return
        loadingMore = true
        try {
            when (mode) {
                ExploreMode.EXPLORE -> {
                    val t = app.api.get<ExploreTail>("/explore/tail?type=$type&page=$page")
                    rows = rows + t.rows.orEmpty().filter { it.type != "top_services" }
                    more = t.more ?: false
                }
                ExploreMode.POPULAR -> {
                    val n = app.api.get<ExploreNew>("/explore/new?type=$type&page=${page + 1}")
                    rows = rows + n.rows.orEmpty()
                    more = (n.more ?: false) && page < 19
                }
            }
            page++
            if (app.demo) more = false
        } catch (e: Exception) { more = false }
        loadingMore = false
    }

    LaunchedEffect(type, app.api) { reload() }
    // Reaching the end loads the next page, like the web's infinite scroll.
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - 2 && loaded) loadMore() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(vertical = 50.pt),
        verticalArrangement = Arrangement.spacedBy(28.pt),
    ) {
        item(key = "types") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SegmentBar(
                    listOf(
                        Triple("all", "All", Icons.Outlined.GridView),
                        Triple("movies", "Movies", Icons.Outlined.Movie),
                        Triple("shows", "TV shows", Icons.Outlined.Tv),
                    ),
                    type, { type = it },
                )
            }
        }
        if (hero.isNotEmpty()) item(key = "hero-$type") { HeroCarousel(hero, Modifier.padding(horizontal = Space.gutter)) }
        if (rail.isNotEmpty()) item(key = "rail") { RailShelf(rail) }
        itemsIndexed(rows, key = { i, r -> "$i-${r.id}" }) { _, row -> RowView(row) }
        when {
            error != null -> item { ErrorCard(error!!) { scope.launch { reload() } } }
            !loaded || more -> item { LoadingView() }
        }
    }
}

@Composable
private fun RowView(row: ExploreRow) {
    if (row.items.isEmpty()) return
    Shelf(row.title ?: "", row.subtitle) {
        if (row.ranked) {
            itemsIndexed(row.items, key = { i, it -> "$i-${it.key}" }) { i, item ->
                PosterCard(item.name, null, item.poster, item.route, width = 230.pt, rank = item.rank ?: (i + 1))
            }
        } else {
            itemsIndexed(row.items, key = { i, it -> "$i-${it.key}" }) { _, item -> WideCard(item) }
        }
    }
}

@Composable
private fun RailShelf(rail: List<RailDay>) {
    val flat = rail.flatMap { d -> d.items.map { d.day to it } }
    Shelf("This week") {
        itemsIndexed(flat, key = { i, _ -> i }) { _, (day, item) ->
            WideCard(item, width = 440.pt, subtitle = listOfNotNull(parseDay(day)?.fmt("EEE d MMM"), item.what).joinToString(" · "))
        }
    }
}

/**
 * The big hero: backdrop, logo, "year · genres", overview, More info and +.
 * It turns every 9 s, but holds still while focus is inside it.
 */
@Composable
fun HeroCarousel(items: List<Item>, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    var index by remember { mutableStateOf(0) }
    var focusInside by remember { mutableStateOf(false) }
    val logos = remember { mutableStateMapOf<String, String>() }
    var adding by remember { mutableStateOf<Item?>(null) }
    val item = items[index.coerceAtMost(items.size - 1)]

    LaunchedEffect(item.key) {
        if (logos[item.key] == null && item.card_logo == null) {
            runCatching { app.api.get<Details>("/metadata/${item.kind.raw}/${item.tmdb_id.value}") }.getOrNull()?.logo?.let { logos[item.key] = it }
        }
    }
    LaunchedEffect(focusInside) {
        if (focusInside || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(9000)
            index = (index + 1) % items.size
        }
    }

    Box(
        modifier.fillMaxWidth().height(800.pt).clip(RoundedCornerShape(Radii.panel * 1.4f))
            .onFocusChanged { focusInside = it.hasFocus },
    ) {
        Crossfade(item, animationSpec = tween(900), label = "hero") { it ->
            RemoteImage(it.backdrop, Modifier.fillMaxSize(), "w1280", it.name)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.5f to Color.Transparent)))
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 56.pt, end = 56.pt, top = 56.pt, bottom = 64.pt),
            verticalArrangement = Arrangement.spacedBy(9.pt),
        ) {
            val logo = logos[item.key] ?: item.card_logo
            if (logo != null) {
                RemoteImage(logo, Modifier.width(720.pt).height(130.pt), showInitials = false, fit = true, alignment = Alignment.BottomStart)
            } else Text(item.name, style = Typo.geist(44f, FontWeight.Bold), color = Color.White)
            Text(
                (listOfNotNull(item.year?.toString()) + item.genres.orEmpty().take(3)).joinToString(" · "),
                style = Typo.geist(17f, FontWeight.SemiBold), color = Color.White,
            )
            item.overview?.let {
                Text(it, style = Typo.geist(15f), color = Color.White.copy(alpha = 0.9f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 880.pt))
            }
            Row(Modifier.fillMaxWidth().focusGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.pt)) {
                Pill("More info", { app.open(item.route) }, kind = PillKind.GLASS)
                Pill(null, { adding = item }, kind = PillKind.GLASS, round = true, icon = if (item.in_library == true) Icons.Filled.Check else Icons.Filled.Add)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.pt), verticalAlignment = Alignment.CenterVertically) {
                    items.indices.forEach { i ->
                        Focusable({ index = i }, onFocus = { if (it) index = i }) { focused ->
                            val w by animateDpAsState(if (i == index) 34.pt else 12.pt, label = "dot")
                            Box(
                                Modifier.width(w).height(12.pt).clip(CircleShape)
                                    .background(if (i == index) Color.White else Color.White.copy(alpha = if (focused) 0.8f else 0.4f)),
                            )
                        }
                    }
                }
            }
        }
    }
    adding?.let { AddSheet(it.kind, it.tmdb_id.value, it.name) { adding = null } }
}

