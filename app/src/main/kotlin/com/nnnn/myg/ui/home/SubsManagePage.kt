package com.nnnn.myg.ui.home

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dylanc.activityresult.launcher.launchForResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nnnn.myg.MainActivity
import com.nnnn.myg.data.Value
import com.nnnn.myg.data.deleteSubscription
import com.nnnn.myg.data.exportData
import com.nnnn.myg.data.importData
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.ui.component.SubsItemCard
import com.nnnn.myg.ui.component.TextMenu
import com.nnnn.myg.ui.component.waitResult
import com.nnnn.myg.ui.style.itemVerticalPadding
import com.nnnn.myg.util.LOCAL_SUBS_ID
import com.nnnn.myg.util.SafeR
import com.nnnn.myg.util.UpdateTimeOption
import com.nnnn.myg.util.checkSubsUpdate
import com.nnnn.myg.util.findOption
import com.nnnn.myg.util.launchAsFn
import com.nnnn.myg.util.launchTry
import com.nnnn.myg.util.map
import com.nnnn.myg.util.saveFileToDownloads
import com.nnnn.myg.util.shareFile
import com.nnnn.myg.util.storeFlow
import com.nnnn.myg.util.subsIdToRawFlow
import com.nnnn.myg.util.subsItemsFlow
import com.nnnn.myg.util.subsRefreshingFlow
import com.nnnn.myg.util.throttle
import com.nnnn.myg.util.toast
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

val subsNav = BottomNavItem(
    label = "订阅", icon = Icons.AutoMirrored.Filled.FormatListBulleted
)

