package com.nnnn.myg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nnnn.myg.data.RawSubscription
import com.nnnn.myg.data.SubsItem
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.permission.AuthReason
import com.nnnn.myg.ui.component.AlertDialogOptions
import com.nnnn.myg.util.LOCAL_SUBS_ID
import com.nnnn.myg.util.UpdateStatus
import com.nnnn.myg.util.checkUpdate
import com.nnnn.myg.util.clearCache
import com.nnnn.myg.util.launchTry
import com.nnnn.myg.util.map
import com.nnnn.myg.util.storeFlow
import com.nnnn.myg.util.updateSubscription

class MainViewModel : ViewModel() {
    val enableDarkThemeFlow = storeFlow.debounce(300).map { s -> s.enableDarkTheme }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        storeFlow.value.enableDarkTheme
    )
    val enableDynamicColorFlow = storeFlow.debounce(300).map { s -> s.enableDynamicColor }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        storeFlow.value.enableDynamicColor
    )

    val dialogFlow = MutableStateFlow<AlertDialogOptions?>(null)
    val authReasonFlow = MutableStateFlow<AuthReason?>(null)

    val updateStatus = UpdateStatus()

    val shizukuErrorFlow = MutableStateFlow(false)

    init {
        viewModelScope.launchTry(Dispatchers.IO) {
            val subsItems = DbSet.subsItemDao.queryAll()
            if (!subsItems.any { s -> s.id == LOCAL_SUBS_ID }) {
                updateSubscription(
                    RawSubscription(
                        id = LOCAL_SUBS_ID,
                        name = "本地订阅",
                        version = 0
                    )
                )
                DbSet.subsItemDao.insert(
                    SubsItem(
                        id = LOCAL_SUBS_ID,
                        order = subsItems.minByOrNull { it.order }?.order ?: 0,
                    )
                )
            }
        }

        viewModelScope.launchTry(Dispatchers.IO) {
            // 每次进入删除缓存
            clearCache()
        }

        if (META.updateEnabled && storeFlow.value.autoCheckAppUpdate) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    updateStatus.checkUpdate()
                } catch (e: Exception) {
                    e.printStackTrace()
                    LogUtils.d(e)
                }
            }
        }

        viewModelScope.launch {
            storeFlow.map(viewModelScope) { s -> s.log2FileSwitch }.collect {
                LogUtils.getConfig().isLog2FileSwitch = it
            }
        }
    }
}