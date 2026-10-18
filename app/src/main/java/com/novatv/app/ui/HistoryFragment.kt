package com.novatv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.novatv.app.R
import com.novatv.app.data.HistoryItem
import com.novatv.app.data.LiveStream
import com.novatv.app.data.Prefs

class HistoryFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_history, container, false)

    override fun onResume() {
        super.onResume()
        build(requireView())
    }

    private fun build(view: View) {
        val list = view.findViewById<LinearLayout>(R.id.list)
        while (list.childCount > 1) list.removeViewAt(1)
        val history = Prefs.history()
        view.findViewById<View>(R.id.tvEmpty).visibility = if (history.isEmpty()) View.VISIBLE else View.GONE

        val sections = listOf("movie" to "Filmes", "series" to "Séries", "live" to "Canais")
        for ((type, label) in sections) {
            val items = history.filter { it.type == type }
            if (items.isEmpty()) continue

            val header = TextView(requireContext()).apply {
                text = label
                textSize = 19f
                setPadding(0, dp(16), 0, dp(4))
            }
            list.addView(header)

            val rv = RecyclerView(requireContext())
            rv.layoutManager = GridLayoutManager(requireContext(), 2)
            rv.isNestedScrollingEnabled = false
            rv.itemAnimator = null
            val ad = SimpleAdapter<HistoryItem>(R.layout.item_card,
                bind = { v, it, _ ->
                    v.findViewById<ImageView>(R.id.poster).load(it.image)
                    v.findViewById<TextView>(R.id.title).text = it.name
                    v.findViewById<TextView>(R.id.sub).text =
                        if (it.type == "live") "Ao vivo" else "${duration(it.position)} ${it.subtitle}"
                    v.findViewById<TextView>(R.id.date).text = dateTimeLabel(it.time)
                },
                onClick = { _, it, pos -> open(items, it, pos) })
            rv.adapter = ad
            ad.submit(items)
            list.addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun open(items: List<HistoryItem>, h: HistoryItem, pos: Int) {
        if (h.type == "live") {
            val channels = items.map { LiveStream(it.id.toIntOrNull() ?: 0, it.name, it.image, "", 0, it.url) }
            Launcher.live(requireContext(), channels, pos)
        } else {
            Launcher.vod(requireContext(), h.url, h.name, h.image, h.type, h.id, h.subtitle, h.position)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
