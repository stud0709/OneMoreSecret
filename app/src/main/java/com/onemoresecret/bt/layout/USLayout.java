package com.onemoresecret.bt.layout;

import static com.onemoresecret.bt.KeyboardReport.LEFT_SHIFT;

import com.onemoresecret.bt.KeyboardReport;

public class USLayout extends KeyboardLayout {

    public USLayout() {
        layout.put('a', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_A)});
        layout.put('b', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_B)});
        layout.put('c', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_C)});
        layout.put('d', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_D)});
        layout.put('e', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_E)});
        layout.put('f', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_F)});
        layout.put('g', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_G)});
        layout.put('h', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_H)});
        layout.put('i', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_I)});
        layout.put('j', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_J)});
        layout.put('k', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_K)});
        layout.put('l', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_L)});
        layout.put('m', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_M)});
        layout.put('n', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_N)});
        layout.put('o', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_O)});
        layout.put('p', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_P)});
        layout.put('q', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_Q)});
        layout.put('r', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_R)});
        layout.put('s', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_S)});
        layout.put('t', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_T)});
        layout.put('u', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_U)});
        layout.put('v', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_V)});
        layout.put('w', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_W)});
        layout.put('x', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_X)});
        layout.put('y', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_Y)});
        layout.put('z', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_Z)});

        layout.put('1', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_1)});
        layout.put('2', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_2)});
        layout.put('3', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_3)});
        layout.put('4', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_4)});
        layout.put('5', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_5)});
        layout.put('6', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_6)});
        layout.put('7', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_7)});
        layout.put('8', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_8)});
        layout.put('9', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_9)});
        layout.put('0', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_0)});

        layout.put('!', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_1)});
        layout.put('@', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_2)});
        layout.put('#', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_3)});
        layout.put('$', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_4)});
        layout.put('%', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_5)});
        layout.put('^', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_6)});
        layout.put('&', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_7)});
        layout.put('*', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_8)});
        layout.put('(', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_9)});
        layout.put(')', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_0)});

        layout.put('\\', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_BACKSLASH)});
        layout.put('|', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_BACKSLASH)});

        layout.put('=', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_EQUAL)});
        layout.put('+', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_EQUAL)});

        layout.put(',', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_COMMA)});
        layout.put('<', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_COMMA)});

        layout.put('.', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_DOT)});
        layout.put('>', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_DOT)});

        layout.put('/', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_SLASH)});
        layout.put('?', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_SLASH)});

        layout.put(';', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_SEMICOLON)});
        layout.put(':', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_SEMICOLON)});

        layout.put('\'', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_APOSTROPHE)});
        layout.put('"', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_APOSTROPHE)});

        layout.put('\\', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_BACKSLASH)});
        layout.put('|', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_BACKSLASH)});

        layout.put('[', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_LEFTBRACE)});
        layout.put('{', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_LEFTBRACE)});

        layout.put(']', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_RIGHTBRACE)});
        layout.put('}', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_RIGHTBRACE)});

        layout.put('-', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_MINUS)});
        layout.put('_', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_MINUS)});

        layout.put('\t', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_TAB)});
        layout.put(' ', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_SPACE)});
        layout.put('\r', new KeyboardReport[]{});
        layout.put('\n', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_ENTER)});
    }

    @Override
    public KeyboardReport[] forKey(char c) {
        KeyboardReport[] r = layout.get(c);
        if (r == null) {
            r = layout.get(Character.toLowerCase(c));
            if (r != null && r.length == 1) {
                return new KeyboardReport[]{r[0].addModifier(LEFT_SHIFT)};
            }
        }

        return r;
    }

    @Override
    public String toString() {
        return "English (US)";
    }
}
