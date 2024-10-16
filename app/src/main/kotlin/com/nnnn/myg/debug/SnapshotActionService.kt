package com.nnnn.myg.debug

import android.app.Service
import android.content.Intent
import android.os.Binder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nnnn.myg.appScope
import com.nnnn.myg.util.launchTry

/**
 * https://github.com/myg-kit/myg/issues/253
 */
class SnapshotActionService : Service() {
    override fun onBind(intent: Intent?): Binder? = null
    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            delay(1000)
            stopSelf()
        }
        appScope.launchTry {
            SnapshotExt.captureSnapshot()
        }
    }
}