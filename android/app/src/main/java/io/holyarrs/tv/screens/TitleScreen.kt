package io.holyarrs.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.holyarrs.tv.app.LocalApp
import io.holyarrs.tv.core.Details
import io.holyarrs.tv.core.Episode
import io.holyarrs.tv.core.Episodes
import io.holyarrs.tv.core.Extras
import io.holyarrs.tv.core.Follow
import io.holyarrs.tv.core.Kind
import io.holyarrs.tv.core.Radii
import io.holyarrs.tv.core.Route
import io.holyarrs.tv.core.Space
import io.holyarrs.tv.core.TMDBSeason
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.ui.ErrorCard
import io.holyarrs.tv.ui.Focusable
import io.holyarrs.tv.ui.FocusableText
import io.holyarrs.tv.ui.LoadingView
import io.holyarrs.tv.ui.Pill
import io.holyarrs.tv.ui.PillKind
import io.holyarrs.tv.ui.RemoteImage
import io.holyarrs.tv.ui.SegmentBar
import io.holyarrs.tv.ui.Shelf
import io.holyarrs.tv.ui.StatusDot
import io.holyarrs.tv.ui.WideCard
import io.holyarrs.tv.ui.lift
import io.holyarrs.tv.ui.ring
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * `/title/{kind}/{tmdb}`: backdrop hero with logo, tagline and actions, then the
 * facts line, overview, credits, seasons, cast and More like this.
 */
@Composable
fun TitleScreen(kind: Kind, tmdb: String) {
    val app = LocalApp.current
    var d by remember { mutableStateOf<Details?>(null) }
    var extras by remember { mutableStateOf<Extras?>(null) }
    var following by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf(1) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun loadEpisodes(det: Details) {
        val id = det.title_id
        episodes = runCatching { app.api.get<Episodes>("/titles/$id/episodes?season=$season").episodes }.getOrNull().takeIf { id != null }
            ?: runCatching { app.api.get<Episodes>("/metadata/show/$tmdb/seasons/$season").episodes.map { it.copy(season = season) } }.getOrNull()
            ?: emptyList()
    }

    suspend fun load() {
        val api = app.api
        try {
            coroutineScope {
                val det = async { api.get<Details>("/metadata/${kind.raw}/$tmdb") }
                val ex = async { runCatching { api.get<Extras>("/extras/${kind.raw}/$tmdb") }.getOrNull() }
                val details = det.await()
                d = details
                extras = ex.await()
                val followPath = details.title_id?.let { "/titles/$it/follow" } ?: "/metadata/${kind.raw}/$tmdb/follow"
                following = runCatching { api.get<Follow>(followPath).following }.getOrNull() ?: false
                if (kind == Kind.SHOW && episodes.isEmpty()) {
                    season = details.seasons?.firstOrNull { it.number > 0 }?.number ?: 1
                    loadEpisodes(details)
                }
                if (app.startScreen == "add-sheet" && !adding && details.title_id == null && reload == 0) adding = true
            }
        } catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(kind, tmdb, reload) { load() }

    val det = d
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.pt), verticalArrangement = Arrangement.spacedBy(25.pt)) {
        when {
            det != null -> {
                item(key = "hero") {
                    Hero(det, extras, kind, following, onAdd = { adding = true }) { now ->
                        following = now
                    }
                }
                item(key = "facts") { Facts(det, extras, kind) }
                val seasons = det.seasons?.filter { it.number > 0 }.orEmpty()
                if (kind == Kind.SHOW && seasons.isNotEmpty()) item(key = "seasons") {
                    SeasonsBlock(seasons, season, episodes, det.in_library == true) {
                        season = it
                        scope.launch { loadEpisodes(det) }
                    }
                }
                val actors = det.people.orEmpty().filter { it.role == "actor" }.take(20)
                if (actors.isNotEmpty()) item(key = "cast") {
                    Shelf("Cast") {
                        items(actors, key = { it.key }) { person -> CastCard(person.name, person.character, person.profile) { person.pid?.value?.let { app.open(Route.Person(it, person.name)) } } }
                    }
                }
                val similar = det.similar.orEmpty()
                if (similar.isNotEmpty()) item(key = "similar") {
                    Shelf("More like this") {
                        itemsIndexed(similar, key = { i, it -> "$i-${it.key}" }) { _, it -> WideCard(it, width = 470.pt) }
                    }
                }
            }
            error != null -> item { ErrorCard(error!!, Modifier.padding(top = 100.pt)) { scope.launch { load() } } }
            else -> item { LoadingView(Modifier.height(350.pt)) }
        }
    }
    if (adding) AddSheet(kind, tmdb, det?.name ?: "") {
        adding = false
        reload++
    }
}

