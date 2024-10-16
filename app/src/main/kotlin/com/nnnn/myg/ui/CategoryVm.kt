package com.nnnn.myg.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.CategoryPageDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.util.map
import com.nnnn.myg.util.subsIdToRawFlow
import com.nnnn.myg.util.subsItemsFlow

class CategoryVm (stateHandle: SavedStateHandle) : ViewModel() {
    private val args = CategoryPageDestination.argsFrom(stateHandle)

    val subsItemFlow =
        subsItemsFlow.map(viewModelScope) { subsItems -> subsItems.find { s -> s.id == args.subsItemId } }

    val subsRawFlow = subsIdToRawFlow.map(viewModelScope) { m -> m[args.subsItemId] }

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}