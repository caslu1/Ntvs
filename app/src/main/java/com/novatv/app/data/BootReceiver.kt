package com.novatv.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.novatv.app.ui.LoginActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Prefs.init(ctx)
        if (!Prefs.autoStart) return
        try {
            ctx.startActivity(Intent(ctx, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
