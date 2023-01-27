package com.onemoresecret.bt.layout;

import static com.onemoresecret.bt.KeyboardReport.LEFT_SHIFT;
import static com.onemoresecret.bt.KeyboardReport.RIGHT_ALT;

import com.onemoresecret.bt.KeyboardReport;

/**
 * German keyboard layout. Typing into Hyper-V causes issues for keys with RIGHT_ALT.
 * The workaround was (1) introduce delay between key strokes and (2) pre-press RIGHT_ALT key.
 */
public class GermanLayout extends USLayout {

    public GermanLayout() {
        layout.put('y', new Stroke().type(KeyboardUsage.KBD_Z));
        layout.put('z', new Stroke().type(KeyboardUsage.KBD_Y));

        layout.put('ß', new Stroke().type(KeyboardUsage.KBD_MINUS));
        layout.put('?', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_MINUS));

        layout.put('"', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_2));
        layout.put('§', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_3));
        layout.put('&', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_6));
        layout.put('/', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_7));
        layout.put('(', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_8));
        layout.put(')', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_9));
        layout.put('=', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_0));

        layout.put('{', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_7));
        layout.put('[', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_8));
        layout.put(']', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_9));
        layout.put('}', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_0));
        layout.put('\\', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_MINUS));
        layout.put('²', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_2));
        layout.put('³', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_3));
        layout.put('µ', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_M));
        layout.put('@', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_Q));
        layout.put('€', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_E));

        layout.put('<', new Stroke().type(KeyboardUsage.KBD_NON_US_BACKSLASH));
        layout.put('>', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_NON_US_BACKSLASH));
        layout.put('|', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_NON_US_BACKSLASH));

        layout.put(';', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_COMMA));

        layout.put(':', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_DOT));

        layout.put('-', new Stroke().type(KeyboardUsage.KBD_SLASH));
        layout.put('_', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_SLASH));

        layout.put('´', new Stroke().type(KeyboardUsage.KBD_EQUAL, KeyboardUsage.KBD_SPACE));
        layout.put('`', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_EQUAL).clear().type(KeyboardUsage.KBD_SPACE));

        layout.put('#', new Stroke().type(KeyboardUsage.KBD_HASHTILDE));
        layout.put('\'', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_HASHTILDE));

        layout.put('ü', new Stroke().type(KeyboardUsage.KBD_LEFTBRACE));
        layout.put('ö', new Stroke().type(KeyboardUsage.KBD_SEMICOLON));
        layout.put('ä', new Stroke().type(KeyboardUsage.KBD_APOSTROPHE));

        layout.put('+', new Stroke().type(KeyboardUsage.KBD_RIGHTBRACE));
        layout.put('*', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_RIGHTBRACE));
        layout.put('~', new Stroke().press(RIGHT_ALT).type(KeyboardUsage.KBD_RIGHTBRACE));

        layout.put('^', new Stroke().type(KeyboardUsage.KBD_GRAVE, KeyboardUsage.KBD_SPACE));
        layout.put('°', new Stroke().press(LEFT_SHIFT).type(KeyboardUsage.KBD_GRAVE));
    }

    @Override
    public String toString() {
        return "German (Germany)";
    }
}
