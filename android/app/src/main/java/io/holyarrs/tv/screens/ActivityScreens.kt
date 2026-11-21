package io.holyarrs.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.holyarrs.tv.app.LocalApp
import io.holyarrs.tv.core.Ask
import io.holyarrs.tv.core.Asks
import io.holyarrs.tv.core.CalendarResponse
import io.holyarrs.tv.core.CalendarRow
import io.holyarrs.tv.core.DownloadRow
import io.holyarrs.tv.core.DownloadsView
import io.holyarrs.tv.core.PersonPageData
import io.holyarrs.tv.core.Placeholder
import io.holyarrs.tv.core.Radii
import io.holyarrs.tv.core.Route
import io.holyarrs.tv.core.Space
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.fmt
import io.holyarrs.tv.core.parseDay
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.ui.EmptyCard
import io.holyarrs.tv.ui.ErrorCard
import io.holyarrs.tv.ui.Focusable
import io.holyarrs.tv.ui.FocusableText
import io.holyarrs.tv.ui.LoadingView
import io.holyarrs.tv.ui.Pill
import io.holyarrs.tv.ui.PosterCard
import io.holyarrs.tv.ui.RemoteImage
import io.holyarrs.tv.ui.SegmentBar
import io.holyarrs.tv.ui.Shelf
import io.holyarrs.tv.ui.bytes
import io.holyarrs.tv.ui.lift
import io.holyarrs.tv.ui.ring
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Requests

private fun inTab(a: Ask, t: String) = when (t) {
    "waiting" -> a.state == "asked"
    "processing" -> a.state == "approved"
    else -> a.state == "available" || a.state == "turned_down"
}

/** Requests: Waiting / Processing / Done, like the web's tabs. */
@Composable
fun RequestsScreen() {
    val app = LocalApp.current
    val p = Theme.p
    var asks by remember { mutableStateOf<List<Ask>>(emptyList()) }
    var tab by remember { mutableStateOf("waiting") }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try { asks = app.api.get<Asks>("/asks?limit=200").asks; error = null } catch (e: Exception) { error = e.message }
        loaded = true
    }
    LaunchedEffect(app.libraryVersion, app.api) { load() }

    val list = asks.filter { inTab(it, tab) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = 60.pt),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { Text("Requests", style = Typo.largeTitle, color = p.text, modifier = Modifier.padding(bottom = 17.pt)) }
        item {
            SegmentBar(
                listOf("waiting" to "Waiting", "processing" to "Processing", "done" to "Done").map { (k, l) ->
                    Triple(k, "$l  ${asks.count { a -> inTab(a, k) }}", null)
                },
                tab, { tab = it }, Modifier.padding(bottom = 17.pt),
            )
        }
        when {
            error != null -> item { ErrorCard(error!!) { scope.launch { load() } } }
            !loaded -> item { LoadingView() }
            list.isEmpty() -> item { EmptyCard(Icons.Filled.Check, "You're all caught up.", "New requests show up here.") }
            else -> items(list, key = { it.id }) { AskRow(it) }
        }
    }
}

@Composable
private fun AskRow(ask: Ask) {
    val app = LocalApp.current
    val p = Theme.p
    Focusable({ ask.tmdb_id?.value?.let { app.open(Route.Title(ask.kind, it)) } }) { focused ->
        Row(
            Modifier.fillMaxWidth().lift(focused, Radii.card, 1.01f).clip(RoundedCornerShape(Radii.card))
                .background(if (focused) p.surface2 else Color.Transparent).padding(horizontal = 13.pt, vertical = 9.pt),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.pt),
        ) {
            RemoteImage(ask.poster, Modifier.width(80.pt).height(120.pt).clip(RoundedCornerShape(Radii.sm)), "w185", ask.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.pt)) {
                Text(listOfNotNull(ask.name, ask.year?.let { "($it)" }).joinToString(" "), style = Typo.headline, color = p.text)
                Text(
                    listOfNotNull(ask.kind.word, ask.season_words, ask.asked_by?.name?.let { "asked by $it" }).joinToString(" · "),
                    style = Typo.footnote, color = p.textMuted,
                )
                (ask.step?.text ?: ask.problem)?.let { Text(it, style = Typo.footnote, color = p.textMuted) }
            }
            StateChip(ask.state, ask.state_label ?: ask.state)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

@Composable
fun StateChip(state: String, label: String, modifier: Modifier = Modifier) {
    val p = Theme.p
    val color = when (state) {
        "available", "done", "seeding" -> p.ok
        "turned_down", "failed" -> p.fail
        "approved", "downloading" -> p.info
        else -> p.wait
    }
    Row(
        modifier.clip(CircleShape).background(p.surface2).padding(horizontal = 8.pt, vertical = 4.pt),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.pt),
    ) {
        Box(Modifier.size(12.pt).clip(CircleShape).background(color))
        Text(label, style = Typo.subhead, color = p.text, maxLines = 1)
    }
}

