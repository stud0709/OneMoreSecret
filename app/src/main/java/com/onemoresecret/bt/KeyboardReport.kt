package com.onemoresecret.bt

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.util.Arrays

@JsonSerialize(using = KeyboardReportSerializer::class)
class KeyboardReport {
    val modifiers: MutableSet<KeyModifier> = HashSet()
    val usage: KeyboardUsage

    /**
     * Report data.
     *
     * @param modifiers Use [KeyModifier.value] combined with boolean AND, e.g. `LEFT_CONTROL.value & LEFT_SHIFT.value `
     * @param usage       Physical key according to USB HID Usage Tables.
     */
    constructor(usage: KeyboardUsage, vararg modifiers: KeyModifier) {
        this.modifiers.addAll(Arrays.asList(*modifiers))
        this.usage = usage
    }

    constructor(usage: KeyboardUsage) {
        this.usage = usage
    }

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
        val CAPS_LOCK: Int = 1 shl 1
        val SCROLL_LOCK: Int = 1 shl 2
        val COMPOSE: Int = 1 shl 3
        val KANA: Int = 1 shl 4
    }
}
