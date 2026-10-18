package com.novatv.app.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.novatv.app.R
import com.novatv.app.data.Detail
import com.novatv.app.data.Episode
import com.novatv.app.data.Prefs
import com.novatv.app.data.Xtream
import kotlinx.coroutines.launch
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    private var type = "movie"
    private var id = 0
    private var detail: Detail? = null
    private var posterUrl = ""

    private lateinit var btnPlay: TextView
    private lateinit var btnFav: TextView
    private lateinit var episodesBox: LinearLayout

    private val favKey get() = "$type:$id"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        setContentView(R.layout.activity_detail)
        type = intent.getStringExtra("type") ?: "movie"
        id = intent.getIntExtra("id", 0)
        posterUrl = intent.getStringExtra("poster") ?: ""

        findViewById<TextView>(R.id.title).text = intent.getStringExtra("name") ?: ""
        findViewById<ImageView>(R.id.poster).load(posterUrl)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        btnPlay = findViewById(R.id.btnPlay)
        btnFav = findViewById(R.id.btnFav)
        episodesBox = findViewById(R.id.episodes)

        updateFav()
        btnFav.setOnClickListener {
            val added = Prefs.toggleFav(favKey)
            toast(if (added) "Adicionado aos favoritos" else "Removido dos favoritos")
            updateFav()
        }
        btnPlay.setOnClickListener { play() }
        btnPlay.requestFocus()

        val progress = findViewById<View>(R.id.progress)
        lifecycleScope.launch {
            try {
                val d = if (type == "series") Xtream.seriesDetail(id) else Xtream.movieDetail(id)
                detail = d
                render(d)
            } catch (e: Exception) {
                toast("Não foi possível carregar os detalhes")
            }
            progress.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        updatePlayLabel()
        val d = detail
        if (d != null && type == "series" && currentSeason >= 0) {
            shownSeason = -1
            showEpisodes(d, currentSeason)
        }
    }

    private fun updateFav() {
        btnFav.text = if (Prefs.isFav(favKey)) "Remover" else "Favoritos"
    }

    private fun updatePlayLabel() {
        val h = Prefs.findHistory(type, id.toString())
        btnPlay.text = when {
            h == null || h.position < 10_000 -> "Reproduzir"
            type == "series" -> "Continuar ${h.subtitle}"
            else -> "Continuar"
        }
    }

    private fun render(d: Detail) {
        if (d.title.isNotEmpty()) findViewById<TextView>(R.id.title).text = d.title
        if (d.poster.isNotEmpty()) {
            posterUrl = d.poster
            findViewById<ImageView>(R.id.poster).load(d.poster)
        }
        findViewById<ImageView>(R.id.backdrop).load(d.backdrop.ifEmpty { posterUrl })
        val rating = d.rating.toDoubleOrNull()
        val meta = listOfNotNull(
            if (rating != null && rating > 0) "★ ${String.format(Locale.US, "%.1f", rating)}" else null,
            d.year.ifEmpty { null },
            d.genre.ifEmpty { null }
        )
        findViewById<TextView>(R.id.meta).text = meta.joinToString("   |   ")
        findViewById<TextView>(R.id.plot).text = d.plot

        if (type == "series" && d.seasons.isNotEmpty()) setupSeasons(d)
    }

    private fun setupSeasons(d: Detail) {
        val rv = findViewById<RecyclerView>(R.id.rvSeasons)
        rv.visibility = View.VISIBLE
        rv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rv.itemAnimator = null
        val seasons = d.seasons.keys.toList()
        lateinit var ad: SimpleAdapter<Int>
        ad = SimpleAdapter<Int>(R.layout.item_season,
            bind = { v, s, _ -> (v as TextView).text = "Temporada $s" },
            onClick = { _, s, pos -> ad.select(pos); showEpisodes(d, s) },
            onFocus = { s, pos -> ad.select(pos); showEpisodes(d, s) })
        rv.adapter = ad
        ad.submit(seasons)

        // Abre na temporada do último episódio assistido
        val last = Prefs.findHistory("series", id.toString())?.subtitle ?: ""
        val lastSeason = Regex("S(\\d+)").find(last)?.groupValues?.get(1)?.toIntOrNull()
        val start = (if (lastSeason == null) -1 else seasons.indexOf(lastSeason)).let { if (it < 0) 0 else it }
        ad.select(start)
        showEpisodes(d, seasons[start])
    }

    private var shownSeason = -1
    private var currentSeason = -1

    private fun showEpisodes(d: Detail, season: Int) {
        if (season == shownSeason) return
        shownSeason = season
        currentSeason = season
        episodesBox.removeAllViews()
        val eps = d.seasons[season] ?: return
        val watched = Prefs.findHistory("series", id.toString())
        for (ep in eps) {
            val v = layoutInflater.inflate(R.layout.item_card, episodesBox, false)
            v.findViewById<ImageView>(R.id.poster).load(ep.image.ifEmpty { d.backdrop.ifEmpty { posterUrl } })
            v.findViewById<TextView>(R.id.title).text = "${ep.num}. ${ep.title}"
            v.findViewById<TextView>(R.id.sub).text = ep.plot
            v.findViewById<TextView>(R.id.date).text =
                if (watched?.subtitle == label(ep)) "Parou em ${duration(watched.position)}" else label(ep)
            v.setOnClickListener { playEpisode(d, ep) }
            episodesBox.addView(v)
        }
    }

    private fun label(ep: Episode) = String.format(Locale.US, "S%02dE%02d", ep.season, ep.num)

    private fun play() {
        val d = detail ?: return toast("Carregando...")
        if (type == "movie") {
            val h = Prefs.findHistory("movie", id.toString())
            Launcher.vod(
                this, d.url.ifEmpty { Xtream.movieUrl(d.streamId, d.ext) }, d.title.ifEmpty { intent.getStringExtra("name") ?: "" },
                posterUrl, "movie", id.toString(), "", h?.position ?: 0L
            )
        } else {
            val h = Prefs.findHistory("series", id.toString())
            if (h != null) {
                Launcher.vod(this, h.url, h.name, h.image, "series", h.id, h.subtitle, h.position)
            } else {
                val first = d.seasons.values.firstOrNull()?.firstOrNull() ?: return toast("Nenhum episódio")
                playEpisode(d, first)
            }
        }
    }

    private fun playEpisode(d: Detail, ep: Episode) {
        val lbl = label(ep)
        val h = Prefs.findHistory("series", id.toString())
        val start = if (h != null && h.subtitle == lbl) h.position else 0L
        Launcher.vod(this, ep.url.ifEmpty { Xtream.episodeUrl(ep.id, ep.ext) }, d.title, posterUrl, "series", id.toString(), lbl, start)
    }
}
