package com.onemoresecret.bt.layout;

import static com.onemoresecret.bt.KeyboardReport.RIGHT_ALT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class KeyboardLayout {
    protected final Map<Character, Stroke> layout = new HashMap<>();
    public static final Class<?>[] knownSubclasses = {USLayout.class, GermanLayout.class};

    protected abstract Stroke forKey(char c);

    /**
     * Convert a {@link String} to key strokes
     *
     * @param s text to be converted into key strokes
     * @return key strokes
     */
    public List<Stroke> forString(String s) {
        char[] cArr = s.toCharArray();
        List<Stroke> list = new ArrayList<>();
        list.add(new Stroke()
                .press(RIGHT_ALT) //work around Hyper-V
                ); //all keys up

        for (char c : cArr) {
            list.add(forKey(c));
        }

        return list;
    }
}
