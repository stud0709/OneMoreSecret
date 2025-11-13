package com.onemoresecret.bt.layout

import com.onemoresecret.bt.KeyModifier
import com.onemoresecret.bt.KeyboardUsage

/**
 * Swiss keyboard layout.
 */
class SwissLayout : GermanLayout() {
    init {
        layout['§'] = Stroke()
            .type(KeyboardUsage.KBD_GRAVE)
        layout['°'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_GRAVE)

        layout['+'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_1)
        layout['¦'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_1)

        layout['@'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_2)

        layout['*'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_3)
        layout['#'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_3)

        layout['ç'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_3)

        layout['¬'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_7)
        layout['|'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_7)

        layout['¢'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_8)

        layout['\''] = Stroke()
            .type(KeyboardUsage.KBD_MINUS)
        layout['´'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_MINUS)
            .clear()
            .type(KeyboardUsage.KBD_SPACE)
        layout['?'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_MINUS)

        layout['^'] = Stroke()
            .type(KeyboardUsage.KBD_GRAVE, KeyboardUsage.KBD_EQUAL)
        layout['~'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_EQUAL)

        layout['ü'] = Stroke()
            .type(KeyboardUsage.KBD_LEFTBRACE)
        layout['è'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_LEFTBRACE)
        layout['Ü'] = Stroke()
            .type(KeyboardUsage.KBD_CAPSLOCK)
            .type(KeyboardUsage.KBD_LEFTBRACE)
            .type(KeyboardUsage.KBD_LEFTBRACE)
        layout['È'] = Stroke()
            .type(KeyboardUsage.KBD_CAPSLOCK)
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_LEFTBRACE)
            .type(KeyboardUsage.KBD_CAPSLOCK)

        layout['¨'] = Stroke()
            .type(KeyboardUsage.KBD_RIGHTBRACE)
            .type(KeyboardUsage.KBD_SPACE)
        layout['!'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_RIGHTBRACE)

        layout['ö'] = Stroke()
            .type(KeyboardUsage.KBD_SEMICOLON)
        layout['é'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_SEMICOLON)
        layout['Ö'] = Stroke()
            .type(KeyboardUsage.KBD_CAPSLOCK)
            .type(KeyboardUsage.KBD_SEMICOLON)
            .type(KeyboardUsage.KBD_LEFTBRACE)
        layout['É'] = Stroke()
            .type(KeyboardUsage.KBD_CAPSLOCK)
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_SEMICOLON)
            .type(KeyboardUsage.KBD_CAPSLOCK)

        layout['ä'] = Stroke()
            .type(KeyboardUsage.KBD_APOSTROPHE)
        layout['à'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_APOSTROPHE)
        layout['Ä'] = Stroke()
            .type(KeyboardUsage.KBD_CAPSLOCK)
            .type(KeyboardUsage.KBD_APOSTROPHE)
            .type(KeyboardUsage.KBD_LEFTBRACE)
        layout['À'] = Stroke()
            .type(KeyboardUsage.KBD_CAPSLOCK)
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_APOSTROPHE)
            .type(KeyboardUsage.KBD_CAPSLOCK)
        layout['{'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_APOSTROPHE)

        layout['$'] = Stroke()
            .type(KeyboardUsage.KBD_BACKSLASH)
        layout['£'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_BACKSLASH)
        layout['}'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_BACKSLASH)

        layout['<'] = Stroke()
            .type(KeyboardUsage.KBD_NON_US_BACKSLASH)
        layout['>'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_NON_US_BACKSLASH)
        layout['\\'] = Stroke()
            .press(KeyModifier.RIGHT_ALT)
            .type(KeyboardUsage.KBD_NON_US_BACKSLASH)

        remove('ß', '²', '³', 'µ')
    }

    override fun toString(): String {
        return "German (Switzerland)"
    }
}
