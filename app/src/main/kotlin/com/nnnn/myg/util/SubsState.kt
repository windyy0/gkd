package com.nnnn.myg.util

import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.NetworkUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import com.nnnn.myg.appScope
import com.nnnn.myg.data.AppRule
import com.nnnn.myg.data.CategoryConfig
import com.nnnn.myg.data.GlobalRule
import com.nnnn.myg.data.RawSubscription
import com.nnnn.myg.data.SubsConfig
import com.nnnn.myg.data.SubsItem
import com.nnnn.myg.data.SubsVersion
import com.nnnn.myg.db.DbSet
import li.songe.json5.decodeFromJson5String
import java.net.URI

val subsItemsFlow by lazy {
    DbSet.subsItemDao.query().stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

data class SubsEntry(
    val subsItem: SubsItem,
    val subscription: RawSubscription?,
) {
    val checkUpdateUrl = run {
        val checkUpdateUrl = subscription?.checkUpdateUrl ?: return@run null
        val updateUrl = subscription.updateUrl ?: subsItem.updateUrl ?: return@run checkUpdateUrl
        try {
            return@run URI(updateUrl).resolve(checkUpdateUrl).toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@run null
    }
}

val subsLoadErrorsFlow = MutableStateFlow<Map<Long, Exception>>(emptyMap())
val subsRefreshErrorsFlow = MutableStateFlow<Map<Long, Exception>>(emptyMap())
val subsIdToRawFlow = MutableStateFlow<Map<Long, RawSubscription>>(emptyMap())

val subsEntriesFlow by lazy {
    combine(
        subsItemsFlow,
        subsIdToRawFlow,
    ) { subsItems, subsIdToRaw ->
        subsItems.map { s ->
            SubsEntry(
                subsItem = s,
                subscription = subsIdToRaw[s.id],
            )
        }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())
}


private val updateSubsFileMutex by lazy { Mutex() }
fun updateSubscription(subscription: RawSubscription) {
    appScope.launchTry {
        updateSubsFileMutex.withLock {
            val newMap = subsIdToRawFlow.value.toMutableMap()
            if (subscription.id < 0 && newMap[subscription.id]?.version == subscription.version) {
                newMap[subscription.id] = subscription.copy(version = subscription.version + 1)
            } else {
                newMap[subscription.id] = subscription
            }
            subsIdToRawFlow.value = newMap
            if (subsLoadErrorsFlow.value.contains(subscription.id)) {
                subsLoadErrorsFlow.update {
                    it.toMutableMap().apply {
                        remove(subscription.id)
                    }
                }
            }
            withContext(Dispatchers.IO) {
                DbSet.subsItemDao.updateMtime(subscription.id, System.currentTimeMillis())
                subsFolder.resolve("${subscription.id}.json")
                    .writeText(json.encodeToString(subscription))
            }
            LogUtils.d("更新订阅文件:id=${subscription.id},name=${subscription.name}")
        }
    }
}

fun getGroupRawEnable(
    group: RawSubscription.RawGroupProps,
    subsConfig: SubsConfig?,
    category: RawSubscription.RawCategory?,
    categoryConfig: CategoryConfig?,
): Boolean {
    // 优先级: 规则用户配置 > 批量配置 > 批量默认 > 规则默认
    // 1.规则用户配置
    return subsConfig?.enable ?: if (category != null) {// 这个规则被批量配置捕获
        val enable = if (categoryConfig != null) {
            // 2.批量配置
            categoryConfig.enable
        } else {
            // 3.批量默认
            category.enable
        }
        enable
    } else {
        null
    } ?: group.enable ?: true
}

data class RuleSummary(
    val globalRules: List<GlobalRule> = emptyList(),
    val globalGroups: List<ResolvedGlobalGroup> = emptyList(),
    val appIdToRules: Map<String, List<AppRule>> = emptyMap(),
    val appIdToGroups: Map<String, List<RawSubscription.RawAppGroup>> = emptyMap(),
    val appIdToAllGroups: Map<String, List<ResolvedAppGroup>> = emptyMap(),
) {
    val appSize = appIdToRules.keys.size
    val appGroupSize = appIdToGroups.values.sumOf { s -> s.size }

    val numText = if (globalGroups.size + appGroupSize > 0) {
        if (globalGroups.isNotEmpty()) {
            "${globalGroups.size}全局" + if (appGroupSize > 0) {
                "/"
            } else {
                ""
            }
        } else {
            ""
        } + if (appGroupSize > 0) {
            "${appSize}应用/${appGroupSize}规则组"
        } else {
            ""
        }
    } else {
        "暂无规则"
    }

    val slowGlobalGroups =
        globalRules.filter { r -> r.isSlow }.distinctBy { r -> r.group }
            .map { r -> r.group to r }
    val slowAppGroups =
        appIdToRules.values.flatten().filter { r -> r.isSlow }.distinctBy { r -> r.group }
            .map { r -> r.group to r }
    val slowGroupCount = slowGlobalGroups.size + slowAppGroups.size
}

private val usedSubsEntriesFlow by lazy {
    subsEntriesFlow.map { it.filter { s -> s.subsItem.enable && s.subscription != null } }
}

val ruleSummaryFlow by lazy {
    combine(
        usedSubsEntriesFlow,
        appInfoCacheFlow,
        DbSet.subsConfigDao.queryUsedList(),
        DbSet.categoryConfigDao.queryUsedList(),
    ) { subsEntries, appInfoCache, subsConfigs, categoryConfigs ->
        val globalSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.GlobalGroupType }
        val appSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.AppType }
        val groupSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.AppGroupType }
        val appRules = HashMap<String, MutableList<AppRule>>()
        val appGroups = HashMap<String, List<RawSubscription.RawAppGroup>>()
        val appAllGroups =
            HashMap<String, List<ResolvedAppGroup>>()
        val globalRules = mutableListOf<GlobalRule>()
        val globalGroups = mutableListOf<ResolvedGlobalGroup>()
        subsEntries.forEach { (subsItem, rawSubs) ->
            rawSubs ?: return@forEach

            // global scope
            val subGlobalSubsConfigs = globalSubsConfigs.filter { c -> c.subsItemId == subsItem.id }
            val subGlobalGroupToRules =
                mutableMapOf<RawSubscription.RawGlobalGroup, List<GlobalRule>>()
            rawSubs.globalGroups.filter { g ->
                (subGlobalSubsConfigs.find { c -> c.groupKey == g.key }?.enable
                        ?: g.enable ?: true) && g.valid
            }.forEach { groupRaw ->
                val config = subGlobalSubsConfigs.find { c -> c.groupKey == groupRaw.key }
                val g = ResolvedGlobalGroup(
                    group = groupRaw,
                    subscription = rawSubs,
                    subsItem = subsItem,
                    config = config
                )
                globalGroups.add(g)
                val subRules = groupRaw.rules.map { ruleRaw ->
                    GlobalRule(
                        rule = ruleRaw,
                        g = g,
                        appInfoCache = appInfoCache,
                    )
                }
                subGlobalGroupToRules[groupRaw] = subRules
                globalRules.addAll(subRules)
            }
            subGlobalGroupToRules.values.forEach {
                it.forEach { r ->
                    r.groupToRules = subGlobalGroupToRules
                }
            }
            subGlobalGroupToRules.clear()

            // app scope
            val subAppSubsConfigs = appSubsConfigs.filter { c -> c.subsItemId == subsItem.id }
            val subGroupSubsConfigs = groupSubsConfigs.filter { c -> c.subsItemId == subsItem.id }
            val subCategoryConfigs = categoryConfigs.filter { c -> c.subsItemId == subsItem.id }
            rawSubs.apps.filter { appRaw ->
                // 筛选 当前启用的 app 订阅规则
                appRaw.groups.isNotEmpty() && (subAppSubsConfigs.find { c -> c.appId == appRaw.id }?.enable
                    ?: (appInfoCache[appRaw.id] != null))
            }.forEach { appRaw ->
                val subAppGroups = mutableListOf<RawSubscription.RawAppGroup>()
                val appGroupConfigs = subGroupSubsConfigs.filter { c -> c.appId == appRaw.id }
                val subAppGroupToRules = mutableMapOf<RawSubscription.RawAppGroup, List<AppRule>>()
                val groupAndEnables = appRaw.groups.map { group ->
                    val enable = getGroupRawEnable(
                        group,
                        appGroupConfigs.find { c -> c.groupKey == group.key },
                        rawSubs.groupToCategoryMap[group],
                        subCategoryConfigs.find { c -> c.categoryKey == rawSubs.groupToCategoryMap[group]?.key }
                    ) && group.valid
                    ResolvedAppGroup(
                        group = group,
                        subscription = rawSubs,
                        subsItem = subsItem,
                        config = appGroupConfigs.find { c -> c.groupKey == group.key },
                        app = appRaw,
                        enable = enable
                    )
                }
                appAllGroups[appRaw.id] = (appAllGroups[appRaw.id] ?: emptyList()) + groupAndEnables
                groupAndEnables.forEach { g ->
                    if (g.enable) {
                        subAppGroups.add(g.group)
                        val subRules = g.group.rules.map { ruleRaw ->
                            AppRule(
                                rule = ruleRaw,
                                g = g,
                                appInfo = appInfoCache[appRaw.id]
                            )
                        }.filter { r -> r.enable }
                        subAppGroupToRules[g.group] = subRules
                        if (subRules.isNotEmpty()) {
                            val rules = appRules[appRaw.id] ?: mutableListOf()
                            appRules[appRaw.id] = rules
                            rules.addAll(subRules)
                        }
                    }
                }
                if (subAppGroups.isNotEmpty()) {
                    appGroups[appRaw.id] = subAppGroups
                }
                subAppGroupToRules.values.forEach {
                    it.forEach { r ->
                        r.groupToRules = subAppGroupToRules
                    }
                }
            }
        }
        RuleSummary(
            globalRules = globalRules,
            globalGroups = globalGroups,
            appIdToRules = appRules,
            appIdToGroups = appGroups,
            appIdToAllGroups = appAllGroups
        )
    }.flowOn(Dispatchers.Default).stateIn(appScope, SharingStarted.Eagerly, RuleSummary())
}

