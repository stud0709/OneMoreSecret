package com.onemoresecret.bt.layout;

import static com.onemoresecret.bt.KeyModifier.LEFT_SHIFT;

import android.util.Log;

import androidx.annotation.NonNull;

import com.onemoresecret.bt.KeyboardUsage;

/**
 * US Keyboard Layout
 */
public class USLayout extends KeyboardLayout {
    private static final String TAG = USLayout.class.getSimpleName();

    public USLayout() {
        layout.put('a', new Stroke()
                .type(KeyboardUsage.KBD_A));
        layout.put('b', new Stroke()
                .type(KeyboardUsage.KBD_B));
        layout.put('c', new Stroke()
                .type(KeyboardUsage.KBD_C));
        layout.put('d', new Stroke()
                .type(KeyboardUsage.KBD_D));
        layout.put('e', new Stroke()
                .type(KeyboardUsage.KBD_E));
        layout.put('f', new Stroke()
                .type(KeyboardUsage.KBD_F));
        layout.put('g', new Stroke()
                .type(KeyboardUsage.KBD_G));
        layout.put('h', new Stroke()
                .type(KeyboardUsage.KBD_H));
        layout.put('i', new Stroke()
                .type(KeyboardUsage.KBD_I));
        layout.put('j', new Stroke()
                .type(KeyboardUsage.KBD_J));
        layout.put('k', new Stroke()
                .type(KeyboardUsage.KBD_K));
        layout.put('l', new Stroke()
                .type(KeyboardUsage.KBD_L));
        layout.put('m', new Stroke()
                .type(KeyboardUsage.KBD_M));
        layout.put('n', new Stroke()
                .type(KeyboardUsage.KBD_N));
        layout.put('o', new Stroke()
                .type(KeyboardUsage.KBD_O));
        layout.put('p', new Stroke()
                .type(KeyboardUsage.KBD_P));
        layout.put('q', new Stroke()
                .type(KeyboardUsage.KBD_Q));
        layout.put('r', new Stroke()
                .type(KeyboardUsage.KBD_R));
        layout.put('s', new Stroke()
                .type(KeyboardUsage.KBD_S));
        layout.put('t', new Stroke()
                .type(KeyboardUsage.KBD_T));
        layout.put('u', new Stroke()
                .type(KeyboardUsage.KBD_U));
        layout.put('v', new Stroke()
                .type(KeyboardUsage.KBD_V));
        layout.put('w', new Stroke()
                .type(KeyboardUsage.KBD_W));
        layout.put('x', new Stroke()
                .type(KeyboardUsage.KBD_X));
        layout.put('y', new Stroke()
                .type(KeyboardUsage.KBD_Y));
        layout.put('z', new Stroke()
                .type(KeyboardUsage.KBD_Z));

        layout.put('1', new Stroke()
                .type(KeyboardUsage.KBD_1));
        layout.put('2', new Stroke()
                .type(KeyboardUsage.KBD_2));
        layout.put('3', new Stroke()
                .type(KeyboardUsage.KBD_3));
        layout.put('4', new Stroke()
                .type(KeyboardUsage.KBD_4));
        layout.put('5', new Stroke()
                .type(KeyboardUsage.KBD_5));
        layout.put('6', new Stroke()
                .type(KeyboardUsage.KBD_6));
        layout.put('7', new Stroke()
                .type(KeyboardUsage.KBD_7));
        layout.put('8', new Stroke()
                .type(KeyboardUsage.KBD_8));
        layout.put('9', new Stroke()
                .type(KeyboardUsage.KBD_9));
        layout.put('0', new Stroke()
                .type(KeyboardUsage.KBD_0));

        layout.put('!', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_1));
        layout.put('@', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_2));
        layout.put('#', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_3));
        layout.put('$', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_4));
        layout.put('%', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_5));
        layout.put('^', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_6));
        layout.put('&', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_7));
        layout.put('*', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_8));
        layout.put('(', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_9));
        layout.put(')', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_0));

        layout.put('\\', new Stroke()
                .type(KeyboardUsage.KBD_BACKSLASH));
        layout.put('|', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_BACKSLASH));

        layout.put('=', new Stroke()
                .type(KeyboardUsage.KBD_EQUAL));
        layout.put('+', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_EQUAL));

        layout.put(',', new Stroke()
                .type(KeyboardUsage.KBD_COMMA));
        layout.put('<', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_COMMA));

        layout.put('.', new Stroke()
                .type(KeyboardUsage.KBD_DOT));
        layout.put('>', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_DOT));

        layout.put('/', new Stroke()
                .type(KeyboardUsage.KBD_SLASH));
        layout.put('?', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_SLASH));

        layout.put(';', new Stroke()
                .type(KeyboardUsage.KBD_SEMICOLON));
        layout.put(':', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_SEMICOLON));

        layout.put('\'', new Stroke()
                .type(KeyboardUsage.KBD_APOSTROPHE));
        layout.put('"', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_APOSTROPHE));

        layout.put('[', new Stroke()
                .type(KeyboardUsage.KBD_LEFTBRACE));
        layout.put('{', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_LEFTBRACE));

        layout.put(']', new Stroke()
                .type(KeyboardUsage.KBD_RIGHTBRACE));
        layout.put('}', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_RIGHTBRACE));

        layout.put('-', new Stroke()
                .type(KeyboardUsage.KBD_MINUS));
        layout.put('_', new Stroke()
                .press(LEFT_SHIFT)
                .type(KeyboardUsage.KBD_MINUS));

        layout.put('\t', new Stroke()
                .type(KeyboardUsage.KBD_TAB));
        layout.put(' ', new Stroke()
                .type(KeyboardUsage.KBD_SPACE));
        layout.put('\r', new Stroke()); //will be ignored
        layout.put('\n', new Stroke()
                .type(KeyboardUsage.KBD_ENTER));
    }

    @Override
    public Stroke forKey(char c) {
        var s = layout.get(c);

        if (s == null) {
            //lookup lower case character and set it uppercase
            var cLower = Character.toLowerCase(c);
            s = layout.get(cLower);

            if (s != null) {
                s = s.toUpper();
                if (s == null)
                    Log.e(TAG, "Cannot capitalize " + c + ", create dedicated Stroke definition");
                return s;
            }
        } else {
            return s;
        }

        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return "English (US)";
    }
}
