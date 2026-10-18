package com.novatv.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("novatv", Context.MODE_PRIVATE)
    }

    private fun str(k: String, d: String = "") = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var server: String
        get() = str("server")
        set(v) = put("server", v)
    var user: String
        get() = str("user")
        set(v) = put("user", v)
    var pass: String
        get() = str("pass")
        set(v) = put("pass", v)
    /** "xtream" ou "m3u" */
    var loginType: String
        get() = str("logintype", "xtream")
        set(v) = put("logintype", v)
    var m3uUrl: String
        get() = str("m3u")
        set(v) = put("m3u", v)
    val isM3u: Boolean get() = loginType == "m3u"

    var expDate: String
        get() = str("exp")
        set(v) = put("exp", v)
    var maxConn: String
        get() = str("maxconn")
        set(v) = put("maxconn", v)

    /** "ts" ou "m3u8" */
    var liveFormat: String
        get() = str("livefmt", "ts")
        set(v) = put("livefmt", v)
    var externalPlayer: Boolean
        get() = bool("extplayer", false)
        set(v) = putB("extplayer", v)
    var autoStart: Boolean
        get() = bool("autostart", false)
        set(v) = putB("autostart", v)
    /** padrao | az | recentes */
    var vodSort: String
        get() = str("vodsort", "padrao")
        set(v) = put("vodsort", v)
    var pin: String
        get() = str("pin")
        set(v) = put("pin", v)

    fun isLogged() = if (isM3u) m3uUrl.isNotEmpty() && pass == "m3u"
        else server.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()

    fun logout() {
        sp.edit().remove("user").remove("pass").remove("exp").apply()
    }

    // ---------- Favoritos (chaves: live:ID, movie:ID, series:ID) ----------
    fun favorites(): MutableSet<String> = HashSet(sp.getStringSet("favs", emptySet()) ?: emptySet())
    fun isFav(key: String) = favorites().contains(key)
    fun toggleFav(key: String): Boolean {
        val s = favorites()
        val added = if (s.remove(key)) false else { s.add(key); true }
        sp.edit().putStringSet("favs", s).apply()
        return added
    }

    // ---------- Histórico ----------
    fun history(): List<HistoryItem> = try {
        val a = JSONArray(str("history", "[]"))
        (0 until a.length()).map { HistoryItem.from(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    fun saveHistory(item: HistoryItem) {
        val list = history().filterNot { it.type == item.type && it.id == item.id }.toMutableList()
        list.add(0, item)
        val a = JSONArray()
        list.take(60).forEach { a.put(it.toJson()) }
        put("history", a.toString())
    }

    fun findHistory(type: String, id: String) = history().firstOrNull { it.type == type && it.id == id }

    fun clearHistory() = put("history", "[]")
}
