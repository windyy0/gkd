package com.nnnn.myg.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.ui.component.UploadOptions

class AdvancedVm : ViewModel() {
    val snapshotCountFlow =
        DbSet.snapshotDao.count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val uploadOptions = UploadOptions(viewModelScope)
}