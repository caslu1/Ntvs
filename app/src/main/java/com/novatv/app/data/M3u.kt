package com.novatv.app.data

import android.net.Uri
import java.io.BufferedReader

/** Leitor de listas M3U / M3U8 (lê linha a linha para não estourar memória em TV boxes). */
object M3u {

    class Data(
        val liveCats: List<Category>,
        val live: List<LiveStream>,
        val vodCats: List<Category>,
        val movies: List<VodItem>,
        val seriesCats: List<Category>,
        val series: List<VodItem>,
        val episodes: Map<Int, List<Episode>>
    ) {
        val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
    }

    private val attrRx = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val episodeRx = Regex("^(.*?)[\\s._\\-|]*S(\\d{1,2})\\s*E(\\d{1,4})", RegexOption.IGNORE_CASE)
    private val videoExt = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "m4v")

    /** Se o link M3U for de um painel Xtream (get.php?username=..&password=..), devolve servidor, usuário e senha. */
    fun xtreamCredentials(link: String): Triple<String, String, String>? = try {
        val u = Uri.parse(link.trim())
        val user = u.getQueryParameter("username")
        val pass = u.getQueryParameter("password")
        val host = u.host
        if (user.isNullOrEmpty() || pass.isNullOrEmpty() || host.isNullOrEmpty()) null
        else {
            val port = if (u.port > 0) ":${u.port}" else ""
            Triple("${u.scheme ?: "http"}://$host$port", user, pass)
        }
    } catch (e: Exception) { null }

    private class Cats {
        val map = LinkedHashMap<String, String>()
        fun id(name: String): String = map.getOrPut(name) { (map.size + 1).toString() }
        fun list() = map.map { (name, id) -> Category(id, name) }
    }

    fun parse(reader: BufferedReader): Data {
        val liveCats = Cats(); val vodCats = Cats(); val seriesCats = Cats()
        val live = ArrayList<LiveStream>()
        val movies = ArrayList<VodItem>()
        val seriesIds = LinkedHashMap<String, Int>()
        val seriesItems = ArrayList<VodItem>()
        val episodes = HashMap<Int, MutableList<Episode>>()

        var attrs: Map<String, String> = emptyMap()
        var name = ""
        var group = ""
        var pending = false
        var n = 0

        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", true) -> {
                    attrs = attrRx.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val lastQuote = line.lastIndexOf('"')
                    val comma = line.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
                    name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    if (name.isEmpty()) name = attrs["tvg-name"] ?: "Sem nome"
                    group = attrs["group-title"] ?: ""
                    pending = true
                }
                line.startsWith("#EXTGRP:", true) -> {
                    if (group.isEmpty()) group = line.substring(8).trim()
                }
                line.startsWith("#") -> {}
                pending -> {
                    pending = false
                    n++
                    val url = line
                    val logo = attrs["tvg-logo"] ?: ""
                    val lower = url.lowercase()
                    val ext = lower.substringBefore('?').substringAfterLast('.', "").take(4)
                    val grp = group.ifEmpty { "Outros" }
                    val ep = episodeRx.find(name)
                    val isSeries = lower.contains("/series/") || (ep != null && ext in videoExt)
                    val isMovie = !isSeries && (lower.contains("/movie/") || ext in videoExt)

                    when {
                        isSeries -> {
                            val showName = ep?.groupValues?.get(1)?.trim()?.ifEmpty { null } ?: name
                            val key = "$grp|${showName.lowercase()}"
                            val sid = seriesIds.getOrPut(key) {
                                val id = seriesIds.size + 1
                                seriesItems.add(VodItem(id, showName, logo, "", "", seriesCats.id(grp), n.toLong(), true))
                                id
                            }
                            val season = ep?.groupValues?.get(2)?.toIntOrNull() ?: 1
                            val num = ep?.groupValues?.get(3)?.toIntOrNull() ?: ((episodes[sid]?.size ?: 0) + 1)
                            episodes.getOrPut(sid) { ArrayList() }.add(
                                Episode(n.toString(), name, season, num, ext.ifEmpty { "mp4" }, logo, "", url)
                            )
                        }
                        isMovie -> movies.add(
                            VodItem(n, name, logo, "", ext.ifEmpty { "mp4" }, vodCats.id(grp), n.toLong(), false, url)
                        )
                        else -> live.add(LiveStream(n, name, logo, liveCats.id(grp), live.size + 1, url))
                    }
                    group = ""
                }
            }
        }
        return Data(
            liveCats.list(), live, vodCats.list(), movies, seriesCats.list(), seriesItems,
            episodes.mapValues { (_, v) -> v.sortedWith(compareBy({ it.season }, { it.num })) }
        )
    }
}
