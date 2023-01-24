package com.onemoresecret.bt.layout;

import static com.onemoresecret.bt.layout.KeyboardUsage.KBD_NONE;

import android.util.Log;

import com.onemoresecret.bt.KeyboardReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class KeyboardLayout {
    protected final Map<Character, KeyboardReport[]> layout = new HashMap<>();
    public static final Class<?> knownSubclasses[] = {USLayout.class, GermanLayout.class};

    protected abstract KeyboardReport[] forKey(char c);

    public Map<Character, KeyboardReport[]> getLayout() {
        return Collections.unmodifiableMap(layout);
    }

    /**
     * Convert a {@link String} to key strokes
     *
     * @param s
     * @return
     */
    public List<KeyboardReport[]> forString(String s) {
        char cArr[] = s.toCharArray();
        List<KeyboardReport[]> list = new ArrayList<>();
        for (char c : cArr) {
            list.add(forKey(c));
            list.add(new KeyboardReport[]{new KeyboardReport((byte) 0, KBD_NONE)}); //key up
        }

        return list;
    }

    public static final List<KeyboardReport[]> forKey(int keyboardUsage) {
        List<KeyboardReport[]> list = new ArrayList<>();
        list.add(new KeyboardReport[]{new KeyboardReport((byte) 0, keyboardUsage)});
        list.add(new KeyboardReport[]{new KeyboardReport((byte) 0, KBD_NONE)}); //key up

        return list;
    }
}
