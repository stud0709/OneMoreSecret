package com.onemoresecret.bt.layout;

import static com.onemoresecret.bt.KeyModifier.LEFT_SHIFT;
import static com.onemoresecret.bt.KeyModifier.RIGHT_ALT;

import androidx.annotation.NonNull;

import com.onemoresecret.bt.KeyboardUsage;

/**
 * Swiss keyboard layout.
 */
public class SwissLayout extends GermanLayout {

    public SwissLayout() {
        layout.put('§', new Stroke()
                .type(KeyboardUsage.KBD_GRAVE));
        layout.put('°', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_GRAVE));

        layout.put('+', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_1));
        layout.put('¦', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_1));

        layout.put('@', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_2));

        layout.put('*', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_3));
        layout.put('#', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_3));

        layout.put('ç', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_3));

        layout.put('¬', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_7));
        layout.put('|', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_7));

        layout.put('¢', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_8));

        layout.put('\'', new Stroke()
                .type(KeyboardUsage.KBD_MINUS));
        layout.put('´', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_MINUS)
                .clear()
                .type(KeyboardUsage.KBD_SPACE));
        layout.put('?', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_MINUS));

        layout.put('^', new Stroke()
                .type(KeyboardUsage.KBD_GRAVE, KeyboardUsage.KBD_EQUAL));
        layout.put('~', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_EQUAL));

        layout.put('ü', new Stroke()
                .type(KeyboardUsage.KBD_LEFTBRACE));
        layout.put('è', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_LEFTBRACE));
        layout.put('Ü', new Stroke()
                .type(KeyboardUsage.KBD_CAPSLOCK)
                .type(KeyboardUsage.KBD_LEFTBRACE)
                .type(KeyboardUsage.KBD_LEFTBRACE));
        layout.put('È', new Stroke()
                .type(KeyboardUsage.KBD_CAPSLOCK)
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_LEFTBRACE)
                .type(KeyboardUsage.KBD_CAPSLOCK));

        layout.put('¨', new Stroke()
                .type(KeyboardUsage.KBD_RIGHTBRACE)
                .type(KeyboardUsage.KBD_SPACE));
        layout.put('!', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_RIGHTBRACE));

        layout.put('ö', new Stroke()
                .type(KeyboardUsage.KBD_SEMICOLON));
        layout.put('é', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_SEMICOLON));
        layout.put('Ö', new Stroke()
                .type(KeyboardUsage.KBD_CAPSLOCK)
                .type(KeyboardUsage.KBD_SEMICOLON)
                .type(KeyboardUsage.KBD_LEFTBRACE));
        layout.put('É', new Stroke()
                .type(KeyboardUsage.KBD_CAPSLOCK)
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_SEMICOLON)
                .type(KeyboardUsage.KBD_CAPSLOCK));

        layout.put('ä', new Stroke()
                .type(KeyboardUsage.KBD_APOSTROPHE));
        layout.put('à', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_APOSTROPHE));
        layout.put('Ä', new Stroke()
                .type(KeyboardUsage.KBD_CAPSLOCK)
                .type(KeyboardUsage.KBD_APOSTROPHE)
                .type(KeyboardUsage.KBD_LEFTBRACE));
        layout.put('À', new Stroke()
                .type(KeyboardUsage.KBD_CAPSLOCK)
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_APOSTROPHE)
                .type(KeyboardUsage.KBD_CAPSLOCK));
        layout.put('{', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_APOSTROPHE));

        layout.put('$', new Stroke()
                .type(KeyboardUsage.KBD_BACKSLASH));
        layout.put('£', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_BACKSLASH));
        layout.put('}', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_BACKSLASH));

        layout.put('<', new Stroke()
                .type(KeyboardUsage.KBD_NON_US_BACKSLASH));
        layout.put('>', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_NON_US_BACKSLASH));
        layout.put('\\', new Stroke()
                .press(RIGHT_ALT)
                .type(KeyboardUsage.KBD_NON_US_BACKSLASH));

        remove('ß', '²', '³', 'µ');
    }

    @NonNull
    @Override
    public String toString() {
        return "German (Switzerland)";
    }
}
