package com.onemoresecret.bt.layout;

import static com.onemoresecret.bt.KeyboardReport.LEFT_SHIFT;
import static com.onemoresecret.bt.KeyboardReport.RIGHT_ALT;

import com.onemoresecret.bt.KeyboardReport;

public class GermanLayout extends USLayout {

    public GermanLayout() {
        layout.put('y', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_Z)});
        layout.put('z', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_Y)});

        layout.put('ß', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_MINUS)});

        layout.put('"', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_2)});
        layout.put('§', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_3)});
        layout.put('&', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_6)});
        layout.put('/', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_7)});
        layout.put('(', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_8)});
        layout.put(')', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_9)});
        layout.put('=', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_0)});
        layout.put('?', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_MINUS)});

        layout.put('{', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_7)});
        layout.put('[', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_8)});
        layout.put(']', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_9)});
        layout.put('}', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_0)});
        layout.put('\\', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_MINUS)});
        layout.put('²', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_2)});
        layout.put('³', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_3)});
        layout.put('µ', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_M)});
        layout.put('@', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_Q)});
        layout.put('€', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_E)});

        layout.put('<', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_BACKSLASH)});
        layout.put('>', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_BACKSLASH)});
        layout.put('|', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_BACKSLASH)});

        layout.put(';', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_COMMA)});

        layout.put(':', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_DOT)});

        layout.put('-', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_SLASH)});
        layout.put('_', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_SLASH)});

        layout.put('´', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_SPACE)});
        layout.put('`', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_SPACE)});

        layout.put('#', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_BACKSLASH)});
        layout.put('\'', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_BACKSLASH)});

        layout.put('ü', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_LEFTBRACE)});
        layout.put('ö', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_SEMICOLON)});
        layout.put('ä', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_APOSTROPHE)});

        layout.put('+', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_RIGHTBRACE)});
        layout.put('*', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_RIGHTBRACE)});
        layout.put('~', new KeyboardReport[]{new KeyboardReport(RIGHT_ALT, KeyboardUsage.KBD_RIGHTBRACE)});

        layout.put('^', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_GRAVE), new KeyboardReport(0, KeyboardUsage.KBD_SPACE)});
        layout.put('°', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_GRAVE)});

        layout.put('â', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_GRAVE), new KeyboardReport(0, KeyboardUsage.KBD_A)});
        layout.put('ê', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_GRAVE), new KeyboardReport(0, KeyboardUsage.KBD_E)});
        layout.put('î', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_GRAVE), new KeyboardReport(0, KeyboardUsage.KBD_I)});
        layout.put('ô', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_GRAVE), new KeyboardReport(0, KeyboardUsage.KBD_O)});
        layout.put('û', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_GRAVE), new KeyboardReport(0, KeyboardUsage.KBD_U)});

        layout.put('á', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_A)});
        layout.put('é', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_E)});
        layout.put('í', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_I)});
        layout.put('ó', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_O)});
        layout.put('ú', new KeyboardReport[]{new KeyboardReport(0, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_U)});

        layout.put('à', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_A)});
        layout.put('è', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_E)});
        layout.put('ì', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_I)});
        layout.put('ò', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_O)});
        layout.put('ù', new KeyboardReport[]{new KeyboardReport(LEFT_SHIFT, KeyboardUsage.KBD_EQUAL), new KeyboardReport(0, KeyboardUsage.KBD_U)});
    }

}
