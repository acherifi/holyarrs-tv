package io.holyarrs.tv.ui

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.foundation.Image
import io.holyarrs.tv.app.LocalApp
import io.holyarrs.tv.core.Geist
import io.holyarrs.tv.core.Item
import io.holyarrs.tv.core.Marks
import io.holyarrs.tv.core.Placeholder
import io.holyarrs.tv.core.Radii
import io.holyarrs.tv.core.Route
import io.holyarrs.tv.core.Space
import io.holyarrs.tv.core.Theme
import io.holyarrs.tv.core.Typo
import io.holyarrs.tv.core.pt
import io.holyarrs.tv.core.wp
import io.holyarrs.tv.core.wsp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Pictures

/** Loads a picture through HolyArrs (with the API key), falling back to the web's initials tile. */
@Composable
fun RemoteImage(
    raw: String?,
    modifier: Modifier = Modifier,
    size: String = "w500",
    name: String = "",
    showInitials: Boolean = true,
    fit: Boolean = false,
    alignment: Alignment = Alignment.Center,
) {
    val app = LocalApp.current
    val url = app.api.imageUrl(raw, size)
    Box(modifier) {
        if (url == null) {
            if (showInitials) InitialsTile(name, Modifier.fillMaxSize())
            return@Box
        }
        val ctx = LocalContext.current
        // A picture that fails (a network blip) is asked for again, twice at most.
        var attempt by remember(url) { mutableStateOf(0) }
        val request = remember(url, attempt) {
            ImageRequest.Builder(ctx).data(url).crossfade(250).apply {
                if (app.api.wantsKey(url)) addHeader("X-Api-Key", app.api.key)
            }.build()
        }
        val painter = rememberAsyncImagePainter(request, imageLoader = app.imageLoader)
        val failed = painter.state is AsyncImagePainter.State.Error
        LaunchedEffect(failed, attempt) {
            if (failed && attempt < 2) { delay(1500L * (attempt + 1)); attempt++ }
        }
        when (painter.state) {
            is AsyncImagePainter.State.Error -> if (showInitials) InitialsTile(name, Modifier.fillMaxSize())
            is AsyncImagePainter.State.Success -> Unit
            else -> if (showInitials) Box(Modifier.fillMaxSize().background(Theme.p.surface2))
        }
        Image(
            painter, null, Modifier.fillMaxSize(),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            alignment = alignment,
        )
    }
}

@Composable
fun InitialsTile(name: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.verticalGradient(Placeholder.colors(name))), contentAlignment = Alignment.Center) {
        Text(Placeholder.initials(name), style = Typo.geist(30f, FontWeight.Bold), color = Color.White.copy(alpha = 0.78f))
    }
}

// Focus

/**
 * Everything you can select: reacts to the D-pad centre / Enter, tracks focus
 * for its look, and draws above its neighbours while focused so a lifted card
 * is never covered by the next one.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocus: (Boolean) -> Unit = {},
    enabled: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
            .clickable(source, indication = null, enabled = enabled, onClick = onClick),
    ) { content(focused) }
}

/** Cards lift and glow on focus like the web's hover lift. */
@Composable
fun Modifier.lift(focused: Boolean, radius: Dp = Radii.card, scale: Float = 1.06f): Modifier {
    val s by animateFloatAsState(if (focused) scale else 1f, tween(180), label = "lift")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .shadow(if (focused) 14.dp else 0.dp, RoundedCornerShape(radius), clip = false)
}

/** The focus ring drawn around a picture. */
@Composable
fun Modifier.ring(focused: Boolean, radius: Dp = Radii.card): Modifier =
    border(2.5.dp, if (focused) Theme.p.focus else Color.Transparent, RoundedCornerShape(radius))

enum class PillKind { PRIMARY, GLASS, PLAIN, ACCENT, SEGMENT, SEGMENT_ON }

