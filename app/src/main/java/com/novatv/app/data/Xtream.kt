package com.novatv.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Cliente da API Xtream Codes (player_api.php) usada pela maioria dos painéis IPTV. */
object Xtream {

    private val base: String get() = Prefs.server.trim().trimEnd('/')
    private val cache = HashMap<String, Any>()

    fun clearCache() = cache.clear()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String?, extra: String = ""): String {
        val a = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=${enc(Prefs.user)}&password=${enc(Prefs.pass)}$a$extra"
    }

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        var target = url
        var result: String? = null
        var redirects = 0
        while (result == null) {
            val c = URL(target).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 45000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "NovaTV/1.0 (Linux; Android)")
            try {
                val code = c.responseCode
                val loc = c.getHeaderField("Location")
                if (code in 300..399 && loc != null && redirects < 5) {
                    target = URL(URL(target), loc).toString()
                    redirects++
                } else {
                    if (code >= 400) throw Exception("Servidor respondeu HTTP $code")
                    result = c.inputStream.bufferedReader().use { it.readText() }
                }
            } finally {
                c.disconnect()
            }
        }
        result ?: ""
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        cache[key]?.let { return it as T }
        val v = load()
        cache[key] = v
        return v
    }

    private fun arr(s: String): JSONArray = try {
        val t = s.trim()
        if (t.startsWith("[")) JSONArray(t) else JSONArray()
    } catch (e: Exception) { JSONArray() }

    private fun objs(a: JSONArray): List<JSONObject> =
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    fun b64(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) { s }

    // ------------------------------------------------------------------ M3U
    private suspend fun m3u(): M3u.Data = cached("m3u") { loadM3u() }

    private suspend fun loadM3u(): M3u.Data = withContext(Dispatchers.IO) {
        var target = Prefs.m3uUrl.trim()
        var result: M3u.Data? = null
        var redirects = 0
        while (result == null) {
            val c = URL(target).openConnection() as HttpURLConnection
            c.connectTimeout = 20000
            c.readTimeout = 60000
            c.setRequestProperty("User-Agent", "NovaTV/1.0 (Linux; Android)")
            try {
                val code = c.responseCode
                val loc = c.getHeaderField("Location")
                if (code in 300..399 && loc != null && redirects < 5) {
                    target = URL(URL(target), loc).toString()
                    redirects++
                } else {
                    if (code >= 400) throw Exception("Servidor respondeu HTTP $code")
                    result = c.inputStream.bufferedReader().use { M3u.parse(it) }
                }
            } finally {
                c.disconnect()
            }
        }
        result ?: throw Exception("Falha ao ler a lista")
    }

    /** Baixa e valida a lista M3U no login. Retorna a quantidade de itens. */
    suspend fun loginM3u(): Int {
        val d = m3u()
        if (d.isEmpty) throw Exception("Lista vazia ou link inválido")
        return d.live.size + d.movies.size + d.series.size
    }

    // ------------------------------------------------------------------ login
    suspend fun login(): JSONObject {
        val body = get(api(null))
        val o = try { JSONObject(body) } catch (e: Exception) { throw Exception("Resposta inválida do servidor") }
        val ui = o.optJSONObject("user_info") ?: throw Exception("Resposta inválida do servidor")
        if (ui.optInt("auth", 0) != 1) throw Exception("Usuário ou senha inválidos")
        val status = ui.optString("status")
        if (status.isNotEmpty() && !status.equals("Active", true)) throw Exception("Conta $status")
        return ui
    }

    // ------------------------------------------------------------------ categorias
    private fun cats(s: String) = objs(arr(s))
        .map { Category(it.optString("category_id"), it.optString("category_name")) }
        .filter { Parental.allowed(it.name) }

    suspend fun liveCategories(): List<Category> = cached("lc") {
        if (Prefs.isM3u) m3u().liveCats.filter { Parental.allowed(it.name) } else cats(get(api("get_live_categories")))
    }
    suspend fun vodCategories(): List<Category> = cached("vc") {
        if (Prefs.isM3u) m3u().vodCats.filter { Parental.allowed(it.name) } else cats(get(api("get_vod_categories")))
    }
    suspend fun seriesCategories(): List<Category> = cached("sc") {
        if (Prefs.isM3u) m3u().seriesCats.filter { Parental.allowed(it.name) } else cats(get(api("get_series_categories")))
    }

    // ------------------------------------------------------------------ listas
    suspend fun liveStreams(): List<LiveStream> = cached("ls") {
        val allowed = liveCategories().map { it.id }.toSet()
        if (Prefs.isM3u) return@cached m3u().live.filter { it.categoryId in allowed }
        objs(arr(get(api("get_live_streams")))).map {
            LiveStream(
                it.optInt("stream_id"), it.optString("name"), it.optString("stream_icon"),
                it.optString("category_id"), it.optInt("num")
            )
        }.filter { it.categoryId in allowed }
    }

    suspend fun movies(): List<VodItem> = cached("vs") {
        val allowed = vodCategories().map { it.id }.toSet()
        if (Prefs.isM3u) return@cached m3u().movies.filter { it.categoryId in allowed }
        objs(arr(get(api("get_vod_streams")))).map {
            VodItem(
                it.optInt("stream_id"), it.optString("name"), it.optString("stream_icon"),
                it.optString("rating"), it.optString("container_extension", "mp4"),
                it.optString("category_id"), it.optString("added").toLongOrNull() ?: 0L, false
            )
        }.filter { it.categoryId in allowed }
    }

    suspend fun series(): List<VodItem> = cached("ss") {
        val allowed = seriesCategories().map { it.id }.toSet()
        if (Prefs.isM3u) return@cached m3u().series.filter { it.categoryId in allowed }
        objs(arr(get(api("get_series")))).map {
            VodItem(
                it.optInt("series_id"), it.optString("name"), it.optString("cover"),
                it.optString("rating"), "", it.optString("category_id"),
                it.optString("last_modified").toLongOrNull() ?: 0L, true
            )
        }.filter { it.categoryId in allowed }
    }

    // ------------------------------------------------------------------ EPG
    suspend fun epg(streamId: Int, full: Boolean): List<EpgItem> = if (Prefs.isM3u) emptyList() else try {
        val url = if (full) api("get_simple_data_table", "&stream_id=$streamId")
        else api("get_short_epg", "&stream_id=$streamId&limit=3")
        val o = JSONObject(get(url))
        val a = o.optJSONArray("epg_listings") ?: JSONArray()
        objs(a).map {
            EpgItem(
                b64(it.optString("title")),
                (it.optString("start_timestamp").toLongOrNull() ?: 0L) * 1000,
                (it.optString("stop_timestamp").toLongOrNull() ?: 0L) * 1000
            )
        }.filter { it.end > 0 }.sortedBy { it.start }.distinctBy { it.start }
    } catch (e: Exception) { emptyList() }

    // ------------------------------------------------------------------ detalhes
    private fun backdrop(info: JSONObject): String {
        val b = info.opt("backdrop_path")
        return when (b) {
            is JSONArray -> if (b.length() > 0) b.optString(0) else ""
            is String -> b
            else -> ""
        }
    }

    suspend fun movieDetail(id: Int): Detail {
        if (Prefs.isM3u) {
            val m = m3u().movies.first { it.id == id }
            return Detail(m.name, m.poster, "", "", "", "", "", m.id, m.ext, url = m.url)
        }
        val o = JSONObject(get(api("get_vod_info", "&vod_id=$id")))
        val info = o.optJSONObject("info") ?: JSONObject()
        val data = o.optJSONObject("movie_data") ?: JSONObject()
        val date = info.optString("releasedate").ifEmpty { info.optString("release_date") }
        return Detail(
            title = data.optString("name").ifEmpty { info.optString("name") },
            poster = info.optString("movie_image").ifEmpty { info.optString("cover_big") },
            backdrop = backdrop(info),
            plot = info.optString("plot").ifEmpty { info.optString("description") },
            rating = info.optString("rating"),
            year = date.take(4),
            genre = info.optString("genre"),
            streamId = data.optInt("stream_id", id),
            ext = data.optString("container_extension", "mp4").ifEmpty { "mp4" }
        )
    }

    suspend fun seriesDetail(id: Int): Detail {
        if (Prefs.isM3u) {
            val d = m3u()
            val s = d.series.first { it.id == id }
            val eps = d.episodes[id] ?: emptyList()
            return Detail(s.name, s.poster, "", "", "", "", "", seasons = eps.groupBy { it.season }.toSortedMap())
        }
        val o = JSONObject(get(api("get_series_info", "&series_id=$id")))
        val info = o.optJSONObject("info") ?: JSONObject()
        val seasons = sortedMapOf<Int, List<Episode>>()

        fun parseEps(season: Int, a: JSONArray) {
            val eps = objs(a).map {
                val ei = it.optJSONObject("info") ?: JSONObject()
                Episode(
                    it.optString("id"), it.optString("title"),
                    it.optInt("season", season), it.optInt("episode_num"),
                    it.optString("container_extension", "mp4").ifEmpty { "mp4" },
                    ei.optString("movie_image"), ei.optString("plot")
                )
            }.sortedBy { it.num }
            if (eps.isNotEmpty()) seasons[eps.first().season] = eps
        }

        when (val e = o.opt("episodes")) {
            is JSONObject -> e.keys().forEach { k -> e.optJSONArray(k)?.let { parseEps(k.toIntOrNull() ?: 0, it) } }
            is JSONArray -> for (i in 0 until e.length()) e.optJSONArray(i)?.let { parseEps(i + 1, it) }
        }
        val date = info.optString("releaseDate").ifEmpty { info.optString("release_date") }
        return Detail(
            title = info.optString("name"),
            poster = info.optString("cover"),
            backdrop = backdrop(info),
            plot = info.optString("plot"),
            rating = info.optString("rating"),
            year = date.take(4),
            genre = info.optString("genre"),
            seasons = seasons
        )
    }

    // ------------------------------------------------------------------ URLs de vídeo
    fun liveUrl(ch: LiveStream) =
        if (Prefs.isM3u && ch.url.isNotEmpty()) ch.url
        else "$base/live/${Prefs.user}/${Prefs.pass}/${ch.id}.${Prefs.liveFormat}"
    fun movieUrl(id: Int, ext: String) = "$base/movie/${Prefs.user}/${Prefs.pass}/$id.$ext"
    fun episodeUrl(id: String, ext: String) = "$base/series/${Prefs.user}/${Prefs.pass}/$id.$ext"
}