@Composable
private fun Hero(d: Details, extras: Extras?, kind: Kind, following: Boolean, onAdd: () -> Unit, onFollow: (Boolean) -> Unit) {
    Box(
        Modifier.padding(start = Space.gutter, end = Space.gutter, top = 50.pt).fillMaxWidth().height(900.pt)
            .clip(RoundedCornerShape(Radii.panel * 1.4f)),
    ) {
        RemoteImage(extras?.backgrounds?.firstOrNull() ?: d.backdrop, Modifier.fillMaxSize(), "w1280", d.name)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.6f), 0.5f to Color.Transparent)))
        Column(Modifier.align(Alignment.BottomStart).padding(30.pt), verticalArrangement = Arrangement.spacedBy(11.pt)) {
            val logo = extras?.logo ?: d.logo
            if (logo != null) {
                RemoteImage(logo, Modifier.width(780.pt).height(170.pt), showInitials = false, fit = true, alignment = Alignment.BottomStart)
            } else Text(d.name, style = Typo.geist(48f, FontWeight.Bold), color = Color.White)
            d.tagline?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = Typo.geist(18f).copy(fontStyle = FontStyle.Italic), color = Color.White.copy(alpha = 0.92f), modifier = Modifier.widthIn(max = 820.pt))
            }
            Actions(d, kind, following, onAdd, onFollow)
        }
    }
}

@Composable
private fun Actions(d: Details, kind: Kind, following: Boolean, onAdd: () -> Unit, onFollow: (Boolean) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val manage = app.me?.canManage ?: app.demo
    Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.pt)) {
        val ask = d.ask
        when {
            d.in_library == true -> {
                Pill("In library", {}, kind = PillKind.PRIMARY, icon = Icons.Filled.Check)
                val id = d.title_id
                if (id != null && manage) Pill("Open in Jellyfin", { scope.launch { app.openInJellyfin(id) } }, kind = PillKind.GLASS, icon = Icons.Filled.PlayArrow)
            }
            ask != null && ask.state != "turned_down" ->
                Pill(ask.state_label ?: "Asked", {}, kind = PillKind.PRIMARY, icon = Icons.Filled.Schedule)
            d.never_add != true ->
                Pill(if (manage) "Add to library" else "Request", onAdd, kind = PillKind.PRIMARY, icon = Icons.Filled.Add)
        }
        Pill(
            if (following) "Following" else "Follow",
            {
                if (busy) return@Pill
                busy = true
                scope.launch {
                    val path = d.title_id?.let { "/titles/$it/follow" } ?: "/metadata/${kind.raw}/${d.tmdb_id.value}/follow"
                    try {
                        if (following) app.api.delete(path) else app.api.put(path)
                        onFollow(!following)
                        app.show(if (!following) "Following ${d.name}." else "Stopped following ${d.name}.")
                    } catch (e: Exception) { app.show(e.message ?: "Couldn't change it.") }
                    busy = false
                }
            },
            kind = PillKind.GLASS, icon = if (following) Icons.Filled.Notifications else Icons.Outlined.Notifications,
        )
    }
}

@Composable
private fun Facts(d: Details, extras: Extras?, kind: Kind) {
    val p = Theme.p
    Row(Modifier.padding(horizontal = Space.gutter).focusGroup(), horizontalArrangement = Arrangement.spacedBy(40.pt)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.pt)) {
            FactsLine(d, extras, kind)
            d.overview?.takeIf { it.isNotEmpty() }?.let { FocusableText(it, 5, Modifier.offset(x = (-9).pt)) }
        }
        Column(Modifier.width(560.pt), verticalArrangement = Arrangement.spacedBy(7.pt)) {
            val people = d.people.orEmpty()
            val directors = people.filter { it.role == "director" || it.role == "creator" }.map { it.name }
            val stars = people.filter { it.role == "actor" }.take(3).map { it.name }
            if (directors.isNotEmpty()) Credit(if (kind == Kind.SHOW) "Created by" else "Directed by", directors.joinToString(", "))
            if (stars.isNotEmpty()) Credit("Starring", stars.joinToString(", "))
            d.genres?.takeIf { it.isNotEmpty() }?.let { Credit("Genres", it.joinToString(", ")) }
            d.studios?.firstOrNull()?.let { Credit(if (kind == Kind.SHOW) "Network" else "Studio", it) }
        }
    }
}

