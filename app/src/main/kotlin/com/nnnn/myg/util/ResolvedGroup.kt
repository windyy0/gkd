package com.nnnn.myg.util

import com.nnnn.myg.data.RawSubscription
import com.nnnn.myg.data.SubsConfig
import com.nnnn.myg.data.SubsItem

sealed class ResolvedGroup(
    open val group: RawSubscription.RawGroupProps,
    val subscription: RawSubscription,
    val subsItem: SubsItem,
    val config: SubsConfig?,
)

class ResolvedAppGroup(
    override val group: RawSubscription.RawAppGroup,
    subscription: RawSubscription,
    subsItem: SubsItem,
    config: SubsConfig?,
    val app: RawSubscription.RawApp,
    val enable: Boolean,
) : ResolvedGroup(group, subscription, subsItem, config)

class ResolvedGlobalGroup(
    override val group: RawSubscription.RawGlobalGroup,
    subscription: RawSubscription,
    subsItem: SubsItem,
    config: SubsConfig?,
) : ResolvedGroup(group, subscription, subsItem, config)