fun getSubsStatus(ruleSummary: RuleSummary, count: Long): String {
    return if (count > 0) {
        "${ruleSummary.numText}/${count}触发"
    } else {
        ruleSummary.numText
    }
}

private fun loadSubs(id: Long): RawSubscription {
    val file = subsFolder.resolve("${id}.json")
    if (!file.exists()) {
        error("订阅文件不存在")
    }
    val subscription = try {
        RawSubscription.parse(file.readText(), json5 = false)
    } catch (e: Exception) {
        throw Exception("订阅文件解析失败", e)
    }
    if (subscription.id != id) {
        error("订阅文件id不一致")
    }
    return subscription
}

private fun refreshRawSubsList(items: List<SubsItem>) {
    val subscriptions = subsIdToRawFlow.value.toMutableMap()
    val errors = subsLoadErrorsFlow.value.toMutableMap()
    items.forEach { s ->
        try {
            subscriptions[s.id] = loadSubs(s.id)
            errors.remove(s.id)
        } catch (e: Exception) {
            errors[s.id] = e
        }
    }
    subsIdToRawFlow.value = subscriptions
    subsLoadErrorsFlow.value = errors
}

fun initSubsState() {
    subsItemsFlow.value
    appScope.launchTry(Dispatchers.IO) {
        subsRefreshingFlow.value = true
        updateSubsFileMutex.withLock {
            val items = DbSet.subsItemDao.queryAll()
            refreshRawSubsList(items)
        }
        subsRefreshingFlow.value = false
    }
}


