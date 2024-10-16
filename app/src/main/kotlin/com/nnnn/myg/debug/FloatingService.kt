package com.nnnn.myg.debug

import android.content.Intent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.torrydo.floatingbubbleview.FloatingBubbleListener
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import com.torrydo.floatingbubbleview.service.expandable.ExpandableBubbleService
import kotlinx.coroutines.flow.MutableStateFlow
import com.nnnn.myg.app
import com.nnnn.myg.appScope
import com.nnnn.myg.notif.floatingNotif
import com.nnnn.myg.notif.notifyService
import com.nnnn.myg.permission.canDrawOverlaysState
import com.nnnn.myg.permission.notificationState
import com.nnnn.myg.util.launchTry
import kotlin.math.sqrt

class FloatingService : ExpandableBubbleService() {
    override fun configExpandedBubble() = null

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        minimize()
    }

    override fun configBubble(): BubbleBuilder {
        val builder = BubbleBuilder(this).bubbleCompose {
            Icon(
                imageVector = Icons.Default.CenterFocusWeak,
                contentDescription = "capture",
                modifier = Modifier.size(40.dp),
                tint = Color.Red
            )
        }.enableAnimateToEdge(false)

        // https://github.com/gkd-kit/gkd/issues/62
        // https://github.com/gkd-kit/gkd/issues/61
        val defaultFingerData = com.nnnn.myg.data.Tuple3(0L, 0f, 0f)
        var fingerDownData = defaultFingerData
        val maxDistanceOffset = 50
        builder.addFloatingBubbleListener(object : FloatingBubbleListener {
            override fun onFingerDown(x: Float, y: Float) {
                fingerDownData = com.nnnn.myg.data.Tuple3(System.currentTimeMillis(), x, y)
            }

            override fun onFingerMove(x: Float, y: Float) {
                if (fingerDownData === defaultFingerData) {
                    return
                }
                val dx = fingerDownData.t1 - x
                val dy = fingerDownData.t2 - y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > maxDistanceOffset) {
                    // reset
                    fingerDownData = defaultFingerData
                }
            }

            override fun onFingerUp(x: Float, y: Float) {
                if (System.currentTimeMillis() - fingerDownData.t0 < ViewConfiguration.getTapTimeout()) {
                    // is onClick
                    appScope.launchTry {
                        SnapshotExt.captureSnapshot()
                    }
                }
            }
        })
        return builder
    }


    override fun startNotificationForeground() {
        floatingNotif.notifyService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.value = false
    }

    companion object {
        val isRunning = MutableStateFlow(false)

        fun start() {
            if (!notificationState.checkOrToast()) return
            if (!canDrawOverlaysState.checkOrToast()) return
            app.startForegroundService(Intent(app, FloatingService::class.java))
        }
        fun stop() {
            app.stopService(Intent(app, FloatingService::class.java))
        }
    }
}