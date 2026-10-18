package com.novatv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.novatv.app.BuildConfig
import com.novatv.app.R
import com.novatv.app.data.M3u
import com.novatv.app.data.Prefs
import com.novatv.app.data.Xtream
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etServer: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etM3u: EditText
    private lateinit var tabXtream: TextView
    private lateinit var tabM3u: TextView
    private lateinit var btn: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvError: TextView

    private var m3uMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        if (Prefs.isLogged()) {
            openMain()
            return
        }
        immersive()
        setContentView(R.layout.activity_login)
        etServer = findViewById(R.id.etServer)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etM3u = findViewById(R.id.etM3u)
        tabXtream = findViewById(R.id.tabXtream)
        tabM3u = findViewById(R.id.tabM3u)
        btn = findViewById(R.id.btnLogin)
        progress = findViewById(R.id.progress)
        tvError = findViewById(R.id.tvError)

        etServer.setText(Prefs.server.ifEmpty { BuildConfig.DEFAULT_SERVER })
        etUser.setText(Prefs.user)
        etM3u.setText(Prefs.m3uUrl)

        tabXtream.setOnClickListener { setMode(false) }
        tabM3u.setOnClickListener { setMode(true) }
        setMode(Prefs.isM3u)

        etPass.imeOptions = EditorInfo.IME_ACTION_DONE
        etPass.setOnEditorActionListener { _, _, _ -> doLogin(); true }
        etM3u.imeOptions = EditorInfo.IME_ACTION_DONE
        etM3u.setOnEditorActionListener { _, _, _ -> doLogin(); true }
        btn.setOnClickListener { doLogin() }
    }

    private fun setMode(m3u: Boolean) {
        m3uMode = m3u
        tabXtream.isSelected = !m3u
        tabM3u.isSelected = m3u
        tvError.visibility = View.GONE
        etM3u.visibility = if (m3u) View.VISIBLE else View.GONE
        val x = if (m3u) View.GONE else View.VISIBLE
        etServer.visibility = if (!m3u && BuildConfig.DEFAULT_SERVER.isEmpty()) View.VISIBLE else View.GONE
        etUser.visibility = x
        etPass.visibility = x
    }

    private fun doLogin() {
        tvError.visibility = View.GONE
        if (m3uMode) {
            val link = etM3u.text.toString().trim()
            if (!link.startsWith("http")) {
                showError("Cole o link completo da lista (começando com http)")
                return
            }
            // Link de painel Xtream? Usa a API Xtream (mais rápida e com guia de programação)
            val cred = M3u.xtreamCredentials(link)
            if (cred != null) {
                loginXtream(cred.first, cred.second, cred.third)
            } else {
                loginM3u(link)
            }
        } else {
            var server = etServer.text.toString().trim()
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                showError("Preencha todos os campos")
                return
            }
            if (!server.startsWith("http")) server = "http://$server"
            loginXtream(server, user, pass)
        }
    }

    private fun loginXtream(server: String, user: String, pass: String) {
        Prefs.loginType = "xtream"
        Prefs.server = server
        Prefs.user = user
        Prefs.pass = pass
        Xtream.clearCache()
        loading(true)
        lifecycleScope.launch {
            try {
                val info = Xtream.login()
                Prefs.expDate = info.optString("exp_date").let { if (it == "null") "" else it }
                Prefs.maxConn = info.optString("max_connections")
                openMain()
            } catch (e: Exception) {
                Prefs.pass = ""
                showError(e.message ?: "Falha ao conectar")
                loading(false)
            }
        }
    }

    private fun loginM3u(link: String) {
        Prefs.loginType = "m3u"
        Prefs.m3uUrl = link
        Prefs.user = "Lista M3U"
        Prefs.expDate = ""
        Xtream.clearCache()
        loading(true)
        lifecycleScope.launch {
            try {
                val total = Xtream.loginM3u()
                Prefs.pass = "m3u"
                toast("$total itens carregados")
                openMain()
            } catch (e: Exception) {
                Prefs.pass = ""
                showError(e.message ?: "Falha ao carregar a lista")
                loading(false)
            } catch (e: OutOfMemoryError) {
                Prefs.pass = ""
                showError("Lista grande demais para este aparelho")
                loading(false)
            }
        }
    }

    private fun loading(on: Boolean) {
        btn.isEnabled = !on
        btn.text = if (on) "" else "Entrar"
        progress.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
