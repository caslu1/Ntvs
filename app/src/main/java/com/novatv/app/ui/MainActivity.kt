package com.novatv.app.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.novatv.app.R

class MainActivity : AppCompatActivity() {

    private lateinit var navs: List<ImageView>
    private var current = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        setContentView(R.layout.activity_main)
        navs = listOf(R.id.nav_home, R.id.nav_live, R.id.nav_vod, R.id.nav_history, R.id.nav_settings)
            .map { findViewById<ImageView>(it) }
        navs.forEachIndexed { i, v -> v.setOnClickListener { show(i) } }
        show(savedInstanceState?.getInt("tab") ?: 0)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val focusInSidebar = navs.any { it.hasFocus() }
                when {
                    current != 0 && focusInSidebar -> { show(0); navs[0].requestFocus() }
                    current != 0 -> navs[current].requestFocus()
                    else -> finish()
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    fun show(i: Int) {
        if (i == current && supportFragmentManager.findFragmentById(R.id.container) != null) return
        current = i
        navs.forEachIndexed { j, v -> v.isSelected = j == i }
        val f: Fragment = when (i) {
            0 -> HomeFragment()
            1 -> LiveFragment()
            2 -> VodFragment()
            3 -> HistoryFragment()
            else -> SettingsFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commitAllowingStateLoss()
    }

    /** Usado pelos fragments para devolver o foco à barra lateral (seta para a esquerda). */
    fun focusSidebar(): Boolean {
        if (current < 0) return false
        navs[current].requestFocus()
        return true
    }

    @Suppress("unused")
    fun sidebarView(): View = findViewById(R.id.sidebar)
}
