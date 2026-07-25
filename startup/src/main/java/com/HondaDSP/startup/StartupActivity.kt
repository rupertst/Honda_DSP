package com.HondaDSP.startup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class StartupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchHondaDspStartupBridge()
        finish()
    }

    private fun launchHondaDspStartupBridge() {
        val bridgeIntent = Intent().apply {
            setClassName("com.HondaDSP", "com.HondaDSP.StartupBridgeActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(bridgeIntent)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to launch Honda DSP startup bridge", exception)
        }
    }

    companion object {
        private const val TAG = "HondaDSPStartup"
    }
}
