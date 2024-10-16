package com.nnnn.myg.util

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController


val LocalNavController =
    compositionLocalOf<NavHostController> { error("not found DestinationsNavigator") }
