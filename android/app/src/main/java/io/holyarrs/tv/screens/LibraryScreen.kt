package io.holyarrs.tv.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
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
import io.holyarrs.tv.app.LocalApp
import io.holyarrs.tv.core.Kind
import io.holyarrs.tv.core.LibraryTitle
import io.holyarrs.tv.core.Route
import io.holyarrs.tv.core.Space
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.TitlesGrid
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.ui.ChoiceDialog
import io.holyarrs.tv.ui.EmptyCard
import io.holyarrs.tv.ui.ErrorCard
import io.holyarrs.tv.ui.LoadingView
import io.holyarrs.tv.ui.Pill
import io.holyarrs.tv.ui.PosterCard
import io.holyarrs.tv.ui.SearchField
import io.holyarrs.tv.ui.SegmentBar
import kotlinx.coroutines.launch

private val SORTS = listOf("added" to "Recently added", "name" to "Name", "year" to "Year", "size" to "Size")

/** Movies / TV shows: the Library poster wall with search, sort and the have/missing filter. */
@Composable
fun LibraryScreen(kind: Kind) {
    val app = LocalApp.current
    val p = Theme.p
    var titles by remember { mutableStateOf<List<LibraryTitle>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("added") }
    var show by remember { mutableStateOf("all") }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            titles = app.api.get<TitlesGrid>("/titles?grid=1").titles
            error = null
        } catch (e: Exception) { error = e.message }
        loaded = true
    }
    LaunchedEffect(app.libraryVersion, app.api) { load() }

    var list = titles.filter { it.kind == kind }
    if (query.isNotEmpty()) list = list.filter { it.name.contains(query, ignoreCase = true) }
    list = when (show) {
        "have" -> list.filter { it.status == "have" }
        "missing" -> list.filter { it.status != "have" }
        else -> list
    }
    list = when (sort) {
        "name" -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        "year" -> list.sortedByDescending { it.year ?: 0 }
        "size" -> list.sortedByDescending { it.size_bytes ?: 0 }
        else -> list.sortedByDescending { it.added_at ?: "" }
    }
    val noun = if (kind == Kind.MOVIE) "movies" else "shows"
    val full = GridItemSpan(7)

    LazyVerticalGrid(
        GridCells.Fixed(7),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = 50.pt),
        horizontalArrangement = Arrangement.spacedBy(30.pt),
        verticalArrangement = Arrangement.spacedBy(25.pt),
    ) {
        item(span = { full }) {
            Row(Modifier.fillMaxWidth().focusGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.pt)) {
                SearchField(query, { query = it }, "Search $noun", Modifier.widthIn(max = 760.pt).weight(1f)) {
                    Text("${list.size} $noun", style = Typo.footnote, color = p.textMuted)
                }
                Spacer(Modifier.weight(0.2f))
                SegmentBar(listOf(Triple("all", "All", null), Triple("have", "Have", null), Triple("missing", "Missing", null)), show, { show = it })
                Pill("Sort & filter", { sorting = true }, icon = Icons.Outlined.Tune)
            }
        }
        when {
            error != null -> item(span = { full }) { ErrorCard(error!!) { scope.launch { load() } } }
            !loaded -> item(span = { full }) { LoadingView() }
            list.isEmpty() -> item(span = { full }) {
                EmptyCard(if (kind == Kind.MOVIE) Icons.Outlined.Movie else Icons.Outlined.Tv, "Nothing here yet", "Add $noun from Explore or Search.")
            }
            else -> items(list, key = { it.id }) { t ->
                PosterCard(t.name, t.year, t.poster, Route.LibraryTitle(t.id), width = 200.pt, mark = if (t.status == "have") null else t.status)
            }
        }
    }
    if (sorting) ChoiceDialog("Sort by", SORTS, sort, { sort = it }) { sorting = false }
}

/** `/library/{id}` pages: look the title up, then show the regular title page. */
@Composable
fun LibraryTitleResolver(id: Int) {
    val app = LocalApp.current
    var title by remember { mutableStateOf<LibraryTitle?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        try { title = app.api.get<LibraryTitle>("/titles/$id") } catch (e: Exception) { error = e.message }
    }
    val t = title
    when {
        t?.tmdb != null -> TitleScreen(t.kind, t.tmdb!!)
        error != null -> ErrorCard(error!!)
        else -> LoadingView()
    }
}
