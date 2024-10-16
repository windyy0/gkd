package com.nnnn.myg.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppItemPageDestination
import com.ramcosta.composedestinations.generated.destinations.GlobalRulePageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.nnnn.myg.MainActivity
import com.nnnn.myg.data.ClickLog
import com.nnnn.myg.data.ExcludeData
import com.nnnn.myg.data.SubsConfig
import com.nnnn.myg.data.stringify
import com.nnnn.myg.data.switch
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.ui.component.EmptyText
import com.nnnn.myg.ui.component.StartEllipsisText
import com.nnnn.myg.ui.component.waitResult
import com.nnnn.myg.ui.style.EmptyHeight
import com.nnnn.myg.util.LocalNavController
import com.nnnn.myg.util.ProfileTransitions
import com.nnnn.myg.util.appInfoCacheFlow
import com.nnnn.myg.util.launchAsFn
import com.nnnn.myg.util.subsIdToRawFlow
import com.nnnn.myg.util.throttle
import com.nnnn.myg.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun ClickLogPage() {
    val context = LocalContext.current as MainActivity
    val mainVm = context.mainVm
    val navController = LocalNavController.current
    val vm = viewModel<ClickLogVm>()
    val clickLogCount by vm.clickLogCountFlow.collectAsState()
    val clickDataItems = vm.pagingDataFlow.collectAsLazyPagingItems()
    val appInfoCache by appInfoCacheFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    var previewClickLog by remember {
        mutableStateOf<ClickLog?>(null)
    }
    val (previewConfigFlow, setPreviewConfigFlow) = remember {
        mutableStateOf<StateFlow<SubsConfig?>>(MutableStateFlow(null))
    }
    LaunchedEffect(key1 = previewClickLog, block = {
        val log = previewClickLog
        if (log != null) {
            val stateFlow = (if (log.groupType == SubsConfig.AppGroupType) {
                DbSet.subsConfigDao.queryAppGroupTypeConfig(
                    log.subsId, log.appId ?: "", log.groupKey
                )
            } else {
                DbSet.subsConfigDao.queryGlobalGroupTypeConfig(log.subsId, log.groupKey)
            }).map { s -> s.firstOrNull() }.stateIn(vm.viewModelScope, SharingStarted.Eagerly, null)
            setPreviewConfigFlow(stateFlow)
        } else {
            setPreviewConfigFlow(MutableStateFlow(null))
        }
    })

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = throttle {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            title = { Text(text = "触发记录") },
            actions = {
                if (clickLogCount > 0) {
                    IconButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                        mainVm.dialogFlow.waitResult(
                            title = "删除记录",
                            text = "确定删除所有触发记录?",
                            error = true,
                        )
                        DbSet.clickLogDao.deleteAll()
                    })) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                    }
                }
            })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
        ) {
            items(
                count = clickDataItems.itemCount,
                key = clickDataItems.itemKey { c -> c.t0.id }
            ) { i ->
                val (clickLog, group, rule) = clickDataItems[i] ?: return@items
                if (i > 0) {
                    HorizontalDivider()
                }
                Column(
                    modifier = Modifier
                        .clickable {
                            previewClickLog = clickLog
                        }
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Row {
                        Text(text = clickLog.date)
                        Spacer(modifier = Modifier.width(10.dp))
                        val appInfo = appInfoCache[clickLog.appId]
                        val appShowName = appInfo?.name ?: clickLog.appId
                        if (appShowName != null) {
                            Text(
                                text = appShowName,
                                style = LocalTextStyle.current.let {
                                    if (appInfo?.isSystem == true) {
                                        it.copy(textDecoration = TextDecoration.Underline)
                                    } else {
                                        it
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    val showActivityId = clickLog.showActivityId
                    if (showActivityId != null) {
                        StartEllipsisText(text = showActivityId)
                    } else {
                        Text(text = "null", color = LocalContentColor.current.copy(alpha = 0.5f))
                    }
                    group?.name?.let { name ->
                        Text(text = name)
                    }
                    if (rule?.name != null) {
                        Text(text = rule.name ?: "")
                    } else if ((group?.rules?.size ?: 0) > 1) {
                        Text(text = (if (clickLog.ruleKey != null) "key=${clickLog.ruleKey}, " else "") + "index=${clickLog.ruleIndex}")
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (clickLogCount == 0) {
                    EmptyText(text = "暂无记录")
                }
            }
        }
    })

    previewClickLog?.let { clickLog ->
        Dialog(onDismissRequest = { previewClickLog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val previewConfig = previewConfigFlow.collectAsState().value
                val oldExclude = remember(key1 = previewConfig?.exclude) {
                    ExcludeData.parse(previewConfig?.exclude)
                }
                val appInfo = appInfoCache[clickLog.appId]

                Text(
                    text = "查看规则组", modifier = Modifier
                        .clickable(onClick = throttle {
                            clickLog.appId ?: return@throttle
                            if (clickLog.groupType == SubsConfig.AppGroupType) {
                                navController.toDestinationsNavigator().navigate(
                                    AppItemPageDestination(
                                        clickLog.subsId, clickLog.appId, clickLog.groupKey
                                    )
                                )
                            } else if (clickLog.groupType == SubsConfig.GlobalGroupType) {
                                navController.toDestinationsNavigator().navigate(
                                    GlobalRulePageDestination(
                                        clickLog.subsId, clickLog.groupKey
                                    )
                                )
                            }
                            previewClickLog = null
                        })
                        .fillMaxWidth()
                        .padding(16.dp)
                )
                if (clickLog.groupType == SubsConfig.GlobalGroupType && clickLog.appId != null) {
                    val group =
                        subsIdToRaw[clickLog.subsId]?.globalGroups?.find { g -> g.key == clickLog.groupKey }
                    val appChecked = if (group != null) {
                        getChecked(
                            oldExclude,
                            group,
                            clickLog.appId,
                            appInfo
                        )
                    } else {
                        null
                    }
                    if (appChecked != null) {
                        Text(
                            text = if (appChecked) "在此应用禁用" else "移除在此应用的禁用",
                            modifier = Modifier
                                .clickable(
                                    onClick = vm.viewModelScope.launchAsFn(
                                        Dispatchers.IO
                                    ) {
                                        val subsConfig = previewConfig ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsItemId = clickLog.subsId,
                                            groupKey = clickLog.groupKey,
                                        )
                                        val newSubsConfig = subsConfig.copy(
                                            exclude = oldExclude
                                                .copy(
                                                    appIds = oldExclude.appIds
                                                        .toMutableMap()
                                                        .apply {
                                                            set(clickLog.appId, appChecked)
                                                        })
                                                .stringify()
                                        )
                                        DbSet.subsConfigDao.insert(newSubsConfig)
                                        toast("更新禁用")
                                    })
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                    }
                }
                if (clickLog.appId != null && clickLog.activityId != null) {
                    val disabled =
                        oldExclude.activityIds.contains(clickLog.appId to clickLog.activityId)
                    Text(
                        text = if (disabled) "移除在此页面的禁用" else "在此页面禁用",
                        modifier = Modifier
                            .clickable(onClick = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                val subsConfig =
                                    if (clickLog.groupType == SubsConfig.AppGroupType) {
                                        previewConfig ?: SubsConfig(
                                            type = SubsConfig.AppGroupType,
                                            subsItemId = clickLog.subsId,
                                            appId = clickLog.appId,
                                            groupKey = clickLog.groupKey,
                                        )
                                    } else {
                                        previewConfig ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsItemId = clickLog.subsId,
                                            groupKey = clickLog.groupKey,
                                        )
                                    }
                                val newSubsConfig = subsConfig.copy(
                                    exclude = oldExclude
                                        .switch(
                                            clickLog.appId,
                                            clickLog.activityId
                                        )
                                        .stringify()
                                )
                                DbSet.subsConfigDao.insert(newSubsConfig)
                                toast("更新禁用")
                            })
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }

                Text(
                    text = "删除记录",
                    modifier = Modifier
                        .clickable(onClick = vm.viewModelScope.launchAsFn {
                            previewClickLog = null
                            DbSet.clickLogDao.delete(clickLog)
                            toast("删除成功")
                        })
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}