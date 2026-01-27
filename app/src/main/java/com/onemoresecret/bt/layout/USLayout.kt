package com.onemoresecret.bt.layout

import android.util.Log
import com.onemoresecret.bt.KeyModifier
import com.onemoresecret.bt.KeyboardUsage

/**
 * US Keyboard Layout
 */
open class USLayout : KeyboardLayout() {
    init {
        layout['a'] = Stroke()
            .type(KeyboardUsage.KBD_A)
        layout['b'] = Stroke()
            .type(KeyboardUsage.KBD_B)
        layout['c'] = Stroke()
            .type(KeyboardUsage.KBD_C)
        layout['d'] = Stroke()
            .type(KeyboardUsage.KBD_D)
        layout['e'] = Stroke()
            .type(KeyboardUsage.KBD_E)
        layout['f'] = Stroke()
            .type(KeyboardUsage.KBD_F)
        layout['g'] = Stroke()
            .type(KeyboardUsage.KBD_G)
        layout['h'] = Stroke()
            .type(KeyboardUsage.KBD_H)
        layout['i'] = Stroke()
            .type(KeyboardUsage.KBD_I)
        layout['j'] = Stroke()
            .type(KeyboardUsage.KBD_J)
        layout['k'] = Stroke()
            .type(KeyboardUsage.KBD_K)
        layout['l'] = Stroke()
            .type(KeyboardUsage.KBD_L)
        layout['m'] = Stroke()
            .type(KeyboardUsage.KBD_M)
        layout['n'] = Stroke()
            .type(KeyboardUsage.KBD_N)
        layout['o'] = Stroke()
            .type(KeyboardUsage.KBD_O)
        layout['p'] = Stroke()
            .type(KeyboardUsage.KBD_P)
        layout['q'] = Stroke()
            .type(KeyboardUsage.KBD_Q)
        layout['r'] = Stroke()
            .type(KeyboardUsage.KBD_R)
        layout['s'] = Stroke()
            .type(KeyboardUsage.KBD_S)
        layout['t'] = Stroke()
            .type(KeyboardUsage.KBD_T)
        layout['u'] = Stroke()
            .type(KeyboardUsage.KBD_U)
        layout['v'] = Stroke()
            .type(KeyboardUsage.KBD_V)
        layout['w'] = Stroke()
            .type(KeyboardUsage.KBD_W)
        layout['x'] = Stroke()
            .type(KeyboardUsage.KBD_X)
        layout['y'] = Stroke()
            .type(KeyboardUsage.KBD_Y)
        layout['z'] = Stroke()
            .type(KeyboardUsage.KBD_Z)

        layout['1'] = Stroke()
            .type(KeyboardUsage.KBD_1)
        layout['2'] = Stroke()
            .type(KeyboardUsage.KBD_2)
        layout['3'] = Stroke()
            .type(KeyboardUsage.KBD_3)
        layout['4'] = Stroke()
            .type(KeyboardUsage.KBD_4)
        layout['5'] = Stroke()
            .type(KeyboardUsage.KBD_5)
        layout['6'] = Stroke()
            .type(KeyboardUsage.KBD_6)
        layout['7'] = Stroke()
            .type(KeyboardUsage.KBD_7)
        layout['8'] = Stroke()
            .type(KeyboardUsage.KBD_8)
        layout['9'] = Stroke()
            .type(KeyboardUsage.KBD_9)
        layout['0'] = Stroke()
            .type(KeyboardUsage.KBD_0)

        layout['!'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_1)
        layout['@'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_2)
        layout['#'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_3)
        layout['$'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_4)
        layout['%'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_5)
        layout['^'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_6)
        layout['&'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_7)
        layout['*'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_8)
        layout['('] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_9)
        layout[')'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_0)

        layout['\\'] = Stroke()
            .type(KeyboardUsage.KBD_BACKSLASH)
        layout['|'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_BACKSLASH)

        layout['='] = Stroke()
            .type(KeyboardUsage.KBD_EQUAL)
        layout['+'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_EQUAL)

        layout[','] = Stroke()
            .type(KeyboardUsage.KBD_COMMA)
        layout['<'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_COMMA)

        layout['.'] = Stroke()
            .type(KeyboardUsage.KBD_DOT)
        layout['>'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_DOT)

        layout['/'] = Stroke()
            .type(KeyboardUsage.KBD_SLASH)
        layout['?'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_SLASH)

        layout[';'] = Stroke()
            .type(KeyboardUsage.KBD_SEMICOLON)
        layout[':'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_SEMICOLON)

        layout['\''] = Stroke()
            .type(KeyboardUsage.KBD_APOSTROPHE)
        layout['"'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_APOSTROPHE)

        layout['['] = Stroke()
            .type(KeyboardUsage.KBD_LEFTBRACE)
        layout['{'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_LEFTBRACE)

        layout[']'] = Stroke()
            .type(KeyboardUsage.KBD_RIGHTBRACE)
        layout['}'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_RIGHTBRACE)

        layout['-'] = Stroke()
            .type(KeyboardUsage.KBD_MINUS)
        layout['_'] = Stroke()
            .press(KeyModifier.LEFT_SHIFT)
            .type(KeyboardUsage.KBD_MINUS)

        layout['\t'] = Stroke()
            .type(KeyboardUsage.KBD_TAB)
        layout[' '] = Stroke()
            .type(KeyboardUsage.KBD_SPACE)
        layout['\r'] = Stroke() //will be ignored
        layout['\n'] = Stroke()
            .type(KeyboardUsage.KBD_ENTER)
    }

    override fun forKey(c: Char): Stroke? {
        var s = layout[c]

        if (s == null) {
            //lookup lower case character and set it uppercase
            val cLower = c.lowercaseChar()
            s = layout[cLower]

            if (s != null) {
                s = s.toUpper()
                if (s == null) Log.e(
                    TAG,
                    "Cannot capitalize $c, create dedicated Stroke definition"
                )
                return s
            }
        } else {
            return s
        }

        return null
    }

    override fun toString(): String {
        return "English (US)"
    }

    companion object {
        private val TAG: String = USLayout::class.java.getSimpleName()
    }
}
