package io.holyarrs.tv.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** A value the API sends either as a number or as a string (tmdb ids, person ids). */
@Serializable(with = FlexSerializer::class)
data class Flex(val value: String) {
    override fun toString() = value
}

object FlexSerializer : KSerializer<Flex> {
    override val descriptor = PrimitiveSerialDescriptor("Flex", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Flex {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return Flex(decoder.decodeString())
        val p = el as? JsonPrimitive ?: return Flex("")
        if (p.isString) return Flex(p.content)
        p.longOrNull?.let { return Flex(it.toString()) }
        p.doubleOrNull?.let { return Flex(it.toLong().toString()) }
        return Flex("")
    }
    override fun serialize(encoder: Encoder, value: Flex) = encoder.encodeString(value.value)
}

@Serializable(with = KindSerializer::class)
enum class Kind(val raw: String) {
    MOVIE("movie"), SHOW("show"), ARTIST("artist");

    val word get() = when (this) { MOVIE -> "Movie"; SHOW -> "Show"; ARTIST -> "Artist" }
}

object KindSerializer : KSerializer<Kind> {
    override val descriptor = PrimitiveSerialDescriptor("Kind", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Kind = when (decoder.decodeString()) {
        "show", "tv", "series" -> Kind.SHOW
        "artist", "music" -> Kind.ARTIST
        else -> Kind.MOVIE
    }
    override fun serialize(encoder: Encoder, value: Kind) = encoder.encodeString(value.raw)
}

// Account

@Serializable
data class Me(val id: Int? = null, val name: String? = null, val role: String? = null, val perms: List<String>? = null) {
    val canManage get() = perms?.contains("manage_library") == true || role == "owner"
    val canAsk get() = perms?.contains("ask") == true
}

// Discover

@Serializable
data class AskBrief(val id: Int? = null, val state: String? = null, val state_label: String? = null, val mine: Boolean? = null)

@Serializable
data class Fresh(val kind: String? = null, val text: String? = null, val rank: Int? = null)

/** `ItemView`: one title card anywhere in Explore, New & Popular, Similar, search. */
@Serializable
data class Item(
    val tmdb_id: Flex,
    val kind: Kind = Kind.MOVIE,
    val name: String,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val overview: String? = null,
    val genres: List<String>? = null,
    val in_library: Boolean? = null,
    val title_id: Int? = null,
    val ask: AskBrief? = null,
    val fresh: Fresh? = null,
    val trailer_key: String? = null,
    val card_art: String? = null,
    val card_logo: String? = null,
    val rank: Int? = null,
    val what: String? = null,
    val upcoming: Boolean? = null,
) {
    val key get() = "${kind.raw}-${tmdb_id.value}"
    val route get() = Route.Title(kind, tmdb_id.value)
}

@Serializable
data class ExploreRow(
    val id: String,
    val type: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val wide: Boolean? = null,
    val items: List<Item> = emptyList(),
) {
    val ranked get() = type == "np_top_shows" || type == "np_top_movies"
}

@Serializable
data class ExploreHead(val building: Boolean? = null, val hero: List<Item>? = null, val head: List<ExploreRow>? = null, val tail_rows: Int? = null)

@Serializable
data class ExploreTail(val page: Int? = null, val more: Boolean? = null, val rows: List<ExploreRow>? = null)

@Serializable
data class RailDay(val day: String, val items: List<Item> = emptyList())

@Serializable
data class ExploreNew(
    val page: Int? = null,
    val more: Boolean? = null,
    val building: Boolean? = null,
    val rows: List<ExploreRow>? = null,
    val hero: List<Item>? = null,
    val rail: List<RailDay>? = null,
)

// Home

@Serializable
data class HomeCard(
    val key: String,
    val kind: Kind = Kind.MOVIE,
    val title_id: Int? = null,
    val name: String,
    val year: Int? = null,
    val line: String? = null,
    val backdrop: String? = null,
    val poster: String? = null,
    val logo: String? = null,
    val season: Int? = null,
    val at: String? = null,
)

@Serializable
data class LibraryCounts(
    val titles: Int? = null, val parts: Int? = null, val items: Int? = null, val on_disk: Int? = null,
    val missing: Int? = null, val downloading: Int? = null, val size: Long? = null,
)

@Serializable
data class Home(
    val rows: List<String>? = null,
    val owner: Boolean? = null,
    val arrived: List<HomeCard>? = null,
    val new_seasons: List<HomeCard>? = null,
    val following: List<HomeCard>? = null,
    val soon: List<HomeCard>? = null,
    val library: Map<String, LibraryCounts>? = null,
)

@Serializable
data class HealthItem(val name: String? = null, val state: String? = null)

@Serializable
data class Health(val ok: Boolean? = null, val sources: List<HealthItem>? = null, val downloaders: List<HealthItem>? = null)

// Library

@Serializable
data class Slot(val id: Int? = null, val state: String? = null, val state_label: String? = null)

@Serializable
data class SeasonInfo(
    val number: Int,
    val name: String? = null,
    val episodes: Int? = null,
    val aired: Int? = null,
    val in_library: Int? = null,
    val missing: Int? = null,
)

@Serializable
data class ShowInfo(val status: String? = null, val network: String? = null, val seasons: List<SeasonInfo>? = null)

private val HAVE_STATES = setOf("in_library", "better_wanted", "no_hardlink")

/** `TitleView`: a title in the Library. */
@Serializable
data class LibraryTitle(
    val id: Int,
    val kind: Kind = Kind.MOVIE,
    val name: String,
    val year: Int? = null,
    val overview: String? = null,
    val ids: Map<String, Flex>? = null,
    val artwork: List<String>? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val genres: List<String>? = null,
    val runtime_minutes: Int? = null,
    val score: Double? = null,
    val added_at: String? = null,
    val size_bytes: Long? = null,
    val library_folder_id: Int? = null,
    val pick_rules_name: String? = null,
    val slots: List<Slot>? = null,
    val show: ShowInfo? = null,
) {
    val tmdb get() = ids?.get("tmdb")?.value
    val poster get() = artwork?.firstOrNull()

    /** The web's card status: have, downloading, wanted (missing) or not out yet. */
    val status: String
        get() {
            if (kind == Kind.SHOW) {
                val s = show?.seasons?.filter { it.number > 0 }
                if (s != null) {
                    val have = s.sumOf { it.in_library ?: 0 }
                    val missing = s.sumOf { it.missing ?: 0 }
                    if (missing == 0 && have > 0) return "have"
                    return if (have > 0) "partial" else "missing"
                }
            }
            val states = slots.orEmpty().mapNotNull { it.state }
            if (states.any { it in HAVE_STATES }) return "have"
            if ("downloading" in states) return "downloading"
            if ("not_out_yet" in states) return "not_out_yet"
            return "missing"
        }
}

@Serializable
data class TitlesGrid(val titles: List<LibraryTitle> = emptyList())

@Serializable
data class Episode(
    val id: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val number: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val air_day: String? = null,
    val runtime: Int? = null,
    val still: String? = null,
    val slots: List<Slot>? = null,
) {
    val num get() = episode ?: number ?: 0
    val have get() = slots.orEmpty().any { (it.state ?: "") in HAVE_STATES }
    val stableID get() = "${season ?: 0}-$num"
}

@Serializable
data class Episodes(val episodes: List<Episode> = emptyList())

// Title details

@Serializable
data class Person(
    val name: String,
    val role: String? = null,
    val character: String? = null,
    val profile: String? = null,
    @SerialName("id") val pid: Flex? = null,
    val department: String? = null,
) {
    val key get() = (pid?.value ?: "") + name + (character ?: "")
}

@Serializable
data class TMDBSeason(val number: Int, val episode_count: Int? = null, val year: Int? = null)

@Serializable
data class Details(
    val tmdb_id: Flex,
    val kind: Kind = Kind.MOVIE,
    val name: String,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val overview: String? = null,
    val genres: List<String>? = null,
    val logo: String? = null,
    val runtime_minutes: Int? = null,
    val episode_runtime: Int? = null,
    val rating: String? = null,
    val score: Double? = null,
    val people: List<Person>? = null,
    val status: String? = null,
    val tagline: String? = null,
    val studios: List<String>? = null,
    val seasons: List<TMDBSeason>? = null,
    val similar: List<Item>? = null,
    val in_library: Boolean? = null,
    val title_id: Int? = null,
    val ask: AskBrief? = null,
    val never_add: Boolean? = null,
)

@Serializable
data class Score(val source: String, val value: Double, val max: Double? = null)

@Serializable
data class Extras(val logo: String? = null, val backgrounds: List<String>? = null, val scores: List<Score>? = null)

@Serializable
data class Follow(val following: Boolean? = null)

@Serializable
data class WatchLink(val server: String? = null, val kind: String? = null, val url: String)

@Serializable
data class WatchLinks(val links: List<WatchLink>? = null)

// Adding

@Serializable
data class LibraryFolder(
    val id: Int,
    val path: String,
    val kind: String,
    val label: String? = null,
    val default_pick_rules_id: Int? = null,
    val health: String? = null,
    val new_title_tags: List<String>? = null,
) {
    val display get() = label?.takeIf { it.isNotEmpty() } ?: path
}

@Serializable
data class LibraryFolders(val library_folders: List<LibraryFolder> = emptyList())

@Serializable
data class PickRule(val id: Int, val name: String, val kinds: List<String>? = null)

@Serializable
data class PickRules(val pick_rules: List<PickRule> = emptyList())

@Serializable
data class KindDefaults(
    val library_folder_id: Int? = null,
    val pick_rules_id: Int? = null,
    val keep_up_to_date: Boolean? = null,
    val search_on_add: Boolean? = null,
)

@Serializable
data class TitleDefaults(val movie: KindDefaults? = null, val show: KindDefaults? = null)

// Requests

@Serializable
data class NamedUser(val id: Int? = null, val name: String? = null, val display_name: String? = null)

@Serializable
data class AskStep(val kind: String? = null, val text: String? = null)

@Serializable
data class Ask(
    val id: Int,
    val kind: Kind = Kind.MOVIE,
    val tmdb_id: Flex? = null,
    val name: String,
    val year: Int? = null,
    val poster: String? = null,
    val state: String,
    val state_label: String? = null,
    val season_words: String? = null,
    val title_id: Int? = null,
    val asked_by: NamedUser? = null,
    val asked_at: String? = null,
    val note: String? = null,
    val step: AskStep? = null,
    val problem: String? = null,
    val adding: Boolean? = null,
)

@Serializable
data class Asks(val asks: List<Ask> = emptyList())

// Calendar

@Serializable
data class CalendarRow(
    val title_id: Int? = null,
    val name: String,
    val year: Int? = null,
    val kind: String,
    val date: String,
    val tmdb: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episode_name: String? = null,
    val network: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val still: String? = null,
    val banner: String? = null,
    val logo: String? = null,
) {
    val key get() = "${title_id ?: 0}-$kind-$date-${season ?: 0}-${episode ?: 0}"
    val day get() = date.take(10)
}

@Serializable
data class CalendarResponse(val dates: List<CalendarRow>? = null)

// Downloads

@Serializable
data class DownloadRow(
    @SerialName("id") val rowID: Flex? = null,
    val state: String? = null,
    val label: String? = null,
    val progress: Double? = null,
    val speed: Double? = null,
    val eta: Int? = null,
    val phase: String? = null,
    val name: String? = null,
    val title: String? = null,
    val year: Int? = null,
    val size: Long? = null,
    val downloader: String? = null,
    val title_id: Int? = null,
    val hash: String? = null,
) {
    val key get() = (hash ?: "") + (name ?: "") + (rowID?.value ?: "")
}

@Serializable
data class DownloadTotals(val speed: Double? = null, val up_speed: Double? = null)

@Serializable
data class DownloadsView(val downloads: List<DownloadRow>? = null, val totals: DownloadTotals? = null)

// Search & people

@Serializable
data class Lookup(
    val name: String,
    val year: Int? = null,
    val overview: String? = null,
    val ids: Map<String, Flex>? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val title_id: Int? = null,
) {
    fun item(kind: Kind): Item? {
        val t = ids?.get("tmdb")?.value ?: return null
        return Item(Flex(t), kind, name, year, poster, backdrop, overview, in_library = title_id != null, title_id = title_id)
    }
}

@Serializable
data class MovieLookup(val movies: List<Lookup>? = null)

@Serializable
data class ShowLookup(val shows: List<Lookup>? = null)

@Serializable
data class PeopleSearch(val people: List<Person>? = null)

@Serializable
data class PersonInfo(
    val id: Flex? = null,
    val name: String,
    val department: String? = null,
    val biography: String? = null,
    val birthday: String? = null,
    val birthplace: String? = null,
    val profile: String? = null,
)

@Serializable
data class PersonCredit(
    val name: String,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val ids: Map<String, Flex>? = null,
    val kind: Kind? = null,
    val title_id: Int? = null,
    val characters: List<String>? = null,
) {
    val item: Item?
        get() {
            val t = ids?.get("tmdb")?.value ?: return null
            return Item(Flex(t), kind ?: Kind.MOVIE, name, year, poster, backdrop, in_library = title_id != null, title_id = title_id)
        }
}

@Serializable
data class PersonPageData(val person: PersonInfo, val movies: List<PersonCredit>? = null)

// Navigation

sealed interface Route {
    data class Title(val kind: Kind, val tmdb: String) : Route
    data class LibraryTitle(val id: Int) : Route
    data class Person(val id: String, val name: String) : Route
}
