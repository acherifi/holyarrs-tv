package io.holyarrs.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.holyarrs.tv.app.LocalApp
import io.holyarrs.tv.core.Kind
import io.holyarrs.tv.core.KindDefaults
import io.holyarrs.tv.core.LibraryFolder
import io.holyarrs.tv.core.LibraryFolders
import io.holyarrs.tv.core.PickRule
import io.holyarrs.tv.core.PickRules
import io.holyarrs.tv.core.Radii
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.TitleDefaults
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.ui.LoadingView
import io.holyarrs.tv.ui.Pill
import io.holyarrs.tv.ui.PillKind
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The web's Add dialog (owners and managers) or Request dialog (everyone else).
 * Defaults follow lib/mediafiles/title-defaults.ts: saved title defaults first,
 * then the folder's own pick rules.
 */
@Composable
fun AddSheet(kind: Kind, tmdb: String, name: String, onClose: () -> Unit) {
    val app = LocalApp.current
    val p = Theme.p
    val manage = app.me?.canManage ?: app.demo
    val kindKey = if (kind == Kind.MOVIE) "movie" else "show"
    var folders by remember { mutableStateOf<List<LibraryFolder>>(emptyList()) }
    var rules by remember { mutableStateOf<List<PickRule>>(emptyList()) }
    var folderId by remember { mutableStateOf<Int?>(null) }
    var rulesId by remember { mutableStateOf<Int?>(null) }
    var searchNow by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    var seasons by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (manage) try {
            coroutineScope {
                val f = async { app.api.get<LibraryFolders>("/library-folders") }
                val r = async { app.api.get<PickRules>("/pick-rules") }
                val td = async { runCatching { app.api.get<TitleDefaults>("/settings/title-defaults") }.getOrNull() }
                folders = f.await().library_folders.filter { it.kind == kindKey }
                rules = r.await().pick_rules.filter { it.kinds.isNullOrEmpty() || kindKey in it.kinds }
                val defaults: KindDefaults? = if (kind == Kind.MOVIE) td.await()?.movie else td.await()?.show
                folderId = folders.firstOrNull { it.id == defaults?.library_folder_id }?.id
                    ?: folders.firstOrNull { it.health == "ok" }?.id ?: folders.firstOrNull()?.id
                val saved = defaults?.pick_rules_id
                rulesId = when {
                    saved != null && saved != 0 && rules.any { it.id == saved } -> saved
                    else -> folders.firstOrNull { it.id == folderId }?.default_pick_rules_id?.takeIf { r -> rules.any { it.id == r } }
                        ?: rules.firstOrNull()?.id
                }
                searchNow = defaults?.search_on_add ?: (kind == Kind.MOVIE)
            }
        } catch (e: Exception) { error = e.message }
        loading = false
    }
    LaunchedEffect(loading) {
        if (loading) return@LaunchedEffect
        delay(100)
        runCatching { first.requestFocus() }
    }

    fun pickFolder(id: Int) {
        folderId = id
        // Picking another folder brings that folder's own pick rules, as on the web.
        folders.firstOrNull { it.id == id }?.default_pick_rules_id?.takeIf { r -> rules.any { it.id == r } }?.let { rulesId = it }
    }

    fun submit() {
        if (sending) return
        sending = true
        scope.launch {
            try {
                val id = "tmdb:$tmdb"
                if (manage) {
                    val folder = folderId ?: return@launch
                    val body = buildJsonObject {
                        put("id", id); put("follow", follow); put("library_folder_id", folder)
                        rulesId?.let { put("pick_rules_id", it) }
                        put("search_on_add", searchNow); put("keep_up_to_date", true)
                        if (kind == Kind.SHOW) put("seasons", seasons)
                    }
                    app.api.post<JsonElement>(if (kind == Kind.MOVIE) "/titles" else "/shows", body.toString())
                    app.show("Added $name to your library.")
                } else {
                    val body = buildJsonObject {
                        put("id", id); put("follow", follow)
                        if (kind == Kind.SHOW) { put("kind", "show"); put("seasons", JsonArray(emptyList())) }
                    }
                    app.api.post<JsonElement>("/asks", body.toString())
                    app.show("Request sent for $name.")
                }
                app.libraryVersion++
                onClose()
            } catch (e: Exception) { error = e.message }
            sending = false
        }
    }

    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(1300.pt).clip(RoundedCornerShape(Radii.modal)).background(p.surface1)
                .verticalScroll(rememberScrollState()).padding(30.pt),
            verticalArrangement = Arrangement.spacedBy(17.pt),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.pt)) {
                Text(if (manage) "Add to library" else "Request", style = Typo.title1, color = p.text)
                Text(name, style = Typo.body, color = p.textMuted)
            }
            when {
                loading -> LoadingView()
                manage -> {
                    ChoiceRow("Folder", folders.map { it.id to it.display }, folderId, ::pickFolder)
                    ChoiceRow("Pick rules", rules.map { it.id to it.name }, rulesId) { rulesId = it }
                    if (kind == Kind.SHOW) ChoiceRow(
                        "Seasons", listOf("all" to "All", "future" to "Future", "latest" to "Latest", "none" to "None"), seasons,
                    ) { seasons = it }
                    Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.pt)) {
                        Toggle("Search for missing now", searchNow) { searchNow = !searchNow }
                        Toggle("Follow", follow) { follow = !follow }
                    }
                    if (folders.firstOrNull { it.id == folderId }?.new_title_tags?.contains("tiramisu") == true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.pt), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LiveTv, null, Modifier.size(12.pt), tint = p.textMuted)
                            Text("Streams through Tiramisu: it appears in Jellyfin without downloading.", style = Typo.callout, color = p.textMuted)
                        }
                    }
                }
                else -> {
                    Text(
                        if (kind == Kind.SHOW) "Ask for the whole show. The owner gets your request." else "The owner gets your request.",
                        style = Typo.body, color = p.textMuted,
                    )
                    Toggle("Follow", follow) { follow = !follow }
                }
            }
            error?.let { Text(it, style = Typo.callout, color = p.fail) }
            Row(Modifier.fillMaxWidth().focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.pt)) {
                Spacer(Modifier.weight(1f))
                Pill("Cancel", onClose)
                Pill(
                    if (sending) "Sending…" else if (manage) "Add" else "Send request", ::submit,
                    kind = PillKind.ACCENT, focusRequester = first, enabled = !sending && (!manage || folderId != null),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit) {
    val p = Theme.p
    Column(verticalArrangement = Arrangement.spacedBy(3.pt)) {
        Text(label, style = Typo.subhead, color = p.textMuted)
        LazyRow(
            Modifier.focusRestorer().focusGroup(),
            contentPadding = PaddingValues(vertical = 7.pt, horizontal = 3.pt),
            horizontalArrangement = Arrangement.spacedBy(7.pt),
        ) {
            items(options) { (value, text) ->
                val on = value == selected
                Pill(text, { onPick(value) }, kind = if (on) PillKind.PRIMARY else PillKind.PLAIN, icon = if (on) Icons.Filled.Check else null)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Pill(label, onClick, icon = if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank)
}