// Calendar

/** Calendar: a week at a time, with All / Movies / TV shows. */
@Composable
fun CalendarScreen() {
    val app = LocalApp.current
    val p = Theme.p
    var weekStart by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    var type by remember { mutableStateOf("all") }
    var rows by remember { mutableStateOf<List<CalendarRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val days = (0L until 7L).map { weekStart.plusDays(it).toString() }

    LaunchedEffect(weekStart, type, app.api) {
        loaded = false
        var r = runCatching { app.api.get<CalendarResponse>("/calendar?from=${days.first()}&to=${days.last()}&titles=$type") }.getOrNull()?.dates.orEmpty()
        // Demo data is from a fixed week: show it on this week's days.
        if (app.demo) r = r.mapIndexed { i, x -> x.copy(date = days[i % 7]) }
        rows = r
        loaded = true
    }

    val end = weekStart.plusDays(6)
    val range = "${weekStart.fmt("MMM d")} – ${end.fmt(if (end.month == weekStart.month) "d" else "MMM d")}"

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 60.pt), verticalArrangement = Arrangement.spacedBy(17.pt)) {
        item { Text("Calendar", style = Typo.largeTitle, color = p.text, modifier = Modifier.padding(horizontal = Space.gutter)) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter).focusGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.pt)) {
                Pill(null, { weekStart = weekStart.minusDays(7) }, round = true, icon = Icons.Filled.ChevronLeft)
                Text(range, style = Typo.title2, color = p.text)
                Pill(null, { weekStart = weekStart.plusDays(7) }, round = true, icon = Icons.Filled.ChevronRight)
                Spacer(Modifier.weight(1f))
                SegmentBar(listOf(Triple("all", "All", null), Triple("movies", "Movies", null), Triple("shows", "TV shows", null)), type, { type = it })
            }
        }
        when {
            !loaded -> item { LoadingView() }
            rows.isEmpty() -> item {
                EmptyCard(Icons.Outlined.CalendarMonth, "Nothing you follow here", "No dates in the Library this week.", Modifier.padding(horizontal = Space.gutter))
            }
            else -> for (day in days) {
                val dayRows = rows.filter { it.day == day }
                if (dayRows.isNotEmpty()) item(key = day) {
                    Shelf(dayLabel(day)) { items(dayRows, key = { it.key }) { CalendarCard(it) } }
                }
            }
        }
    }
}

private fun dayLabel(s: String): String {
    val d = parseDay(s) ?: return s
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> d.fmt("EEEE d MMMM")
    }
}

