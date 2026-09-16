package com.HondaDSP

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        log(context, "StartReceiver received action=$action")
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Intent(context, HondaDspService::class.java).also {
                it.action = Actions.START.name
                log(context, "StartReceiver starting HondaDspService action=${it.action}")
                context.startForegroundService(it)
            }
        }
    }
}
