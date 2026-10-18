package com.novatv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.novatv.app.R
import com.novatv.app.data.Category
import com.novatv.app.data.LiveStream
import com.novatv.app.data.Prefs
import com.novatv.app.data.Xtream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class LiveFragment : Fragment() {

    data class EpgRow(val title: String, val sub: String, val header: Boolean, val now: Boolean)

    private lateinit var catAd: SimpleAdapter<Category>
    private lateinit var chAd: SimpleAdapter<LiveStream>
    private lateinit var epgAd: SimpleAdapter<EpgRow>
    private lateinit var etSearch: EditText
    private lateinit var tvEpgTitle: TextView
    private lateinit var rvChannels: RecyclerView

    private var all: List<LiveStream> = emptyList()
    private val nowCache = HashMap<Int, String>()
    private val pending = HashSet<Int>()
    private val limiter = Semaphore(4)
    private var epgJob: Job? = null
    private var catJob: Job? = null
    private var epgFor = -1
    private var currentCat = ""

    private val scope get() = viewLifecycleOwner.lifecycleScope

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_live, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val progress = view.findViewById<View>(R.id.progress)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        etSearch = view.findViewById(R.id.etSearch)
        tvEpgTitle = view.findViewById(R.id.tvEpgTitle)
        rvChannels = view.findViewById(R.id.rvChannels)

        // ----- Categorias -----
        val rvCats = view.findViewById<RecyclerView>(R.id.rvCats)
        rvCats.layoutManager = LinearLayoutManager(requireContext())
        rvCats.itemAnimator = null
        catAd = SimpleAdapter<Category>(R.layout.item_text,
            bind = { v, c, _ ->
                v.findViewById<TextView>(R.id.title).text = c.name
                val icon = v.findViewById<ImageView>(R.id.icon)
                val res = when (c.id) {
                    "__search" -> R.drawable.ic_search
                    "__fav" -> R.drawable.ic_star
                    "__all" -> R.drawable.ic_list
                    else -> 0
                }
                if (res != 0) {
                    icon.setImageDrawable(ContextCompat.getDrawable(requireContext(), res))
                    icon.visibility = View.VISIBLE
                } else icon.visibility = View.GONE
            },
            onClick = { _, c, pos -> openCategory(c, pos, true) },
            onFocus = { c, pos ->
                if (c.id != "__search") {
                    catJob?.cancel()
                    catJob = scope.launch { delay(300); openCategory(c, pos, false) }
                }
            })
        rvCats.adapter = catAd

        // ----- Canais -----
        rvChannels.layoutManager = LinearLayoutManager(requireContext())
        rvChannels.itemAnimator = null
        chAd = SimpleAdapter<LiveStream>(R.layout.item_channel,
            bind = { v, ch, pos ->
                v.findViewById<TextView>(R.id.num).text = (pos + 1).toString()
                v.findViewById<ImageView>(R.id.logo).load(ch.icon)
                v.findViewById<TextView>(R.id.title).text = ch.name
                v.findViewById<TextView>(R.id.sub).text = nowCache[ch.id] ?: ""
                v.findViewById<View>(R.id.fav).visibility =
                    if (Prefs.isFav("live:${ch.id}")) View.VISIBLE else View.GONE
                fetchNow(ch)
            },
            onClick = { v, ch, pos ->
                if (v.isInTouchMode && epgFor != ch.id) {
                    chAd.select(pos)
                    showEpg(ch, 0)
                } else {
                    Launcher.live(requireContext(), chAd.items, pos)
                }
            },
            onFocus = { ch, _ -> showEpg(ch, 450) },
            onLongClick = { ch, pos ->
                val added = Prefs.toggleFav("live:${ch.id}")
                requireContext().toast(if (added) "Adicionado aos favoritos" else "Removido dos favoritos")
                chAd.notifyItemChanged(pos)
            })
        rvChannels.adapter = chAd

        // ----- Guia (EPG) -----
        val rvEpg = view.findViewById<RecyclerView>(R.id.rvEpg)
        rvEpg.layoutManager = LinearLayoutManager(requireContext())
        rvEpg.itemAnimator = null
        epgAd = SimpleAdapter<EpgRow>(R.layout.item_epg,
            bind = { v, r, _ ->
                val t = v.findViewById<TextView>(R.id.title)
                val s = v.findViewById<TextView>(R.id.sub)
                t.text = r.title
                s.text = r.sub
                s.visibility = if (r.sub.isEmpty()) View.GONE else View.VISIBLE
                val color = when {
                    r.header -> R.color.text
                    r.now -> R.color.accent
                    else -> R.color.text_dim
                }
                t.setTextColor(ContextCompat.getColor(requireContext(), color))
                t.textSize = if (r.header) 18f else 16f
            },
            onClick = { _, _, _ -> })
        rvEpg.adapter = epgAd

        etSearch.doAfterTextChanged { txt ->
            if (currentCat != "__search") return@doAfterTextChanged
            val q = txt?.toString()?.trim()?.lowercase() ?: ""
            chAd.submit(if (q.length < 2) emptyList() else all.filter { it.name.lowercase().contains(q) })
        }

        // ----- Carregar dados -----
        scope.launch {
            try {
                val cats = Xtream.liveCategories()
                all = Xtream.liveStreams()
                progress.visibility = View.GONE
                val fixed = listOf(
                    Category("__search", "Buscar"),
                    Category("__fav", "Favoritos"),
                    Category("__all", "Tudo: A-Z")
                )
                catAd.submit(fixed + cats)
                val firstPos = if (cats.isNotEmpty()) 3 else 2
                openCategory(catAd.items[firstPos], firstPos, false)
            } catch (e: Exception) {
                progress.visibility = View.GONE
                tvEmpty.text = "Erro ao carregar canais:\n${e.message}"
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun openCategory(c: Category, pos: Int, fromClick: Boolean) {
        if (c.id == currentCat && !fromClick) return
        currentCat = c.id
        catAd.select(pos)
        if (c.id == "__search") {
            etSearch.visibility = View.VISIBLE
            etSearch.setText("")
            etSearch.requestFocus()
            chAd.submit(emptyList())
            return
        }
        etSearch.visibility = View.GONE
        val list = when (c.id) {
            "__fav" -> all.filter { Prefs.isFav("live:${it.id}") }
            "__all" -> all.sortedBy { it.name.lowercase() }
            else -> all.filter { it.categoryId == c.id }
        }
        chAd.submit(list)
        rvChannels.scrollToPosition(0)
        if (fromClick && list.isNotEmpty() && !rvChannels.isInTouchMode) {
            rvChannels.post { rvChannels.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
        }
    }

    private fun fetchNow(ch: LiveStream) {
        if (nowCache.containsKey(ch.id) || !pending.add(ch.id)) return
        scope.launch {
            val list = limiter.withPermit { Xtream.epg(ch.id, false) }
            nowCache[ch.id] = list.firstOrNull { it.isNow }?.title ?: list.firstOrNull()?.title ?: ""
            pending.remove(ch.id)
            val idx = chAd.items.indexOfFirst { it.id == ch.id }
            if (idx >= 0) chAd.notifyItemChanged(idx)
        }
    }

    private fun showEpg(ch: LiveStream, wait: Long) {
        if (epgFor == ch.id) return
        epgFor = ch.id
        tvEpgTitle.text = if (rvChannels.isInTouchMode) "${ch.name}  •  toque de novo para assistir" else ch.name
        epgJob?.cancel()
        epgJob = scope.launch {
            delay(wait)
            epgAd.submit(listOf(EpgRow("Carregando guia...", "", false, false)))
            var list = Xtream.epg(ch.id, true)
            if (list.isEmpty()) list = Xtream.epg(ch.id, false)
            val now = System.currentTimeMillis()
            val rows = ArrayList<EpgRow>()
            var lastDay = ""
            list.filter { it.end > now }.forEach { e ->
                val d = dayLabel(e.start)
                if (d != lastDay) {
                    rows.add(EpgRow(d, "", true, false))
                    lastDay = d
                }
                rows.add(EpgRow(e.title, "${hhmm(e.start)} - ${hhmm(e.end)}", false, e.isNow))
            }
            if (rows.isEmpty()) rows.add(EpgRow("Sem guia de programação", "", false, false))
            epgAd.submit(rows)
        }
    }
}
