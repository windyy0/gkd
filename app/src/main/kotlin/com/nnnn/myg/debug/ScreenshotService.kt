package com.nnnn.myg.debug

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import com.nnnn.myg.app
import com.nnnn.myg.notif.notifyService
import com.nnnn.myg.notif.screenshotNotif
import com.nnnn.myg.util.ScreenshotUtil
import com.nnnn.myg.util.componentName

class ScreenshotService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        screenshotNotif.notifyService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return super.onStartCommand(intent, flags, startId)
        } finally {
            intent?.let {
                screenshotUtil?.destroy()
                screenshotUtil = ScreenshotUtil(this, intent)
                LogUtils.d("screenshot restart")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.value = false
        screenshotUtil?.destroy()
        screenshotUtil = null
    }

    companion object {
        suspend fun screenshot() = screenshotUtil?.execute()

        @SuppressLint("StaticFieldLeak")
        private var screenshotUtil: ScreenshotUtil? = null

        fun start(context: Context = app, intent: Intent) {
            intent.component = ScreenshotService::class.componentName
            context.startForegroundService(intent)
        }

        val isRunning = MutableStateFlow(false)
        fun stop(context: Context = app) {
            context.stopService(Intent(context, ScreenshotService::class.java))
        }
    }
}