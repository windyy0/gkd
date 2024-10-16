package com.nnnn.myg.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.ui.component.UploadOptions
import com.nnnn.myg.util.IMPORT_SHORT_URL

class SnapshotVm : ViewModel() {
    val snapshotsState = DbSet.snapshotDao.query()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uploadOptions = UploadOptions(
        scope = viewModelScope,
        showHref = { IMPORT_SHORT_URL + it.id }
    )
}