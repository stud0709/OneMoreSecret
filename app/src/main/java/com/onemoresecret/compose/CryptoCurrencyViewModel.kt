package com.onemoresecret.compose

import androidx.compose.runtime.mutableStateOf

class CryptoCurrencyViewModel {
    val publicAddress = mutableStateOf("")

    fun setValue(publicAddress: String) {
        this.publicAddress.value = publicAddress
    }
}