@Composable
fun useSubsManagePage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity

    val vm = viewModel<HomeVm>()
    val subItems by subsItemsFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    var orderSubItems by remember {
        mutableStateOf(subItems)
    }
    LaunchedEffect(subItems) {
        orderSubItems = subItems
    }

    val refreshing by subsRefreshingFlow.collectAsState()
    val pullRefreshState = rememberPullRefreshState(refreshing, { checkSubsUpdate(true) })
    var isSelectedMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val draggedFlag = remember { Value(false) }
    LaunchedEffect(key1 = isSelectedMode) {
        if (!isSelectedMode && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }
    if (isSelectedMode) {
        BackHandler {
            isSelectedMode = false
        }
    }
    LaunchedEffect(key1 = subItems.size) {
        if (subItems.size <= 1) {
            isSelectedMode = false
        }
    }

    var showSettingsDlg by remember { mutableStateOf(false) }
    if (showSettingsDlg) {
        AlertDialog(
            onDismissRequest = { showSettingsDlg = false },
            title = { Text("订阅设置") },
            text = {
                val store by storeFlow.collectAsState()
                TextMenu(
                    modifier = Modifier.padding(0.dp, itemVerticalPadding),
                    title = "更新订阅",
                    option = UpdateTimeOption.allSubObject.findOption(store.updateSubsInterval)
                ) {
                    storeFlow.update { s -> s.copy(updateSubsInterval = it.value) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDlg = false }) {
                    Text("关闭")
                }
            }
        )
    }

    ShareDataDialog(vm)
    vm.inputSubsLinkOption.ContentDialog()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    return ScaffoldExt(
        navItem = subsNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                if (isSelectedMode) {
                    IconButton(onClick = { isSelectedMode = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                        )
                    }
                }
            }, title = {
                if (isSelectedMode) {
                    Text(
                        text = if (selectedIds.isNotEmpty()) selectedIds.size.toString() else "",
                    )
                } else {
                    Text(
                        text = subsNav.label,
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                if (isSelectedMode) {
                    val canDeleteIds = if (selectedIds.contains(LOCAL_SUBS_ID)) {
                        selectedIds - LOCAL_SUBS_ID
                    } else {
                        selectedIds
                    }
                    if (canDeleteIds.isNotEmpty()) {
                        val text = "确定删除所选 ${canDeleteIds.size} 个订阅?".let {
                            if (selectedIds.contains(LOCAL_SUBS_ID)) "$it\n\n注: 不包含本地订阅" else it
                        }
                        IconButton(onClick = vm.viewModelScope.launchAsFn {
                            context.mainVm.dialogFlow.waitResult(
                                title = "删除订阅",
                                text = text,
                                error = true,
                            )
                            deleteSubscription(*canDeleteIds.toLongArray())
                            selectedIds = selectedIds - canDeleteIds
                            if (selectedIds.size == canDeleteIds.size) {
                                isSelectedMode = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                            )
                        }
                    }
                    IconButton(onClick = {
                        vm.showShareDataIdsFlow.value = selectedIds
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        expanded = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                } else {
                    IconButton(onClick = throttle {
                        if (storeFlow.value.enableMatch) {
                            toast("暂停规则匹配")
                        } else {
                            toast("开启规则匹配")
                        }
                        storeFlow.update { s -> s.copy(enableMatch = !s.enableMatch) }
                    }) {
                        val scope = rememberCoroutineScope()
                        val enableMatch by remember {
                            storeFlow.map(scope) { it.enableMatch }
                        }.collectAsState()
                        val id = if (enableMatch) SafeR.ic_flash_on else SafeR.ic_flash_off
                        Icon(
                            painter = painterResource(id = id),
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        showSettingsDlg = true
                    }) {
                        Icon(
                            painter = painterResource(id = SafeR.ic_page_info),
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        if (subsRefreshingFlow.value) {
                            toast("正在刷新订阅,请稍后操作")
                        } else {
                            expanded = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                }
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (isSelectedMode) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = "全选")
                                },
                                onClick = {
                                    expanded = false
                                    selectedIds = subItems.map { it.id }.toSet()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = "反选")
                                },
                                onClick = {
                                    expanded = false
                                    val newSelectedIds =
                                        subItems.map { it.id }.toSet() - selectedIds
                                    if (newSelectedIds.isEmpty()) {
                                        isSelectedMode = false
                                    }
                                    selectedIds = newSelectedIds
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                    )
                                },
                                text = {
                                    Text(text = "导入数据")
                                },
                                onClick = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                    expanded = false
                                    val result =
                                        context.launcher.launchForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "application/zip"
                                        })
                                    val uri = result.data?.data
                                    if (uri == null) {
                                        toast("未选择文件")
                                        return@launchAsFn
                                    }
                                    importData(uri)
                                },
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            if (!isSelectedMode) {
                FloatingActionButton(onClick = {
                    if (subsRefreshingFlow.value) {
                        toast("正在刷新订阅,请稍后操作")
                        return@FloatingActionButton
                    }
                    vm.viewModelScope.launchTry {
                        val url = vm.inputSubsLinkOption.getResult() ?: return@launchTry
                        vm.addOrModifySubs(url)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "info",
                    )
                }
            }
        },
    ) { padding ->
        val lazyListState = rememberLazyListState()
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                orderSubItems = orderSubItems.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                    forEachIndexed { index, subsItem ->
                        if (subsItem.order != index) {
                            this[index] = subsItem.copy(order = index)
                        }
                    }
                }
                draggedFlag.value = true
            }
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pullRefresh(pullRefreshState, subItems.isNotEmpty())
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(orderSubItems, { _, subItem -> subItem.id }) { index, subItem ->
                    val canDrag = !refreshing && orderSubItems.size > 1
                    ReorderableItem(
                        reorderableLazyColumnState,
                        key = subItem.id,
                        enabled = canDrag,
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        SubsItemCard(
                            modifier = Modifier.longPressDraggableHandle(
                                enabled = canDrag,
                                interactionSource = interactionSource,
                                onDragStarted = {
                                    if (orderSubItems.size > 1 && !isSelectedMode) {
                                        isSelectedMode = true
                                        selectedIds = setOf(subItem.id)
                                    }
                                },
                                onDragStopped = {
                                    if (draggedFlag.value) {
                                        draggedFlag.value = false
                                        isSelectedMode = false
                                        selectedIds = emptySet()
                                    }
                                    val changeItems = orderSubItems.filter { newItem ->
                                        subItems.find { oldItem -> oldItem.id == newItem.id }?.order != newItem.order
                                    }
                                    if (changeItems.isNotEmpty()) {
                                        vm.viewModelScope.launchTry {
                                            DbSet.subsItemDao.batchUpdateOrder(changeItems)
                                        }
                                    }
                                },
                            ),
                            interactionSource = interactionSource,
                            subsItem = subItem,
                            subscription = subsIdToRaw[subItem.id],
                            index = index + 1,
                            vm = vm,
                            isSelectedMode = isSelectedMode,
                            isSelected = selectedIds.contains(subItem.id),
                            onCheckedChange = { checked ->
                                vm.viewModelScope.launch {
                                    DbSet.subsItemDao.updateEnable(subItem.id, checked)
                                }
                            },
                            onSelectedChange = {
                                val newSelectedIds = if (selectedIds.contains(subItem.id)) {
                                    selectedIds.toMutableSet().apply {
                                        remove(subItem.id)
                                    }
                                } else {
                                    selectedIds + subItem.id
                                }
                                selectedIds = newSelectedIds
                                if (newSelectedIds.isEmpty()) {
                                    isSelectedMode = false
                                }
                            },
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun ShareDataDialog(vm: HomeVm) {
    val context = LocalContext.current as MainActivity
    val showShareDataIds = vm.showShareDataIdsFlow.collectAsState().value
    if (showShareDataIds != null) {
        Dialog(onDismissRequest = { vm.showShareDataIdsFlow.value = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Text(
                    text = "分享到其他应用", modifier = Modifier
                        .clickable(onClick = throttle {
                            vm.showShareDataIdsFlow.value = null
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val file = exportData(showShareDataIds)
                                context.shareFile(file, "分享数据文件")
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "保存到下载",
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            vm.showShareDataIdsFlow.value = null
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val file = exportData(showShareDataIds)
                                context.saveFileToDownloads(file)
                            }
                        })
                        .then(modifier)
                )
            }
        }
    }
}