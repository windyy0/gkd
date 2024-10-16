package com.nnnn.myg.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.nnnn.myg.data.SubsConfig
import com.nnnn.myg.data.Tuple3
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.util.subsIdToRawFlow

class ClickLogVm : ViewModel() {

    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) { DbSet.clickLogDao.pagingSource() }
        .flow.cachedIn(viewModelScope)
        .combine(subsIdToRawFlow) { pagingData, subsIdToRaw ->
            pagingData.map { c ->
                val group = if (c.groupType == SubsConfig.AppGroupType) {
                    val app = subsIdToRaw[c.subsId]?.apps?.find { a -> a.id == c.appId }
                    app?.groups?.find { g -> g.key == c.groupKey }
                } else {
                    subsIdToRaw[c.subsId]?.globalGroups?.find { g -> g.key == c.groupKey }
                }
                val rule = group?.rules?.run {
                    if (c.ruleKey != null) {
                        find { r -> r.key == c.ruleKey }
                    } else {
                        getOrNull(c.ruleIndex)
                    }
                }
                Tuple3(c, group, rule)
            }
        }

    val clickLogCountFlow =
        DbSet.clickLogDao.count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

}