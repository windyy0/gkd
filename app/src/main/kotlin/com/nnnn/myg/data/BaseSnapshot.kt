package com.nnnn.myg.data

interface BaseSnapshot {
    val id: Long

    val appId: String?
    val activityId: String?
    val appName: String?
    val appVersionCode: Long?
    val appVersionName: String?

    val screenHeight: Int
    val screenWidth: Int
    val isLandscape: Boolean

}