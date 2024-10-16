package com.nnnn.myg

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.hjq.toast.Toaster
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import com.nnnn.myg.data.selfAppInfo
import com.nnnn.myg.debug.clearHttpSubs
import com.nnnn.myg.notif.initChannel
import com.nnnn.myg.permission.shizukuOkState
import com.nnnn.myg.service.A11yService
import com.nnnn.myg.util.SafeR
import com.nnnn.myg.util.componentName
import com.nnnn.myg.util.initAppState
import com.nnnn.myg.util.initFolder
import com.nnnn.myg.util.initStore
import com.nnnn.myg.util.initSubsState
import com.nnnn.myg.util.launchTry
import com.nnnn.myg.util.setReactiveToastStyle
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku


val appScope by lazy { MainScope() }

private lateinit var innerApp: Application
val app: Application
    get() = innerApp

val applicationInfo by lazy {
    app.packageManager.getApplicationInfo(
        app.packageName,
        PackageManager.GET_META_DATA
    )
}

data object META {
    val channel by lazy { applicationInfo.metaData.getString("channel")!! }
    val commitId by lazy { applicationInfo.metaData.getString("commitId")!! }
    val commitUrl by lazy { "https://github.com/gkd-kit/gkd/commit/$commitId" }
    val commitTime by lazy { applicationInfo.metaData.getLong("commitTime") }
    val updateEnabled by lazy { applicationInfo.metaData.getBoolean("updateEnabled") }
    val debuggable by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    val versionCode by lazy { selfAppInfo.versionCode.toInt() }
    val versionName by lazy { selfAppInfo.versionName!! }
    val appId by lazy { app.packageName!! }
    val appName by lazy { app.getString(SafeR.app_name) }
}

class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun onCreate() {
        super.onCreate()
        innerApp = this
        Utils.init(this)

        val errorHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            LogUtils.d("UncaughtExceptionHandler", t, e)
            errorHandler?.uncaughtException(t, e)
        }
        MMKV.initialize(this)

        Toaster.init(this)
        setReactiveToastStyle()

        LogUtils.getConfig().apply {
            setConsoleSwitch(META.debuggable)
            saveDays = 7
            isLog2FileSwitch = true
        }
        LogUtils.d(
            "META", META
        )
        initFolder()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                LogUtils.d("onActivityCreated", activity, savedInstanceState)
            }

            override fun onActivityStarted(activity: Activity) {
                LogUtils.d("onActivityStarted", activity)
            }

            override fun onActivityResumed(activity: Activity) {
                LogUtils.d("onActivityResumed", activity)
            }

            override fun onActivityPaused(activity: Activity) {
                LogUtils.d("onActivityPaused", activity)
            }

            override fun onActivityStopped(activity: Activity) {
                LogUtils.d("onActivityStopped", activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                LogUtils.d("onActivitySaveInstanceState", activity, outState)
            }

            override fun onActivityDestroyed(activity: Activity) {
                LogUtils.d("onActivityDestroyed", activity)
            }
        })
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    a11yServiceEnabledFlow.value = getA11yServiceEnabled()
                }
            }
        )
        Shizuku.addBinderReceivedListener {
            appScope.launchTry(Dispatchers.IO) {
                shizukuOkState.updateAndGet()
            }
        }
        Shizuku.addBinderDeadListener {
            shizukuOkState.stateFlow.value = false
        }
        appScope.launchTry(Dispatchers.IO) {
            initStore()
            initAppState()
            initSubsState()
            initChannel()
            clearHttpSubs()
            syncFixState()
        }
    }
}

val a11yServiceEnabledFlow by lazy { MutableStateFlow(getA11yServiceEnabled()) }
private fun getA11yServiceEnabled(): Boolean {
    val value = try {
        Settings.Secure.getString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    } catch (_: Exception) {
        null
    }
    if (value.isNullOrEmpty()) return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(value)
    val name = A11yService::class.componentName
    while (colonSplitter.hasNext()) {
        if (ComponentName.unflattenFromString(colonSplitter.next()) == name) {
            return true
        }
    }
    return false
}
