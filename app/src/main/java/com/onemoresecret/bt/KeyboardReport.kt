package com.onemoresecret.bt

import com.fasterxml.jackson.databind.annotation.JsonSerialize

/**
 * Report data.
 *
 * @param modifiers Use [KeyModifier.value] combined with boolean AND, e.g. `LEFT_CONTROL.value & LEFT_SHIFT.value `
 * @param usage       Physical key according to USB HID Usage Tables.
 */
@JsonSerialize(using = KeyboardReportSerializer::class)
class KeyboardReport(val usage: KeyboardUsage, val modifiers: Set<KeyModifier>) {
    constructor(usage: KeyboardUsage) : this(usage, HashSet())

    val report: ByteArray
        /**
         * Keyboard report. byte[0] = modifiers, byte[1] = keyboard usage (i.e. key which is currently pressed)
         * @return Keyboard report
         */
        get() {
            var mByte: Byte = 0
            for (m in modifiers) {
                mByte = (mByte.toInt() or m.value.toInt()).toByte()
            }
            return byteArrayOf(mByte, usage.value)
        }

    companion object {
        const val NUM_LOCK: Int = 1
        const val CAPS_LOCK: Int = 1 shl 1
        const val SCROLL_LOCK: Int = 1 shl 2
        const val COMPOSE: Int = 1 shl 3
        const val KANA: Int = 1 shl 4
    }
}
