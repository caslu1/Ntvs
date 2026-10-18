package com.novatv.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.novatv.app.BuildConfig
import com.novatv.app.R
import com.novatv.app.data.Parental
import com.novatv.app.data.Prefs
import com.novatv.app.data.Xtream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var list: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.list)
        build()
    }

    private fun dialog() = AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)

    private fun row(title: String, value: String, onClick: () -> Unit) {
        val v = layoutInflater.inflate(R.layout.item_setting, list, false)
        v.findViewById<TextView>(R.id.title).text = title
        v.findViewById<TextView>(R.id.value).text = value
        v.setOnClickListener { onClick() }
        list.addView(v)
    }

    private fun choose(title: String, options: Array<String>, current: Int, onPick: (Int) -> Unit) {
        dialog().setTitle(title)
            .setSingleChoiceItems(options, current) { d, which -> onPick(which); d.dismiss(); build() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun askPin(title: String, onOk: (String) -> Unit) {
        val et = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN de 4 dígitos"
        }
        dialog().setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun build() {
        val focusIdx = (0 until list.childCount).indexOfFirst { list.getChildAt(it).hasFocus() }
        list.removeAllViews()
        buildRows()
        if (focusIdx in 0 until list.childCount) list.getChildAt(focusIdx).requestFocus()
    }

    private fun buildRows() {
        val ctx = requireContext()

        // Conta
        val exp = Prefs.expDate.toLongOrNull()
        val expTxt = if (exp != null && exp > 0)
            SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(exp * 1000)) else "Ilimitado"
        row("Conta: ${Prefs.user}", "Vence: $expTxt") {}

        val fmts = arrayOf("TS (padrão)", "HLS (m3u8)")
        val fmtIdx = if (Prefs.liveFormat == "m3u8") 1 else 0
        row("Formato do ao vivo", fmts[fmtIdx]) {
            choose("Formato do ao vivo", fmts, fmtIdx) { Prefs.liveFormat = if (it == 1) "m3u8" else "ts" }
        }

        val players = arrayOf("ExoPlayer (interno)", "Player externo (VLC, MX...)")
        val plIdx = if (Prefs.externalPlayer) 1 else 0
        row("Player preferido", players[plIdx]) {
            choose("Player preferido", players, plIdx) { Prefs.externalPlayer = it == 1 }
        }

        val sorts = arrayOf("Padrão", "A-Z", "Mais recentes")
        val sortKeys = arrayOf("padrao", "az", "recentes")
        val sIdx = sortKeys.indexOf(Prefs.vodSort).coerceAtLeast(0)
        row("Ordenação de VODs", sorts[sIdx]) {
            choose("Ordenação de VODs", sorts, sIdx) { Prefs.vodSort = sortKeys[it] }
        }

        row("Abrir aplicativo automaticamente ao ligar", if (Prefs.autoStart) "Ligado" else "Desligado") {
            Prefs.autoStart = !Prefs.autoStart
            build()
        }

        val pinSet = Prefs.pin.isNotEmpty()
        row("Controle dos pais", if (!pinSet) "Desativado" else if (Parental.unlocked) "Desbloqueado" else "Ativado") {
            if (!pinSet) {
                askPin("Criar PIN (oculta categorias adultas)") { p ->
                    if (p.length >= 4) {
                        Prefs.pin = p; Parental.unlocked = false; Xtream.clearCache()
                        ctx.toast("Controle dos pais ativado"); build()
                    } else ctx.toast("O PIN precisa ter 4 dígitos")
                }
            } else {
                val opts = arrayOf("Desbloquear nesta sessão", "Remover PIN")
                dialog().setTitle("Controle dos pais").setItems(opts) { _, which ->
                    askPin("Digite o PIN") { p ->
                        if (p != Prefs.pin) { ctx.toast("PIN incorreto"); return@askPin }
                        if (which == 0) Parental.unlocked = true else Prefs.pin = ""
                        Xtream.clearCache()
                        build()
                    }
                }.show()
            }
        }

        row("Atualizar listas", "") {
            Xtream.clearCache()
            ctx.toast("Listas serão recarregadas")
        }

        row("Limpar histórico e cache", "") {
            dialog().setTitle("Limpar histórico e cache?")
                .setPositiveButton("Limpar") { _, _ ->
                    Prefs.clearHistory(); Xtream.clearCache()
                    ctx.cacheDir.deleteRecursively()
                    ctx.toast("Pronto")
                }
                .setNegativeButton("Cancelar", null).show()
        }

        row("Informações do sistema", "${Build.MANUFACTURER}/${Build.MODEL}/${Build.VERSION.SDK_INT}") {}
        row("Versão do app", BuildConfig.VERSION_NAME) {}

        row("Desconectar", "") {
            dialog().setTitle("Sair da conta?")
                .setPositiveButton("Sair") { _, _ ->
                    Prefs.logout(); Xtream.clearCache()
                    startActivity(Intent(ctx, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
                .setNegativeButton("Cancelar", null).show()
        }
    }
}
