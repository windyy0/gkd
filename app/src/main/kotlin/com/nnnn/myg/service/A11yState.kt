package com.nnnn.myg.service

import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.nnnn.myg.META
import com.nnnn.myg.app
import com.nnnn.myg.appScope
import com.nnnn.myg.data.ActivityLog
import com.nnnn.myg.data.AppRule
import com.nnnn.myg.data.ClickLog
import com.nnnn.myg.data.GlobalRule
import com.nnnn.myg.data.ResolvedRule
import com.nnnn.myg.data.SubsConfig
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.isActivityVisible
import com.nnnn.myg.util.RuleSummary
import com.nnnn.myg.util.actionCountFlow
import com.nnnn.myg.util.getDefaultLauncherActivity
import com.nnnn.myg.util.launchTry
import com.nnnn.myg.util.ruleSummaryFlow
import com.nnnn.myg.util.storeFlow

data class TopActivity(
    val appId: String = "",
    val activityId: String? = null,
    val number: Int = 0
) {
    fun format(): String {
        return "${appId}/${activityId}/${number}"
    }
}

val topActivityFlow = MutableStateFlow(TopActivity())
private val activityLogMutex by lazy { Mutex() }

private var activityLogCount = 0
private var lastActivityChangeTime = 0L
fun updateTopActivity(topActivity: TopActivity) {
    val isSameActivity =
        topActivityFlow.value.appId == topActivity.appId && topActivityFlow.value.activityId == topActivity.activityId
    if (isSameActivity) {
        if (isActivityVisible() && topActivity.appId == META.appId) {
            return
        }
        if (topActivityFlow.value.number == topActivity.number) {
            return
        }
        val t = System.currentTimeMillis()
        if (t - lastActivityChangeTime < 1000) {
            return
        }
    }
    if (storeFlow.value.enableActivityLog) {
        appScope.launchTry(Dispatchers.IO) {
            activityLogMutex.withLock {
                DbSet.activityLogDao.insert(
                    ActivityLog(
                        appId = topActivity.appId,
                        activityId = topActivity.activityId
                    )
                )
                activityLogCount++
                if (activityLogCount % 100 == 0) {
                    DbSet.activityLogDao.deleteKeepLatest()
                }
            }
        }
    }
    LogUtils.d(
        "${topActivityFlow.value.format()} -> ${topActivity.format()}"
    )
    topActivityFlow.value = topActivity
    lastActivityChangeTime = System.currentTimeMillis()
}

data class ActivityRule(
    val appRules: List<AppRule> = emptyList(),
    val globalRules: List<GlobalRule> = emptyList(),
    val topActivity: TopActivity = TopActivity(),
    val ruleSummary: RuleSummary = RuleSummary(),
) {
    val currentRules = (appRules + globalRules).sortedBy { r -> r.order }
}

val activityRuleFlow by lazy { MutableStateFlow(ActivityRule()) }

private var lastTopActivity: TopActivity = topActivityFlow.value

private fun getFixTopActivity(): TopActivity {
    val top = topActivityFlow.value
    if (top.activityId == null) {
        if (lastTopActivity.appId == top.appId) {
            // 当从通知栏上拉返回应用, 从锁屏返回 等时, activityId 的无障碍事件不会触发, 此时复用上一次获得的 activityId 填充
            updateTopActivity(lastTopActivity)
        }
    } else {
        // 仅保留最近的有 activityId 的单个 TopActivity
        lastTopActivity = top
    }
    return topActivityFlow.value
}

fun getAndUpdateCurrentRules(): ActivityRule {
    val topActivity = getFixTopActivity()
    val oldActivityRule = activityRuleFlow.value
    val allRules = ruleSummaryFlow.value
    val idChanged = topActivity.appId != oldActivityRule.topActivity.appId
    val topChanged = idChanged || oldActivityRule.topActivity != topActivity
    val ruleChanged = oldActivityRule.ruleSummary !== allRules
    if (topChanged || ruleChanged) {
        val t = System.currentTimeMillis()
        val newActivityRule = ActivityRule(
            ruleSummary = allRules,
            topActivity = topActivity,
            appRules = (allRules.appIdToRules[topActivity.appId] ?: emptyList()).filter { rule ->
                rule.matchActivity(topActivity.appId, topActivity.activityId)
            },
            globalRules = ruleSummaryFlow.value.globalRules.filter { r ->
                r.matchActivity(topActivity.appId, topActivity.activityId)
            },
        )
        if (idChanged) {
            appChangeTime = t
            allRules.globalRules.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
            allRules.appIdToRules[oldActivityRule.topActivity.appId]?.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
            newActivityRule.appRules.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
        } else {
            newActivityRule.currentRules.forEach { r ->
                if (r.resetMatchTypeWhenActivity) {
                    r.actionDelayTriggerTime = 0
                    r.actionCount.value = 0
                }
                if (!oldActivityRule.currentRules.contains(r)) {
                    // 新增规则
                    r.matchChangedTime = t
                }
            }
        }
        activityRuleFlow.value = newActivityRule
    }
    return activityRuleFlow.value
}

var lastTriggerRule: ResolvedRule? = null

@Volatile
var lastTriggerTime = 0L

@Volatile
var appChangeTime = 0L

var launcherActivity = TopActivity("")
val launcherAppId: String
    get() = launcherActivity.appId

fun updateLauncherAppId() {
    launcherActivity = app.packageManager.getDefaultLauncherActivity()
}

val clickLogMutex by lazy { Mutex() }
suspend fun insertClickLog(rule: ResolvedRule) {
    clickLogMutex.withLock {
        actionCountFlow.value++
        val clickLog = ClickLog(
            appId = topActivityFlow.value.appId,
            activityId = topActivityFlow.value.activityId,
            subsId = rule.subsItem.id,
            subsVersion = rule.rawSubs.version,
            groupKey = rule.g.group.key,
            groupType = when (rule) {
                is AppRule -> SubsConfig.AppGroupType
                is GlobalRule -> SubsConfig.GlobalGroupType
            },
            ruleIndex = rule.index,
            ruleKey = rule.key,
        )
        DbSet.clickLogDao.insert(clickLog)
        if (actionCountFlow.value % 100 == 0L) {
            DbSet.clickLogDao.deleteKeepLatest()
        }
    }
}
