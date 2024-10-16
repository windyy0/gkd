package com.nnnn.myg.util

import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.nnnn.myg.app

val Dp.px: Float
    get() {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value, app.resources.displayMetrics
        )
    }

val TextUnit.px: Float
    get() {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value, app.resources.displayMetrics
        )
    }

