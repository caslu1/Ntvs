package com.novatv.app.data

import org.json.JSONObject

data class Category(val id: String, val name: String)

data class LiveStream(
    val id: Int,
    val name: String,
    val icon: String,
    val categoryId: String,
    val num: Int,
    val url: String = ""
)

data class VodItem(
    val id: Int,
    val name: String,
    val poster: String,
    val rating: String,
    val ext: String,
    val categoryId: String,
    val added: Long,
    val isSeries: Boolean,
    val url: String = ""
)

data class EpgItem(val title: String, val start: Long, val end: Long) {
    val isNow: Boolean
        get() {
            val n = System.currentTimeMillis()
            return n in start until end
        }
}

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val num: Int,
    val ext: String,
    val image: String,
    val plot: String,
    val url: String = ""
)

data class Detail(
    val title: String,
    val poster: String,
    val backdrop: String,
    val plot: String,
    val rating: String,
    val year: String,
    val genre: String,
    val streamId: Int = 0,
    val ext: String = "mp4",
    val seasons: Map<Int, List<Episode>> = emptyMap(),
    val url: String = ""
)

data class HistoryItem(
    val type: String,      // live | movie | series
    val id: String,        // id do canal / filme / série
    val name: String,
    val image: String,
    val url: String,
    val subtitle: String,  // ex: S02E03
    val position: Long,
    val duration: Long,
    val time: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type); put("id", id); put("name", name); put("image", image)
        put("url", url); put("subtitle", subtitle); put("position", position)
        put("duration", duration); put("time", time)
    }

    companion object {
        fun from(o: JSONObject) = HistoryItem(
            o.optString("type"), o.optString("id"), o.optString("name"),
            o.optString("image"), o.optString("url"), o.optString("subtitle"),
            o.optLong("position"), o.optLong("duration"), o.optLong("time")
        )
    }
}
