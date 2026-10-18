package com.novatv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.novatv.app.BuildConfig
import com.novatv.app.R
import com.novatv.app.data.HistoryItem
import com.novatv.app.data.Prefs
import com.novatv.app.data.VodItem
import com.novatv.app.data.Xtream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var bannerJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvUser).text = Prefs.user
        val exp = Prefs.expDate.toLongOrNull()
        val df = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        view.findViewById<TextView>(R.id.tvDate).text =
            if (exp != null && exp > 0) "Vence em " + df.format(Date(exp * 1000)) else df.format(Date())

        setupContinue(view)
        setupRecent(view)
        setupBanner(view)
    }

    // ---------------- Banner rotativo (imagens configuradas em BANNERS_URL) ----------------
    private fun setupBanner(view: View) {
        val box = view.findViewById<View>(R.id.bannerBox)
        val title = view.findViewById<View>(R.id.tvBannerTitle)
        val img = view.findViewById<ImageView>(R.id.banner)
        val dots = view.findViewById<TextView>(R.id.bannerDots)
        box.setOnClickListener { (activity as? MainActivity)?.show(1) }

        if (BuildConfig.BANNERS_URL.isEmpty()) {
            box.visibility = View.GONE; title.visibility = View.GONE
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val urls = try {
                val a = JSONArray(Xtream.get(BuildConfig.BANNERS_URL))
                (0 until a.length()).map { i ->
                    a.optJSONObject(i)?.optString("image") ?: a.optString(i)
                }.filter { it.startsWith("http") }
            } catch (e: Exception) { emptyList() }
            if (urls.isEmpty()) {
                box.visibility = View.GONE; title.visibility = View.GONE
                return@launch
            }
            bannerJob = launch {
                var i = 0
                while (isActive) {
                    img.load(urls[i % urls.size]) { crossfade(true) }
                    dots.text = urls.indices.joinToString(" ") { if (it == i % urls.size) "●" else "○" }
                    i++
                    delay(7000)
                }
            }
        }
    }

    // ---------------- Continuar assistindo ----------------
    private fun setupContinue(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rvContinue)
        val label = view.findViewById<View>(R.id.tvContinue)
        val items = Prefs.history().filter { it.type != "live" }.take(15)
        if (items.isEmpty()) {
            rv.visibility = View.GONE; label.visibility = View.GONE
            return
        }
        rv.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        val ad = SimpleAdapter<HistoryItem>(R.layout.item_card,
            bind = { v, it, _ ->
                v.layoutParams = v.layoutParams.apply { width = dp(320) }
                v.findViewById<ImageView>(R.id.poster).load(it.image)
                v.findViewById<TextView>(R.id.title).text = it.name
                v.findViewById<TextView>(R.id.sub).text = "${duration(it.position)} ${it.subtitle}"
                v.findViewById<TextView>(R.id.date).text = dateTimeLabel(it.time)
            },
            onClick = { _, it, _ ->
                Launcher.vod(requireContext(), it.url, it.name, it.image, it.type, it.id, it.subtitle, it.position)
            })
        rv.adapter = ad
        ad.submit(items)
    }

    // ---------------- Adicionados recentemente ----------------
    private fun setupRecent(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rvRecent)
        rv.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        val ad = SimpleAdapter<VodItem>(R.layout.item_poster,
            bind = { v, it, _ ->
                v.layoutParams = v.layoutParams.apply { width = dp(125) }
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
        rv.adapter = ad
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ad.submit(Xtream.movies().sortedByDescending { it.added }.take(25))
            } catch (e: Exception) {
                context?.toast("Erro ao carregar: ${e.message}")
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        bannerJob?.cancel()
        super.onDestroyView()
    }
}