private val updateSubsMutex by lazy { Mutex() }
val subsRefreshingFlow = MutableStateFlow(false)

private suspend fun updateSubs(subsEntry: SubsEntry): RawSubscription? {
    val subsItem = subsEntry.subsItem
    val subsRaw = subsEntry.subscription
    if (subsItem.updateUrl == null || subsItem.id < 0) return null
    val checkUpdateUrl = subsEntry.checkUpdateUrl
    if (checkUpdateUrl != null && subsRaw != null) {
        try {
            val subsVersion = json.decodeFromJson5String<SubsVersion>(
                client.get(checkUpdateUrl).bodyAsText()
            )
            LogUtils.d(
                "快速检测更新:id=${subsRaw.id},version=${subsRaw.version}",
                subsVersion
            )
            if (subsVersion.id == subsRaw.id && subsVersion.version <= subsRaw.version) {
                return null
            }
        } catch (e: Exception) {
            LogUtils.d("快速检测更新失败", subsItem, e)
        }
    }
    val updateUrl = subsRaw?.updateUrl ?: subsItem.updateUrl
    val text = try {
        client.get(updateUrl).bodyAsText()
    } catch (e: Exception) {
        throw Exception("请求更新链接失败", e)
    }
    val newSubsRaw = try {
        RawSubscription.parse(text)
    } catch (e: Exception) {
        throw Exception("解析文本失败", e)
    }
    if (newSubsRaw.id != subsItem.id) {
        error("新id=${newSubsRaw.id}不匹配旧id=${subsItem.id}")
    }
    if (subsRaw != null && newSubsRaw.version <= subsRaw.version) {
        LogUtils.d(
            "版本号不满足条件:id=${subsItem.id}",
            "${subsRaw.version} -> ${newSubsRaw.version}"
        )
        return null
    }
    return newSubsRaw
}

