package com.nnnn.myg.data

import com.blankj.utilcode.util.ScreenUtils
import kotlinx.serialization.Serializable
import com.nnnn.myg.app
import com.nnnn.myg.service.A11yService
import com.nnnn.myg.service.getAndUpdateCurrentRules
import com.nnnn.myg.service.safeActiveWindow

@Serializable
data class ComplexSnapshot(
    override val id: Long,

    override val appId: String?,
    override val activityId: String?,

    override val screenHeight: Int,
    override val screenWidth: Int,
    override val isLandscape: Boolean,

    val appInfo: com.nnnn.myg.data.AppInfo? = appId?.let { app.packageManager.getPackageInfo(appId, 0)?.toAppInfo() },
    val gkdAppInfo: com.nnnn.myg.data.AppInfo? = com.nnnn.myg.data.selfAppInfo,
    val device: com.nnnn.myg.data.DeviceInfo = com.nnnn.myg.data.DeviceInfo.Companion.instance,

    @Deprecated("use appInfo")
    override val appName: String? = appInfo?.name,
    @Deprecated("use appInfo")
    override val appVersionCode: Long? = appInfo?.versionCode,
    @Deprecated("use appInfo")
    override val appVersionName: String? = appInfo?.versionName,

    val nodes: List<com.nnnn.myg.data.NodeInfo>,
) : com.nnnn.myg.data.BaseSnapshot


fun createComplexSnapshot(): com.nnnn.myg.data.ComplexSnapshot {
    val currentAbNode = A11yService.instance?.safeActiveWindow
    val appId = currentAbNode?.packageName?.toString()
    val currentActivityId = getAndUpdateCurrentRules().topActivity.activityId

    return com.nnnn.myg.data.ComplexSnapshot(
        id = System.currentTimeMillis(),

        appId = appId,
        activityId = currentActivityId,

        screenHeight = ScreenUtils.getScreenHeight(),
        screenWidth = ScreenUtils.getScreenWidth(),
        isLandscape = ScreenUtils.isLandscape(),

        nodes = com.nnnn.myg.data.NodeInfo.Companion.info2nodeList(currentAbNode)
    )
}

fun com.nnnn.myg.data.ComplexSnapshot.toSnapshot(): com.nnnn.myg.data.Snapshot {
    return com.nnnn.myg.data.Snapshot(
        id = id,

        appId = appId,
        activityId = activityId,

        screenHeight = screenHeight,
        screenWidth = screenWidth,
        isLandscape = isLandscape,

        appName = appInfo?.name,
        appVersionCode = appInfo?.versionCode,
        appVersionName = appInfo?.versionName,
    )
}


