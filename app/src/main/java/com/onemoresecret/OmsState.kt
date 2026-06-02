package com.onemoresecret

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class OmsState {
    var isAutoLockDisarmed by mutableStateOf(false)
}

val LocalOmsState = staticCompositionLocalOf<OmsState> { error("No OmsState provided") }
