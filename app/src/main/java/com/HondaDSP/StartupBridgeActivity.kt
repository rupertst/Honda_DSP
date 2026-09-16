package com.HondaDSP

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class StartupBridgeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startHondaDspService()
        launchCarLink()
        finish()
    }

    private fun startHondaDspService() {
        Intent(this, HondaDspService::class.java).also {
            it.action = Actions.START.name
            startForegroundService(it)
        }
    }

    private fun launchCarLink() {
        val launchIntent = packageManager.getLaunchIntentForPackage(CAR_LINK_PACKAGE) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    companion object {
        private const val CAR_LINK_PACKAGE = "com.syu.carlink"
    }
}
