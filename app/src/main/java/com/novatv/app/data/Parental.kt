package com.novatv.app.data

object Parental {
    var unlocked = false
    private val words = listOf("adult", "xxx", "+18", "18+", "adulto", "porn", "erotic", "erótic")

    fun allowed(name: String): Boolean {
        if (Prefs.pin.isEmpty() || unlocked) return true
        val n = name.lowercase()
        return words.none { n.contains(it) }
    }
}