@Composable
private fun CalendarCard(row: CalendarRow) {
    val app = LocalApp.current
    val p = Theme.p
    val what = when (row.kind) {
        "episode" -> "S${row.season ?: 0}E${row.episode ?: 0}" + (row.episode_name?.let { " · $it" } ?: "")
        "cinema" -> "In cinemas"
        "digital" -> "Digital release"
        "physical" -> "Disc release"
        else -> row.kind.replaceFirstChar { it.uppercase() }
    }
    Focusable({ row.title_id?.let { app.open(Route.LibraryTitle(it)) } }) { focused ->
        Column(Modifier.width(470.pt).lift(focused), verticalArrangement = Arrangement.spacedBy(5.pt)) {
            Box(Modifier.width(470.pt).height(264.pt).clip(RoundedCornerShape(Radii.card)).ring(focused)) {
                RemoteImage(row.still ?: row.banner ?: row.backdrop, Modifier.fillMaxSize(), "w780", row.name)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
                row.logo?.let {
                    RemoteImage(it, Modifier.align(Alignment.BottomStart).padding(10.pt).width(200.pt).height(70.pt), showInitials = false, fit = true, alignment = Alignment.BottomStart)
                }
            }
            Text(row.name, style = Typo.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(what, style = Typo.footnote, color = p.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Downloads

private fun inDownloadTab(r: DownloadRow, tab: String) = when (tab) {
    "waiting" -> r.phase == "waiting"
    "progress" -> r.phase in setOf("downloading", "stalled", "paused", "importing", "waiting_to_import")
    "problems" -> r.phase in setOf("failed", "vanished", "needs_a_decision")
    "done" -> r.phase == "done"
    else -> true
}

/** Downloads: the list with progress, status and size (read-only on the TV). */
@Composable
fun DownloadsScreen() {
    val app = LocalApp.current
    val p = Theme.p
    var rows by remember { mutableStateOf<List<DownloadRow>>(emptyList()) }
    var tab by remember { mutableStateOf("all") }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try { rows = app.api.get<DownloadsView>("/downloads/view?limit=100").downloads.orEmpty(); error = null } catch (e: Exception) { error = e.message }
        loaded = true
    }
    LaunchedEffect(app.api) {
        load()
        while (!app.demo) { delay(5000); load() }
    }

    val list = rows.filter { inDownloadTab(it, tab) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.gutter, vertical = 60.pt)) {
        item { Text("Downloads", style = Typo.largeTitle, color = p.text, modifier = Modifier.padding(bottom = 15.pt)) }
        item {
            SegmentBar(
                listOf(Triple("all", "All", null), Triple("waiting", "Waiting", null), Triple("progress", "In progress", null), Triple("problems", "Problems", null), Triple("done", "Done", null)),
                tab, { tab = it }, Modifier.padding(bottom = 15.pt),
            )
        }
        when {
            error != null -> item { ErrorCard(error!!) { scope.launch { load() } } }
            !loaded -> item { LoadingView() }
            list.isEmpty() -> item { EmptyCard(Icons.Outlined.Download, "Nothing here", "Downloads show up here.") }
            else -> items(list, key = { it.key }) { DownloadLine(it) }
        }
    }
}

@Composable
private fun DownloadLine(row: DownloadRow) {
    val app = LocalApp.current
    val p = Theme.p
    val title = row.title?.takeIf { it.isNotEmpty() }
    Focusable({ row.title_id?.let { app.open(Route.LibraryTitle(it)) } }) { focused ->
        Row(
            Modifier.fillMaxWidth().lift(focused, Radii.card, 1.01f).clip(RoundedCornerShape(Radii.card))
                .background(if (focused) p.surface2 else Color.Transparent).padding(horizontal = 11.pt, vertical = 7.pt),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.pt),
        ) {
            Box(
                Modifier.width(46.pt).height(64.pt).clip(RoundedCornerShape(3.pt)).background(Brush.verticalGradient(Placeholder.colors(row.name ?: ""))),
                contentAlignment = Alignment.Center,
            ) { Text(Placeholder.initials(title ?: row.name ?: ""), style = Typo.footnote, color = Color.White.copy(alpha = 0.85f)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.pt)) {
                Text(if (title != null) title + (row.year?.let { " ($it)" } ?: "") else row.name ?: "", style = Typo.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.name ?: "", style = Typo.footnote, color = p.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val pr = (row.progress ?: 0.0).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.width(220.pt).height(30.pt).clip(CircleShape).background(p.surface3), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pr).background(p.ok.copy(alpha = 0.75f)))
                Text("${(pr * 100).toInt()}%", style = Typo.caption, color = Color.White, modifier = Modifier.padding(start = 6.pt))
            }
            Box(Modifier.width(230.pt)) { StateChip(row.state ?: "", row.label ?: (row.phase ?: "").replaceFirstChar { it.uppercase() }) }
            Text(bytes(row.size ?: 0), style = Typo.subhead, color = p.textMuted, textAlign = TextAlign.End, modifier = Modifier.width(140.pt))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

// Person

@Composable
fun PersonScreen(personId: String, name: String) {
    val app = LocalApp.current
    val p = Theme.p
    var page by remember { mutableStateOf<PersonPageData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(personId) {
        try { page = app.api.get<PersonPageData>("/people/$personId?role=all&sort=popular&page_size=40") } catch (e: Exception) { error = e.message }
    }
    val pg = page
    val full = GridItemSpan(7)
    LazyVerticalGrid(
        GridCells.Fixed(7), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = 60.pt),
        horizontalArrangement = Arrangement.spacedBy(30.pt),
        verticalArrangement = Arrangement.spacedBy(25.pt),
    ) {
        when {
            pg != null -> {
                item(span = { full }) {
                    Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(25.pt)) {
                        RemoteImage(pg.person.profile, Modifier.width(300.pt).height(450.pt).clip(RoundedCornerShape(Radii.panel)), "w300", pg.person.name)
                        Column(verticalArrangement = Arrangement.spacedBy(9.pt)) {
                            Text(pg.person.name, style = Typo.largeTitle, color = p.text)
                            Text(
                                listOfNotNull(pg.person.department, pg.person.birthday?.let { "Born $it" }, pg.person.birthplace).joinToString(" · "),
                                style = Typo.callout, color = p.textMuted,
                            )
                            pg.person.biography?.takeIf { it.isNotEmpty() }?.let { FocusableText(it, 7, Modifier.offset(x = (-9).pt)) }
                        }
                    }
                }
                val credits = pg.movies.orEmpty().mapNotNull { it.item }
                if (credits.isNotEmpty()) {
                    item(span = { full }) { Text("Known for", style = Typo.title2, color = p.text, modifier = Modifier.padding(top = 10.pt)) }
                    itemsIndexed(credits, key = { i, it -> "$i-${it.key}" }) { _, it ->
                        PosterCard(it.name, it.year, it.poster, it.route, width = 200.pt, mark = if (it.in_library == true) "have" else null)
                    }
                }
            }
            error != null -> item(span = { full }) { ErrorCard(error!!) }
            else -> item(span = { full }) { LoadingView() }
        }
    }
}

