package com.onemoresecret.bt

enum class KeyModifier(value: Int) {
    NONE(0),
    LEFT_CONTROL(1),
    LEFT_SHIFT(1 shl 1),
    LEFT_ALT(1 shl 2),
    LEFT_GUI(1 shl 3),
    RIGHT_CONTROL(1 shl 4),
    RIGHT_SHIFT(1 shl 5),
    RIGHT_ALT(1 shl 6),
    RIGHT_GUI(1 shl 7);

    @JvmField
    val value: Byte

    init {
        this.value = value.toByte()
    }
}