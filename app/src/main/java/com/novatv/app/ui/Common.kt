package com.novatv.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import com.novatv.app.data.LiveStream
import com.novatv.app.data.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Adapter genérico usado em todas as listas (funciona com toque e com controle remoto). */
class SimpleAdapter<T>(
    private val layout: Int,
    private val bind: (View, T, Int) -> Unit,
    private val onClick: (View, T, Int) -> Unit,
    private val onFocus: ((T, Int) -> Unit)? = null,
    private val onLongClick: ((T, Int) -> Unit)? = null
) : RecyclerView.Adapter<SimpleAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v)

    var items: List<T> = emptyList()
        private set
    var selected = -1
        private set

    fun submit(list: List<T>) {
        items = list
        selected = -1
        notifyDataSetChanged()
    }

    fun select(pos: Int) {
        val old = selected
        selected = pos
        if (old in items.indices) notifyItemChanged(old)
        if (pos in items.indices) notifyItemChanged(pos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.itemView.isSelected = position == selected
        bind(h.itemView, item, position)
        h.itemView.setOnClickListener { v ->
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(v, items[p], p)
        }
        h.itemView.setOnFocusChangeListener { _, has ->
            val p = h.bindingAdapterPosition
            if (has && p != RecyclerView.NO_POSITION) onFocus?.invoke(items[p], p)
        }
        h.itemView.setOnLongClickListener {
            val p = h.bindingAdapterPosition
            val cb = onLongClick
            if (cb != null && p != RecyclerView.NO_POSITION) { cb(items[p], p); true } else false
        }
    }
}

fun Activity.immersive() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val c = WindowInsetsControllerCompat(window, window.decorView)
    c.hide(WindowInsetsCompat.Type.systemBars())
    c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

private val hm = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
private val dayFmt = SimpleDateFormat("EEE, dd/MM", Locale("pt", "BR"))
private val dateTime = SimpleDateFormat("dd MMM HH:mm", Locale("pt", "BR"))

fun hhmm(ms: Long): String = hm.format(Date(ms))
fun dayLabel(ms: Long): String = dayFmt.format(Date(ms))
fun dateTimeLabel(ms: Long): String = dateTime.format(Date(ms))

fun duration(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

/** Lista de canais atual do player ao vivo (para trocar com ▲ ▼). */
object PlayerQueue {
    var channels: List<LiveStream> = emptyList()
}

/** Abre o vídeo no player interno ou em player externo, conforme configuração. */
object Launcher {
    fun live(ctx: Context, list: List<LiveStream>, index: Int) {
        PlayerQueue.channels = list
        val ch = list[index]
        if (Prefs.externalPlayer) {
            external(ctx, com.novatv.app.data.Xtream.liveUrl(ch), ch.name)
            return
        }
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
            putExtra("mode", "live")
            putExtra("index", index)
        })
    }

    fun vod(
        ctx: Context, url: String, title: String, image: String,
        histType: String, histId: String, subtitle: String, startPos: Long
    ) {
        if (Prefs.externalPlayer) {
            external(ctx, url, title)
            return
        }
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
            putExtra("mode", "vod")
            putExtra("url", url)
            putExtra("title", title)
            putExtra("image", image)
            putExtra("htype", histType)
            putExtra("hid", histId)
            putExtra("sub", subtitle)
            putExtra("start", startPos)
        })
    }

    private fun external(ctx: Context, url: String, title: String) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
        }
        try {
            ctx.startActivity(Intent.createChooser(i, "Abrir com"))
        } catch (e: Exception) {
            ctx.toast("Nenhum player externo instalado")
        }
    }
}
