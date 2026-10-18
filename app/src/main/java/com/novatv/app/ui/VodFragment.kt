package com.novatv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.novatv.app.R
import com.novatv.app.data.Category
import com.novatv.app.data.Prefs
import com.novatv.app.data.VodItem
import com.novatv.app.data.Xtream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VodFragment : Fragment() {

    private var mode = ""
    private var items: List<VodItem> = emptyList()
    private var catId = ""
    private var loadJob: Job? = null
    private var catJob: Job? = null

    private lateinit var catAd: SimpleAdapter<Category>
    private lateinit var gridAd: SimpleAdapter<VodItem>
    private lateinit var rvGrid: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var progress: View
    private lateinit var tvEmpty: TextView
    private lateinit var tabs: List<TextView>

    private val scope get() = viewLifecycleOwner.lifecycleScope

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_vod, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        progress = view.findViewById(R.id.progress)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        etSearch = view.findViewById(R.id.etSearch)
        rvGrid = view.findViewById(R.id.rvGrid)
        tabs = listOf(R.id.tabMovies, R.id.tabSeries, R.id.tabFavs).map { view.findViewById<TextView>(it) }
        tabs[0].setOnClickListener { setMode("movie") }
        tabs[1].setOnClickListener { setMode("series") }
        tabs[2].setOnClickListener { setMode("fav") }

        val rvCats = view.findViewById<RecyclerView>(R.id.rvCats)
        rvCats.layoutManager = LinearLayoutManager(requireContext())
        rvCats.itemAnimator = null
        catAd = SimpleAdapter<Category>(R.layout.item_text,
            bind = { v, c, _ -> v.findViewById<TextView>(R.id.title).text = c.name },
            onClick = { _, c, pos -> openCat(c, pos, true) },
            onFocus = { c, pos ->
                catJob?.cancel()
                catJob = scope.launch { delay(300); openCat(c, pos, false) }
            })
        rvCats.adapter = catAd

        val glm = GridLayoutManager(requireContext(), 5)
        rvGrid.layoutManager = glm
        rvGrid.itemAnimator = null
        rvGrid.post {
            val w = rvGrid.width
            if (w > 0) glm.spanCount = maxOf(3, w / dp(128))
        }
        gridAd = SimpleAdapter<VodItem>(R.layout.item_poster,
            bind = { v, it, _ ->
                v.findViewById<ImageView>(R.id.poster).load(it.poster) { crossfade(true) }
                v.findViewById<TextView>(R.id.title).text = it.name
            },
            onClick = { _, it, _ ->
                startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
                    putExtra("type", if (it.isSeries) "series" else "movie")
                    putExtra("id", it.id)
                    putExtra("name", it.name)
                    putExtra("poster", it.poster)
                })
            })
        rvGrid.adapter = gridAd

        view.findViewById<View>(R.id.btnSearch).setOnClickListener {
            catId = "__search"
            catAd.select(-1)
            etSearch.visibility = View.VISIBLE
            etSearch.setText("")
            etSearch.requestFocus()
            show(emptyList())
        }
        etSearch.doAfterTextChanged { t ->
            if (catId != "__search") return@doAfterTextChanged
            val q = t?.toString()?.trim()?.lowercase() ?: ""
            show(if (q.length < 2) emptyList() else items.filter { it.name.lowercase().contains(q) })
        }

        setMode("movie")
    }

    override fun onResume() {
        super.onResume()
        // Atualiza a aba de favoritos ao voltar da tela de detalhes
        if (mode == "fav" && view != null) { mode = ""; setMode("fav") }
    }

    private fun setMode(m: String) {
        if (m == mode) return
        mode = m
        tabs.forEachIndexed { i, t -> t.isSelected = i == listOf("movie", "series", "fav").indexOf(m) }
        etSearch.visibility = View.GONE
        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val cats: List<Category>
                when (m) {
                    "movie" -> { cats = Xtream.vodCategories(); items = Xtream.movies() }
                    "series" -> { cats = Xtream.seriesCategories(); items = Xtream.series() }
                    else -> {
                        cats = emptyList()
                        val favs = Prefs.favorites()
                        items = Xtream.movies().filter { "movie:${it.id}" in favs } +
                                Xtream.series().filter { "series:${it.id}" in favs }
                    }
                }
                progress.visibility = View.GONE
                val fixed = if (m == "fav") listOf(Category("__all", "Todos"))
                else listOf(Category("__recent", "Destaques"), Category("__all", "Todos"))
                catAd.submit(fixed + cats)
                catId = ""
                openCat(catAd.items[0], 0, false)
            } catch (e: Exception) {
                progress.visibility = View.GONE
                tvEmpty.text = "Erro ao carregar:\n${e.message}"
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun openCat(c: Category, pos: Int, fromClick: Boolean) {
        if (c.id == catId && !fromClick) return
        catId = c.id
        catAd.select(pos)
        etSearch.visibility = View.GONE
        val list = when (c.id) {
            "__recent" -> items.sortedByDescending { it.added }.take(120)
            "__all" -> sort(items)
            else -> sort(items.filter { it.categoryId == c.id })
        }
        show(list)
        if (fromClick && list.isNotEmpty() && !rvGrid.isInTouchMode) {
            rvGrid.post { rvGrid.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
        }
    }

    private fun sort(list: List<VodItem>) = when (Prefs.vodSort) {
        "az" -> list.sortedBy { it.name.lowercase() }
        "recentes" -> list.sortedByDescending { it.added }
        else -> list
    }

    private fun show(list: List<VodItem>) {
        gridAd.submit(list)
        rvGrid.scrollToPosition(0)
        tvEmpty.text = if (mode == "fav") "Nenhum favorito ainda" else "Nada encontrado"
        tvEmpty.visibility = if (list.isEmpty() && catId != "__search") View.VISIBLE else View.GONE
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