/** The web's rounded buttons: white "In library", glass "Open in Jellyfin", red primary, segment pills. */
@Composable
fun Pill(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: PillKind = PillKind.PLAIN,
    icon: ImageVector? = null,
    round: Boolean = false,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
) {
    val p = Theme.p
    Focusable(onClick, modifier, focusRequester, enabled = enabled) { focused ->
        val onArt = kind == PillKind.PRIMARY || kind == PillKind.GLASS
        val fg = when {
            focused -> if (onArt) Color.Black else Color.White
            kind == PillKind.PRIMARY -> Color.Black
            kind == PillKind.GLASS || kind == PillKind.ACCENT -> Color.White
            kind == PillKind.PLAIN || kind == PillKind.SEGMENT_ON -> p.text
            else -> p.textMuted
        }
        val bg = when {
            focused -> if (onArt) Color.White else p.accentFill
            kind == PillKind.PRIMARY -> Color.White
            kind == PillKind.GLASS -> Color.White.copy(alpha = 0.16f)
            kind == PillKind.ACCENT -> p.accentFill
            kind == PillKind.PLAIN -> p.surface2
            kind == PillKind.SEGMENT_ON -> p.segThumb
            else -> Color.Transparent
        }
        val border = when {
            kind == PillKind.GLASS && !focused -> Color.White.copy(alpha = 0.22f)
            kind == PillKind.SEGMENT_ON && !focused -> p.line
            else -> Color.Transparent
        }
        Row(
            Modifier
                .lift(focused, 999.dp, 1.08f)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape)
                .defaultMinSize(minWidth = if (round) 44.wp else 0.dp, minHeight = 44.wp)
                .padding(horizontal = if (round) 0.dp else 20.wp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.pt, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(13.wp), tint = fg)
            if (text != null) Text(text, style = Typo.geist(17f, FontWeight.SemiBold), color = fg, maxLines = 1)
        }
    }
}

// Shelf

/**
 * A titled horizontal row. The row restores the card you left when focus comes
 * back to it, so moving up and down the page never lands on a random card.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Shelf(title: String, subtitle: String? = null, gap: Dp = Space.shelfGap, content: LazyListScope.() -> Unit) {
    val p = Theme.p
    Column(verticalArrangement = Arrangement.spacedBy(9.pt)) {
        Column(Modifier.padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(2.pt)) {
            Text(title, style = Typo.title2, color = p.text)
            if (subtitle != null) Text(subtitle, style = Typo.callout, color = p.textMuted)
        }
        LazyRow(
            Modifier.focusRestorer().focusGroup(),
            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = 14.pt),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Top,
            content = content,
        )
    }
}

// Cards

/** Explore's wide card: backdrop, logo bottom-left, red "fresh" badge, name and "Movie · 2026" below. */
@Composable
fun WideCard(item: Item, width: Dp = 520.pt, subtitle: String? = null) {
    val app = LocalApp.current
    val p = Theme.p
    Focusable({ app.open(item.route) }) { focused ->
        Column(Modifier.width(width).lift(focused), verticalArrangement = Arrangement.spacedBy(5.pt)) {
            Box(Modifier.width(width).height(width * 9 / 16).clip(RoundedCornerShape(Radii.card)).ring(focused)) {
                RemoteImage(item.card_art ?: item.backdrop, Modifier.fillMaxSize(), "w780", item.name)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
                item.card_logo?.let {
                    RemoteImage(
                        it, Modifier.align(Alignment.BottomStart).padding(11.pt).sizeIn(maxWidth = width * 0.45f, maxHeight = width * 0.16f),
                        "w500", showInitials = false, fit = true, alignment = Alignment.BottomStart,
                    )
                }
                StatusMark(item, Modifier.align(Alignment.TopEnd).padding(7.pt))
                (item.fresh?.text ?: item.what)?.let { Badge(it, Modifier.align(Alignment.BottomEnd).padding(8.pt)) }
            }
            Column {
                Text(item.name, style = Typo.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle ?: listOfNotNull(item.kind.word, item.year?.toString()).joinToString(" · "),
                    style = Typo.footnote, color = p.textMuted, maxLines = 1,
                )
            }
        }
    }
}

