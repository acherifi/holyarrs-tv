package io.holyarrs.tv.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

class ApiError(message: String) : Exception(message)

val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

/**
 * Talks to HolyArrs' /api/v1 with an API key, the same calls the web app makes.
 * In demo mode it answers from JSON bundled in the app (used for emulator screenshots).
 */
class Api(server: String, key: String, val demo: Boolean, private val context: Context) {
    val base: String? = normalize(server)
    val key: String = key.trim()

    val configured get() = demo || (base != null && key.isNotEmpty())

    suspend inline fun <reified T> get(path: String): T = decode(send("GET", path, null), path)

    suspend inline fun <reified T> post(path: String, body: String): T = decode(send("POST", path, body), path)

    suspend fun put(path: String) { send("PUT", path, null) }
    suspend fun delete(path: String) { send("DELETE", path, null) }

    inline fun <reified T> decode(text: String, path: String): T = try {
        json.decodeFromString<T>(text.ifBlank { "{}" })
    } catch (e: Exception) {
        throw ApiError("Could not read $path: ${e.message}")
    }

    suspend fun send(method: String, path: String, body: String?): String = withContext(Dispatchers.IO) {
        if (demo) return@withContext demoAnswer(method, path)
        val url = (base ?: throw ApiError("Set the HolyArrs address in Settings.")) + "/api/v1" + path
        val rb = when {
            body != null -> body.toRequestBody("application/json".toMediaType())
            method == "GET" -> null
            else -> ByteArray(0).toRequestBody(null)
        }
        val req = Request.Builder().url(url)
            .header("X-Api-Key", key)
            .header("Accept", "application/json")
            .header("X-HolyArrs-Language", Locale.getDefault().toLanguageTag())
            .method(method, rb)
            .build()
        val resp = try { client.newCall(req).execute() } catch (e: Exception) {
            throw ApiError(e.message ?: "Couldn't reach HolyArrs.")
        }
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val msg = runCatching { json.decodeFromString<JsonObject>(text)["error"]?.jsonPrimitive?.content }.getOrNull()
                throw ApiError(msg ?: "HolyArrs answered ${it.code}.")
            }
            text
        }
    }

    /** The web app never loads TMDB directly: it goes through HolyArrs' signed-in artwork proxy. */
    fun imageUrl(raw: String?, size: String = "w500"): String? {
        if (raw.isNullOrEmpty()) return null
        if (raw.startsWith("https://image.tmdb.org/t/p/")) {
            val file = raw.substringAfterLast('/')
            if (demo) return "https://image.tmdb.org/t/p/$size/$file"
            return base?.let { "$it/api/v1/artwork/tmdb/$size/$file" }
        }
        if (raw.startsWith("/")) return if (demo) null else base?.let { it + raw }
        return raw
    }

    /** Only requests to HolyArrs itself carry the key. */
    fun wantsKey(url: String): Boolean {
        if (demo) return false
        val host = base?.toHttpUrlOrNull()?.host ?: return false
        return url.toHttpUrlOrNull()?.host == host
    }

    // Demo answers

    private fun demoAnswer(method: String, path: String): String {
        val plain = path.substringBefore('?')
        if (method != "GET") return load(path) ?: load(plain) ?: "{}"
        return load(path) ?: load(plain) ?: loadPrefix(plain) ?: throw ApiError("No demo data for $path")
    }

    private val demoFiles by lazy { context.assets.list("demo").orEmpty().sorted() }

    private fun load(path: String): String? {
        val name = fixtureName(path) + ".json"
        if (name !in demoFiles) return null
        return context.assets.open("demo/$name").bufferedReader().use { it.readText() }
    }

    private fun loadPrefix(path: String): String? {
        val stem = fixtureName(path) + "_"
        val hit = demoFiles.firstOrNull { it.startsWith(stem) } ?: return null
        return context.assets.open("demo/$hit").bufferedReader().use { it.readText() }
    }

    companion object {
        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        fun normalize(server: String): String? {
            var s = server.trim().trimEnd('/')
            if (s.isEmpty()) return null
            if (!s.contains("://")) s = "http://$s"
            return s.toHttpUrlOrNull()?.toString()?.trimEnd('/')
        }

        fun fixtureName(path: String): String =
            "demo_" + path.removePrefix("/").map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    }
}

/** Reads `{web}/web/#/details?id=X&serverId=Y`. */
object JellyfinLink {
    fun itemId(s: String): String? = Regex("[?&]id=([^&#]+)").find(s)?.groupValues?.get(1)
}
