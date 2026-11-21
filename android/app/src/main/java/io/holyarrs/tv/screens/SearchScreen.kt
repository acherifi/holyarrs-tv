package io.holyarrs.tv.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.holyarrs.tv.app.LocalApp
import io.holyarrs.tv.core.Item
import io.holyarrs.tv.core.Kind
import io.holyarrs.tv.core.MovieLookup
import io.holyarrs.tv.core.PeopleSearch
import io.holyarrs.tv.core.Person
import io.holyarrs.tv.core.Route
import io.holyarrs.tv.core.ShowLookup
import io.holyarrs.tv.core.Space
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.ui.EmptyCard
import io.holyarrs.tv.ui.LoadingView
import io.holyarrs.tv.ui.PosterCard
import io.holyarrs.tv.ui.SearchField
import io.holyarrs.tv.ui.SegmentBar
import io.holyarrs.tv.ui.Shelf
import io.holyarrs.tv.ui.WideCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** Search: the web's "Shows, movies and people" box with All / People / Movies / TV shows. */
@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val p = Theme.p
    var query by rememberSaveable { mutableStateOf("") }
    var scopeKind by remember { mutableStateOf("all") }
    var movies by remember { mutableStateOf<List<Item>>(emptyList()) }
    var shows by remember { mutableStateOf<List<Item>>(emptyList()) }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun run() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        val enc = URLEncoder.encode(q, "UTF-8")
        val api = app.api
        coroutineScope {
            val m = async { runCatching { api.get<MovieLookup>("/titles/lookup?term=$enc") }.getOrNull() }
            val s = async { runCatching { api.get<ShowLookup>("/shows/lookup?term=$enc") }.getOrNull() }
            val pp = async { runCatching { api.get<PeopleSearch>("/people/search?q=$enc&page=1") }.getOrNull() }
            movies = m.await()?.movies.orEmpty().mapNotNull { it.item(Kind.MOVIE) }.distinctBy { it.key }
            shows = s.await()?.shows.orEmpty().mapNotNull { it.item(Kind.SHOW) }.distinctBy { it.key }
            people = pp.await()?.people.orEmpty().take(20)
        }
        searching = false
        searched = q
    }
    LaunchedEffect(Unit) { if (app.demo && query.isEmpty()) { query = "alien"; run() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 60.pt), verticalArrangement = Arrangement.spacedBy(22.pt)) {
        item(key = "box") {
            Column(Modifier.fillMaxWidth().focusGroup(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.pt)) {
                SearchField(query, { query = it }, "Shows, movies and people", Modifier.width(1000.pt), onDone = { scope.launch { run() } })
                SegmentBar(
                    listOf(
                        Triple("all", "All", Icons.Outlined.GridView), Triple("people", "People", Icons.Outlined.Group),
                        Triple("movies", "Movies", Icons.Outlined.Movie), Triple("shows", "TV shows", Icons.Outlined.Tv),
                    ),
                    scopeKind, { scopeKind = it },
                )
            }
        }
        if (searching) item { LoadingView() }
        else if (searched.isNotEmpty()) {
            val total = movies.size + shows.size + people.size
            item { Text("$total results", style = Typo.callout, color = p.textMuted, modifier = Modifier.padding(horizontal = Space.gutter)) }
            if (scopeKind == "all") {
                val top = movies.take(3) + shows.take(2)
                if (top.isNotEmpty()) item(key = "top") { Shelf("Top matches") { items(top, key = { "t" + it.key }) { WideCard(it, width = 470.pt) } } }
            }
            if ((scopeKind == "all" || scopeKind == "people") && people.isNotEmpty()) item(key = "people") {
                Shelf("People") {
                    items(people, key = { "p" + it.key }) { person ->
                        PosterCard(person.name, null, person.profile, Route.Person(person.pid?.value ?: "", person.name), width = 230.pt)
                    }
                }
            }
            if ((scopeKind == "all" || scopeKind == "movies") && movies.isNotEmpty()) item(key = "movies") {
                Shelf("Movies") { items(movies, key = { "m" + it.key }) { WideCard(it, width = 470.pt) } }
            }
            if ((scopeKind == "all" || scopeKind == "shows") && shows.isNotEmpty()) item(key = "shows") {
                Shelf("TV shows") { items(shows, key = { "s" + it.key }) { WideCard(it, width = 470.pt) } }
            }
            if (total == 0) item { EmptyCard(Icons.Outlined.Search, "No results", "Try another word.", Modifier.padding(horizontal = Space.gutter)) }
        }
    }
}
