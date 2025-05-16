package com.onemoresecret.bt.layout

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.onemoresecret.bt.KeyModifier
import com.onemoresecret.bt.KeyboardReport
import com.onemoresecret.bt.KeyboardUsage
import java.util.Arrays
import java.util.function.Consumer

@JsonSerialize(using = StrokeSerializer::class)
class Stroke {
    private val reports: MutableList<KeyboardReport> = ArrayList()
    private val modifiers: MutableSet<KeyModifier> = HashSet()

    init {
        sendModifiers()
    }

    /**
     * Get resulting sequence of KeyboardReports
     *
     * @return key stroke sequence
     */
    fun get(): List<KeyboardReport> {
        val list: MutableList<KeyboardReport> = ArrayList(reports)
        if (modifiers.isNotEmpty()) list.add(KeyboardReport(KeyboardUsage.KBD_NONE)) //release all keys, reset all modifiers

        return list
    }

    /**
     * Clear all modifiers
     */
    fun clear(): Stroke {
        modifiers.clear()
        sendModifiers()
        return this
    }

    /**
     * Add modifiers one by one emulating user input
     */
    fun press(vararg modifiers: KeyModifier): Stroke {
        this.modifiers.addAll(modifiers.asList())
        sendModifiers()
        return this
    }

    /**
     * Remove modifiers one by one emulating user input
     */
    fun release(vararg modifiers: KeyModifier): Stroke {
        this.modifiers.removeAll(modifiers.toSet())
        sendModifiers()
        return this
    }

    /**
     * This is a workaround for some cases like Hyper-V, that sometimes "overlooks" modifiers.
     */
    private fun sendModifiers() {
        val mArr: Array<KeyModifier> = modifiers.toTypedArray()
        reports.add(KeyboardReport(KeyboardUsage.KBD_NONE, mArr.toSet()))
    }

    fun type(vararg usages: KeyboardUsage): Stroke {
        for (u in usages) {
            val mArr: Array<KeyModifier> = modifiers.toTypedArray()
            reports.add(KeyboardReport(u, mArr.toSet())) //press
            reports.add(KeyboardReport(KeyboardUsage.KBD_NONE, mArr.toSet())) //release
        }
        return this
    }

    /**
     * Convert this [Stroke] to upper case. This method handles only simple cases like a -> A. For more
     * complex keystrokes, a separate [Stroke] definition should be created.
     */
    fun toUpper(): Stroke? {
        if (reports.stream().filter { r: KeyboardReport -> r.usage != KeyboardUsage.KBD_NONE }
                .count() != 1L) {
            return null //create a dedicated Stroke instead!
        }

        val upperCaseStroke = Stroke()

        var shift = KeyModifier.NONE
        for (i in reports.indices) {
            val kr = reports[i]
            upperCaseStroke.modifiers.addAll(kr.modifiers)
            upperCaseStroke.modifiers.add(shift)

            if (kr.usage != KeyboardUsage.KBD_NONE) {
                //we are typing smth., press SHIFT here
                upperCaseStroke.press(KeyModifier.LEFT_SHIFT)
                upperCaseStroke.type(kr.usage)
                shift = KeyModifier.LEFT_SHIFT
            }
        }

        return upperCaseStroke
    }
}
