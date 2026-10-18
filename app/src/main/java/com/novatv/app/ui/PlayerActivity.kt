package com.novatv.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.load
import com.novatv.app.R
import com.novatv.app.data.HistoryItem
import com.novatv.app.data.LiveStream
import com.novatv.app.data.Prefs
import com.novatv.app.data.Xtream
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var pv: PlayerView
    private lateinit var liveBar: View

    private var isLive = false
    private var index = 0
    private var retries = 0
    private var epgJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideBar = Runnable { liveBar.visibility = View.GONE }

    // VOD
    private var url = ""
    private var title = ""
    private var image = ""
    private var hType = ""
    private var hId = ""
    private var sub = ""
    private var startPos = 0L

    private val channels: List<LiveStream> get() = PlayerQueue.channels

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        pv = findViewById(R.id.playerView)
        liveBar = findViewById(R.id.liveBar)

        isLive = intent.getStringExtra("mode") == "live"
        if (isLive) {
            index = intent.getIntExtra("index", 0)
            if (channels.isEmpty()) { finish(); return }
            pv.useController = false
            findViewById<View>(R.id.root).setOnClickListener { toggleBar() }
            findViewById<View>(R.id.btnUp).setOnClickListener { zap(-1) }
            findViewById<View>(R.id.btnDown).setOnClickListener { zap(1) }
        } else {
            url = intent.getStringExtra("url") ?: ""
            title = intent.getStringExtra("title") ?: ""
            image = intent.getStringExtra("image") ?: ""
            hType = intent.getStringExtra("htype") ?: "movie"
            hId = intent.getStringExtra("hid") ?: ""
            sub = intent.getStringExtra("sub") ?: ""
            startPos = intent.getLongExtra("start", 0L)
            pv.useController = true
            pv.isFocusable = true
            pv.requestFocus()
        }
        initPlayer()
        play()
    }

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("NovaTV/1.0 (Linux; Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(25000)
        val ds = DefaultDataSource.Factory(this, http)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (retries < 3) {
                    retries++
                    handler.postDelayed({ reload() }, 1500)
                } else {
                    toast("Não foi possível reproduzir (${error.errorCodeName})")
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) retries = 0
                if (state == Player.STATE_ENDED && !isLive) {
                    saveVod(finished = true)
                    finish()
                }
            }
        })
        pv.player = p
        player = p
    }

    private fun play() {
        val p = player ?: return
        if (isLive) {
            val ch = channels[index]
            url = Xtream.liveUrl(ch)
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.playWhenReady = true
            showBar(ch)
            Prefs.saveHistory(
                HistoryItem("live", ch.id.toString(), ch.name, ch.icon, url, "", 0, 0, System.currentTimeMillis())
            )
        } else {
            p.setMediaItem(MediaItem.fromUri(url))
            if (startPos > 0) p.seekTo(startPos)
            p.prepare()
            p.playWhenReady = true
        }
    }

    private fun reload() {
        val p = player ?: return
        if (!isLive) startPos = p.currentPosition.coerceAtLeast(0)
        play()
    }

    private fun zap(delta: Int) {
        if (channels.isEmpty()) return
        index = (index + delta + channels.size) % channels.size
        retries = 0
        play()
    }

    // -------------------------------------------------- barra do ao vivo
    private fun toggleBar() {
        if (liveBar.visibility == View.VISIBLE) liveBar.visibility = View.GONE
        else showBar(channels[index])
    }

    private fun showBar(ch: LiveStream) {
        findViewById<TextView>(R.id.chName).text = ch.name
        findViewById<ImageView>(R.id.chLogo).load(ch.icon)
        val nowTime = findViewById<TextView>(R.id.nowTime)
        val nowTitle = findViewById<TextView>(R.id.nowTitle)
        val nextTime = findViewById<TextView>(R.id.nextTime)
        val nextTitle = findViewById<TextView>(R.id.nextTitle)
        val prog = findViewById<ProgressBar>(R.id.epgProgress)
        liveBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideBar)
        handler.postDelayed(hideBar, 6000)

        epgJob?.cancel()
        epgJob = lifecycleScope.launch {
            val list = Xtream.epg(ch.id, false)
            val nowIdx = list.indexOfFirst { it.isNow }.let { if (it < 0) 0 else it }
            val now = list.getOrNull(nowIdx)
            val next = list.getOrNull(nowIdx + 1)
            nowTime.text = now?.let { "${hhmm(it.start)}-${hhmm(it.end)}" } ?: "--:--"
            nowTitle.text = now?.title ?: "Sem informação"
            nextTime.text = next?.let { "${hhmm(it.start)}-${hhmm(it.end)}" } ?: "--:--"
            nextTitle.text = next?.title ?: ""
            prog.progress = if (now != null && now.end > now.start) {
                (((System.currentTimeMillis() - now.start) * 1000) / (now.end - now.start)).toInt().coerceIn(0, 1000)
            } else 0
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isLive) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> {
                    toggleBar(); return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // -------------------------------------------------- histórico / ciclo de vida
    private fun saveVod(finished: Boolean = false) {
        if (isLive || hId.isEmpty()) return
        val p = player ?: return
        val dur = if (p.duration == C.TIME_UNSET) 0L else p.duration
        var pos = p.currentPosition
        if (finished || (dur > 0 && pos > dur - 90_000)) pos = 0
        Prefs.saveHistory(HistoryItem(hType, hId, title, image, url, sub, pos, dur, System.currentTimeMillis()))
    }

    override fun onPause() {
        super.onPause()
        saveVod()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        immersive()
        player?.play()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }
}
