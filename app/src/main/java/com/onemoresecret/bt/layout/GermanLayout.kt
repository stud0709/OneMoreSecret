package com.onemoresecret.bt.layout

import com.onemoresecret.bt.KeyModifier
import com.onemoresecret.bt.KeyboardUsage

/**
 * German keyboard layout.
 */
open class GermanLayout : USLayout() {
    init {
        layout['y'] = Stroke()
            .type(KeyboardUsage.KBD_Z)
        layout['z'] = Stroke()
            .type(KeyboardUsage.KBD_Y)

        layout['ß'] = Stroke()
            .type(KeyboardUsage.KBD_MINUS)
        layout['?'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_MINUS)

        layout['"'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_2)
        layout['§'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_3)
        layout['&'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_6)
        layout['/'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_7)
        layout['('] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_8)
        layout[')'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_9)
        layout['='] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_0)

        layout['{'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_7)
        layout['['] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_8)
        layout[']'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_9)
        layout['}'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_0)
        layout['\\'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_MINUS)
        layout['²'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_2)
        layout['³'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_3)
        layout['µ'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_M)
        layout['@'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_Q)
        layout['€'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_E)

        layout['<'] = Stroke()
            .type(KeyboardUsage.KBD_NON_US_BACKSLASH)
        layout['>'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_NON_US_BACKSLASH)
        layout['|'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_NON_US_BACKSLASH)

        layout[';'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_COMMA)

        layout[':'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_DOT)

        layout['-'] = Stroke()
            .type(KeyboardUsage.KBD_SLASH)
        layout['_'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_SLASH)

        layout['´'] = Stroke()
            .type(KeyboardUsage.KBD_EQUAL, KeyboardUsage.KBD_SPACE)
        layout['`'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_EQUAL)
            .clear()
            .type(KeyboardUsage.KBD_SPACE)

        layout['#'] = Stroke()
            .type(KeyboardUsage.KBD_HASHTILDE)
        layout['\''] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_HASHTILDE)

        layout['ü'] = Stroke()
            .type(KeyboardUsage.KBD_LEFTBRACE)
        layout['ö'] = Stroke()
            .type(KeyboardUsage.KBD_SEMICOLON)
        layout['ä'] = Stroke()
            .type(KeyboardUsage.KBD_APOSTROPHE)

        layout['+'] = Stroke()
            .type(KeyboardUsage.KBD_RIGHTBRACE)
        layout['*'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_RIGHTBRACE)
        layout['~'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_RIGHTBRACE)

        layout['^'] = Stroke()
            .type(KeyboardUsage.KBD_GRAVE, KeyboardUsage.KBD_SPACE)
        layout['°'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_GRAVE)
    }

    override fun toString(): String {
        return "German (Germany)"
    }
}
