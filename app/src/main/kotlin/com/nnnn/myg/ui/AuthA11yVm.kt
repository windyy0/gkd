package com.nnnn.myg.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.nnnn.myg.permission.writeSecureSettingsState

class AuthA11yVm : ViewModel() {
    init {
        viewModelScope.launch {
            while (isActive) {
                if (writeSecureSettingsState.updateAndGet()) {
                    break
                }
                delay(1000)
            }
        }
    }

    val showCopyDlgFlow = MutableStateFlow(false)
}
