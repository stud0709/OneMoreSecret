package com.onemoresecret.bt.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class SwissLayoutTest {
    @Test fun caretIsCommittedBeforeTheFollowingUppercaseLetter() {
        val reports = SwissLayout().forString("^E").flatMap { requireNotNull(it).get() }
            .map { requireNotNull(it).report }

        // USB HID key-down reports for a Swiss host: circumflex + Space, then the text.
        // Space commits the dead key without adding a space to the resulting text.
        assertEquals(
            listOf(0 to 0x2e, 0 to 0x2c, 2 to 0x08),
            reports.filter { it[2] != 0.toByte() }.map { it[0].toInt() to it[2].toInt() }
        )
        // Every key-down is followed by a key-up, including the dead key and its Space.
        reports.forEachIndexed { index, report ->
            if (report[2] != 0.toByte()) assertEquals(0, reports[index + 1][2].toInt())
        }
        assertEquals(0, reports.last()[0].toInt())
    }
}
