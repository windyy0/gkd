package com.nnnn.myg.shizuku

import kotlinx.serialization.Serializable

@Serializable
data class CommandResult(
    val code: Int,
    val result: String,
    val error: String?
)
