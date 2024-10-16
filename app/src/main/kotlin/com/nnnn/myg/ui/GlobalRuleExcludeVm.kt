package com.nnnn.myg.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.GlobalRuleExcludePageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.nnnn.myg.data.ExcludeData
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.util.SortTypeOption
import com.nnnn.myg.util.findOption
import com.nnnn.myg.util.map
import com.nnnn.myg.util.orderedAppInfosFlow
import com.nnnn.myg.util.storeFlow
import com.nnnn.myg.util.subsIdToRawFlow

class GlobalRuleExcludeVm(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = GlobalRuleExcludePageDestination.argsFrom(stateHandle)

    val rawSubsFlow = subsIdToRawFlow.map(viewModelScope) { it[args.subsItemId] }

    val groupFlow =
        rawSubsFlow.map(viewModelScope) { r -> r?.globalGroups?.find { g -> g.key == args.groupKey } }

    val subsConfigFlow =
        DbSet.subsConfigDao.queryGlobalGroupTypeConfig(args.subsItemId, args.groupKey)
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val excludeDataFlow = subsConfigFlow.map(viewModelScope) { s -> ExcludeData.parse(s?.exclude) }

    val searchStrFlow = MutableStateFlow("")
    private val debounceSearchStrFlow = searchStrFlow.debounce(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchStrFlow.value)

    private val appIdToOrderFlow =
        DbSet.clickLogDao.queryLatestUniqueAppIds(args.subsItemId, args.groupKey).map { appIds ->
            appIds.mapIndexed { index, appId -> appId to index }.toMap()
        }
    val sortTypeFlow = storeFlow.map(viewModelScope) {
        SortTypeOption.allSubObject.findOption(it.subsExcludeSortType)
    }
    val showSystemAppFlow = storeFlow.map(viewModelScope) { it.subsExcludeShowSystemApp }
    val showHiddenAppFlow = storeFlow.map(viewModelScope) { it.subsExcludeShowHiddenApp }
    val showAppInfosFlow =
        combine(orderedAppInfosFlow.combine(showHiddenAppFlow) { appInfos, showHiddenApp ->
            if (showHiddenApp) {
                appInfos
            } else {
                appInfos.filter { a -> !a.hidden }
            }
        }.combine(showSystemAppFlow) { apps, showSystemApp ->
            if (showSystemApp) {
                apps
            } else {
                apps.filter { a -> !a.isSystem }
            }
        }, sortTypeFlow, appIdToOrderFlow) { apps, sortType, appIdToOrder ->
            when (sortType) {
                SortTypeOption.SortByAppMtime -> {
                    apps.sortedBy { a -> -a.mtime }
                }

                SortTypeOption.SortByTriggerTime -> {
                    apps.sortedBy { a -> appIdToOrder[a.id] ?: Int.MAX_VALUE }
                }

                SortTypeOption.SortByName -> {
                    apps
                }
            }
        }.combine(debounceSearchStrFlow) { apps, str ->
            if (str.isBlank()) {
                apps
            } else {
                (apps.filter { a -> a.name.contains(str, true) } + apps.filter { a ->
                    a.id.contains(
                        str,
                        true
                    )
                }).distinct()
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

}