@Composable
private fun Credit(label: String, value: String) {
    val p = Theme.p
    Row(horizontalArrangement = Arrangement.spacedBy(6.pt)) {
        Text(label, style = Typo.subhead, color = p.textMuted)
        Text(value, style = Typo.subhead, color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FactsLine(d: Details, extras: Extras?, kind: Kind) {
    val p = Theme.p
    val style = Typo.geist(15f, FontWeight.Medium)
    val bits = mutableListOf<@Composable () -> Unit>()
    d.year?.let { bits += { Text("$it", style = style, color = p.textMuted) } }
    if (kind == Kind.SHOW) {
        val n = d.seasons?.count { it.number > 0 } ?: 0
        if (n > 0) bits += { Text("$n season${if (n == 1) "" else "s"}", style = style, color = p.textMuted) }
    }
    if (kind == Kind.MOVIE) d.runtime_minutes?.takeIf { it > 0 }?.let { m ->
        bits += { Text(if (m >= 60) "${m / 60} hr ${m % 60} min" else "$m min", style = style, color = p.textMuted) }
    }
    d.rating?.takeIf { it.isNotEmpty() }?.let { r ->
        bits += {
            Text(r, style = Typo.footnote, color = p.textMuted, modifier = Modifier.border(0.75.dp, p.textMuted, RoundedCornerShape(2.5.pt)).padding(horizontal = 4.pt, vertical = 1.pt))
        }
    }
    for (s in extras?.scores.orEmpty()) {
        when (s.source) {
            "imdb" -> bits += {
                Row(horizontalArrangement = Arrangement.spacedBy(4.pt), verticalAlignment = Alignment.CenterVertically) {
                    Text("IMDb", style = Typo.geist(11.6f, FontWeight.Bold), color = Color.Black,
                        modifier = Modifier.clip(RoundedCornerShape(2.pt)).background(Color(0xFFF5C518)).padding(horizontal = 3.pt, vertical = 0.5.pt))
                    Text("%.1f".format(s.value), style = style, color = p.text)
                }
            }
            "rt_critics" -> bits += { Text("🍅 ${s.value.toInt()}% Critics", style = style, color = p.textMuted) }
            "rt_audience" -> bits += { Text("🍿 ${s.value.toInt()}% Audience", style = style, color = p.textMuted) }
            "metacritic" -> bits += { Text("${s.value.toInt()} Metacritic", style = style, color = p.textMuted) }
            "letterboxd" -> bits += { Text("%.1f Letterboxd".format(s.value), style = style, color = p.textMuted) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(7.pt), verticalAlignment = Alignment.CenterVertically) {
        bits.forEachIndexed { i, bit ->
            if (i > 0) Text("·", style = style, color = p.textFaint)
            bit()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeasonsBlock(seasons: List<TMDBSeason>, season: Int, episodes: List<Episode>, inLibrary: Boolean, onSeason: (Int) -> Unit) {
    val p = Theme.p
    Column(verticalArrangement = Arrangement.spacedBy(5.pt)) {
        Row(Modifier.padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(12.pt), verticalAlignment = Alignment.CenterVertically) {
            Text("Episodes", style = Typo.title2, color = p.text)
            SegmentBar(seasons.map { Triple(it.number, "Season ${it.number}", null) }, season, onSeason)
        }
        LazyRow(
            Modifier.focusRestorer().focusGroup(),
            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = 14.pt),
            horizontalArrangement = Arrangement.spacedBy(Space.shelfGap),
        ) {
            items(episodes, key = { it.stableID }) { EpisodeCard(it, inLibrary) }
        }
    }
}

@Composable
private fun EpisodeCard(ep: Episode, inLibrary: Boolean) {
    val p = Theme.p
    var open by remember { mutableStateOf(false) }
    Focusable({ open = true }) { focused ->
        Column(Modifier.width(440.pt).lift(focused), verticalArrangement = Arrangement.spacedBy(5.pt)) {
            Box(Modifier.width(440.pt).height(248.pt).clip(RoundedCornerShape(Radii.card)).ring(focused)) {
                RemoteImage(ep.still, Modifier.fillMaxSize(), "w780", ep.name ?: "")
                if (inLibrary) StatusDot(if (ep.have) "have" else "missing", Modifier.align(Alignment.TopEnd).padding(6.pt))
            }
            Text("${ep.num}. ${ep.name ?: "Episode ${ep.num}"}", style = Typo.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(ep.air_day, ep.runtime?.let { "$it min" }).joinToString(" · "), style = Typo.footnote, color = p.textMuted)
        }
    }
    if (open) Dialog({ open = false }) {
        Column(
            Modifier.widthIn(max = 1100.pt).clip(RoundedCornerShape(Radii.modal)).background(p.surface1).padding(30.pt),
            verticalArrangement = Arrangement.spacedBy(10.pt),
        ) {
            Text("S${ep.season ?: 0}E${ep.num} · ${ep.name ?: ""}", style = Typo.title2, color = p.text)
            Text(ep.overview ?: "No summary yet.", style = Typo.body, color = p.text)
        }
    }
}

@Composable
fun CastCard(name: String, character: String?, profile: String?, onClick: () -> Unit) {
    val p = Theme.p
    Focusable(onClick) { focused ->
        Column(Modifier.width(190.pt).lift(focused), verticalArrangement = Arrangement.spacedBy(4.pt)) {
            Box(Modifier.width(190.pt).height(250.pt).clip(RoundedCornerShape(Radii.card)).ring(focused)) {
                RemoteImage(profile, Modifier.fillMaxSize(), "w185", name)
            }
            Text(name, style = Typo.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(character ?: "", style = Typo.footnote, color = p.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
