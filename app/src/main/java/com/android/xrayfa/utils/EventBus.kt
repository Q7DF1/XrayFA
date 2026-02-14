package com.android.xrayfa.utils

import com.android.xrayfa.core.XrayBaseService
import kotlinx.coroutines.flow.MutableStateFlow

object EventBus {

    val statusFlow = MutableStateFlow<Boolean>(XrayBaseService.isRunning)
}