fun checkSubsUpdate(showToast: Boolean = false) = appScope.launchTry(Dispatchers.IO) {
    if (updateSubsMutex.isLocked || subsRefreshingFlow.value) {
        return@launchTry
    }
    subsRefreshingFlow.value = true
    updateSubsMutex.withLock {
        if (!withContext(Dispatchers.IO) { NetworkUtils.isAvailable() }) {
            if (showToast) {
                toast("网络不可用")
            }
            return@withLock
        }
        LogUtils.d("开始检测更新")
        val localSubsEntries =
            subsEntriesFlow.value.filter { e -> e.subsItem.id < 0 && e.subscription == null }
        val subsEntries = subsEntriesFlow.value.filter { e -> e.subsItem.id >= 0 }
        refreshRawSubsList(localSubsEntries.map { e -> e.subsItem })

        var successNum = 0
        subsEntries.forEach { subsEntry ->
            try {
                val newSubsRaw = updateSubs(subsEntry)
                if (newSubsRaw != null) {
                    updateSubscription(newSubsRaw)
                    successNum++
                }
                if (subsRefreshErrorsFlow.value.contains(subsEntry.subsItem.id)) {
                    subsRefreshErrorsFlow.update {
                        it.toMutableMap().apply {
                            remove(subsEntry.subsItem.id)
                        }
                    }
                }
            } catch (e: Exception) {
                subsRefreshErrorsFlow.update {
                    it.toMutableMap().apply {
                        set(subsEntry.subsItem.id, e)
                    }
                }
                LogUtils.d("检测更新失败", e)
            }
        }
        if (showToast) {
            if (successNum > 0) {
                toast("更新 $successNum 条订阅")
            } else {
                toast("暂无更新")
            }
        }
        LogUtils.d("结束检测更新")
    }
    delay(500)
    subsRefreshingFlow.value = false
}