/** Library-style poster: 2:3 picture with name and year under it. */
@Composable
fun PosterCard(
    name: String,
    year: Int?,
    poster: String?,
    route: Route,
    width: Dp = 230.pt,
    rank: Int? = null,
    mark: String? = null,
) {
    val app = LocalApp.current
    val p = Theme.p
    Focusable({ app.open(route) }) { focused ->
        Row(verticalAlignment = Alignment.Bottom) {
            if (rank != null) {
                Text(
                    "$rank", fontFamily = Geist, fontWeight = FontWeight.Bold,
                    fontSize = (width.value * 0.78f).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) },
                    color = p.surface3, modifier = Modifier.offset(x = width * 0.12f, y = width * 0.14f),
                    maxLines = 1,
                )
            }
            Column(Modifier.width(width).lift(focused), verticalArrangement = Arrangement.spacedBy(5.pt)) {
                Box(Modifier.width(width).height(width * 1.5f).clip(RoundedCornerShape(Radii.card)).ring(focused)) {
                    RemoteImage(poster, Modifier.fillMaxSize(), "w342", name)
                    if (mark != null) StatusDot(mark, Modifier.align(Alignment.TopEnd).padding(6.pt))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.pt), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = Typo.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (year != null) Text("$year", style = Typo.footnote, color = p.textMuted)
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = Typo.caption, color = Color.White,
        modifier = modifier.clip(RoundedCornerShape(2.pt)).background(Theme.p.accentFill).padding(horizontal = 5.pt, vertical = 2.pt),
    )
}

/** The library status circle drawn on cards (have / downloading / asked / no). */
@Composable
fun StatusDot(state: String, modifier: Modifier = Modifier) {
    val (color, icon) = when (state) {
        "have" -> Marks.have to Icons.Filled.Check
        "downloading" -> Marks.download to Icons.Filled.ArrowDownward
        "asked", "partial" -> Marks.ask to Icons.Filled.Schedule
        "not_out_yet" -> Marks.ask to Icons.Filled.Event
        else -> Marks.no to Icons.Filled.Close
    }
    Box(
        modifier.size(20.pt).clip(CircleShape).background(color).border(0.5.dp, Color.Black.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(11.pt), tint = Color.White) }
}

@Composable
fun StatusMark(item: Item, modifier: Modifier = Modifier) {
    val s = item.ask?.state
    when {
        item.in_library == true -> StatusDot("have", modifier)
        s == "asked" || s == "approved" -> StatusDot("asked", modifier)
    }
}

// Page chrome

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> SegmentBar(options: List<Triple<T, String, ImageVector?>>, selection: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Theme.p.surface2).padding(2.5.pt).focusRestorer().focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.pt),
    ) {
        for ((value, label, icon) in options) {
            Pill(label, { onSelect(value) }, kind = if (value == selection) PillKind.SEGMENT_ON else PillKind.SEGMENT, icon = icon)
        }
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 150.pt), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Theme.p.textMuted, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

/** The web's empty card: a round icon, a headline and one line under it. */
@Composable
fun EmptyCard(icon: ImageVector, title: String, line: String, modifier: Modifier = Modifier) {
    val p = Theme.p
    Column(
        modifier.fillMaxWidth().border(1.dp, p.line, RoundedCornerShape(Radii.panel)).padding(vertical = 45.pt),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.pt),
    ) {
        Box(Modifier.size(40.pt).clip(CircleShape).background(p.surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(15.pt), tint = p.text)
        }
        Text(title, style = Typo.title3, color = p.text)
        Text(line, style = Typo.callout, color = p.textMuted, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier, retry: (() -> Unit)? = null) {
    val p = Theme.p
    Column(
        modifier.fillMaxWidth().padding(30.pt),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.pt),
    ) {
        Icon(Icons.Outlined.WarningAmber, null, Modifier.size(17.pt), tint = p.wait)
        Text(message, style = Typo.callout, color = p.textMuted, textAlign = TextAlign.Center)
        if (retry != null) Pill("Try again", retry, kind = PillKind.ACCENT)
    }
}

/**
 * Long text (overviews, biographies) as a focusable block: the D-pad only moves
 * between focusable things, so plain text below the last button would be
 * unreachable. Selecting it opens the whole text.
 */
