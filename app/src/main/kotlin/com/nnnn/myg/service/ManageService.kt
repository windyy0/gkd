package com.nnnn.myg.service

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nnnn.myg.app
import com.nnnn.myg.notif.abNotif
import com.nnnn.myg.notif.notifyService
import com.nnnn.myg.permission.notificationState
import com.nnnn.myg.util.actionCountFlow
import com.nnnn.myg.util.getSubsStatus
import com.nnnn.myg.util.ruleSummaryFlow
import com.nnnn.myg.util.storeFlow

class ManageService : Service() {
    override fun onBind(intent: Intent?) = null
    val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        abNotif.notifyService(this)
        scope.launch {
            combine(
                A11yService.isRunning,
                storeFlow,
                ruleSummaryFlow,
                actionCountFlow,
            ) { abRunning, store, ruleSummary, count ->
                if (!abRunning) return@combine "无障碍未授权"
                if (!store.enableMatch) return@combine "暂停规则匹配"
                if (store.useCustomNotifText) {
                    return@combine store.customNotifText
                        .replace("\${i}", ruleSummary.globalGroups.size.toString())
                        .replace("\${k}", ruleSummary.appSize.toString())
                        .replace("\${u}", ruleSummary.appGroupSize.toString())
                        .replace("\${n}", count.toString())
                }
                return@combine getSubsStatus(ruleSummary, count)
            }.debounce(500L).stateIn(scope, SharingStarted.Eagerly, "").collect { text ->
                abNotif.copy(text = text).notifyService(this@ManageService)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.value = false
    }

    companion object {
        val isRunning = MutableStateFlow(false)

        fun start() {
            if (!notificationState.checkOrToast()) return
            app.startForegroundService(Intent(app, ManageService::class.java))
        }

        fun stop() {
            app.stopService(Intent(app, ManageService::class.java))
        }

        fun autoStart() {
            // 在[系统重启]/[被其它高权限应用重启]时自动打开通知栏状态服务
            if (storeFlow.value.enableStatusService
                && !isRunning.value
                && notificationState.updateAndGet()
            ) {
                start()
            }
        }
    }
}