@Composable
fun FocusableText(text: String, lines: Int = 4, modifier: Modifier = Modifier) {
    val p = Theme.p
    var open by remember { mutableStateOf(false) }
    Focusable({ open = true }, modifier) { focused ->
        Text(
            text, style = Typo.body, color = p.text, maxLines = lines, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card))
                .background(if (focused) p.surface2 else Color.Transparent).padding(9.pt),
        )
    }
    if (open) TextDialog(text) { open = false }
}

/** Full text in a dialog; up and down scroll it. */
@Composable
fun TextDialog(text: String, onClose: () -> Unit) {
    val p = Theme.p
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.widthIn(max = 700.pt).heightIn(max = 860.pt).clip(RoundedCornerShape(Radii.modal)).background(p.surface1)
                .focusRequester(fr)
                .onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (it.key) {
                        Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(200f) }; true }
                        Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-200f) }; true }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            Text(text, style = Typo.body, color = p.text, modifier = Modifier.verticalScroll(scroll).padding(30.pt))
        }
    }
    LaunchedEffect(Unit) { fr.requestFocus() }
}

private suspend fun androidx.compose.foundation.ScrollState.animateScrollBy(d: Float) {
    animateScrollTo((value + d.toInt()).coerceIn(0, maxValue))
}

@Composable
fun bytes(n: Long): String = Formatter.formatShortFileSize(LocalContext.current, n)

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

/**
 * A text field the D-pad can pass over: focusing it does nothing, selecting it
 * starts typing (and opens the on-screen keyboard), Done or Back stops.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Outlined.Search,
    secret: Boolean = false,
    focusRequester: FocusRequester? = null,
    onDone: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    val p = Theme.p
    var editing by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val outer = focusRequester ?: remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Focusable({ editing = true }, modifier, outer) { focused ->
        Row(
            Modifier.fillMaxWidth().height(52.wp).clip(RoundedCornerShape(Radii.btn))
                .background(p.surface2)
                .border(if (focused || editing) 2.dp else 1.dp, if (focused || editing) p.focus else p.line, RoundedCornerShape(Radii.btn))
                .padding(horizontal = 14.wp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.wp),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(16.wp), tint = p.textMuted)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = Typo.body, color = p.textFaint, maxLines = 1)
                BasicTextField(
                    value, onValueChange,
                    Modifier.fillMaxWidth().focusRequester(field)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (!it.isFocused && editing) editing = false },
                    textStyle = Typo.body.copy(color = p.text),
                    singleLine = true,
                    cursorBrush = SolidColor(p.accent),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
                    keyboardActions = KeyboardActions(onDone = {
                        editing = false
                        keyboard?.hide()
                        runCatching { outer.requestFocus() }
                        onDone()
                    }),
                )
            }
            trailing()
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { field.requestFocus() }
            keyboard?.show()
        }
    }
}

/** A list of choices in a dialog (the web's menus and selects). */
@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit, onClose: () -> Unit) {
    val p = Theme.p
    val first = remember { FocusRequester() }
    Dialog(onClose) {
        Column(
            Modifier.widthIn(min = 300.pt, max = 520.pt).clip(RoundedCornerShape(Radii.modal)).background(p.surface1).padding(16.pt),
            verticalArrangement = Arrangement.spacedBy(3.pt),
        ) {
            Text(title, style = Typo.title3, color = p.text, modifier = Modifier.padding(start = 8.pt, bottom = 6.pt))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, (value, label) ->
                    val on = value == selected
                    Focusable(
                        { onPick(value); onClose() },
                        focusRequester = if (on || (selected == null && i == 0) || (i == 0 && options.none { it.first == selected })) first else null,
                    ) { focused ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.btn))
                                .background(if (focused) p.surface3 else Color.Transparent).padding(horizontal = 9.pt, vertical = 7.pt),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = Typo.body, color = p.text, modifier = Modifier.weight(1f))
                            if (on) Icon(Icons.Filled.Check, null, Modifier.size(12.pt), tint = p.accent)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
