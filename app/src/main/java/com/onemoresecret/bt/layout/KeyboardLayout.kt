package com.onemoresecret.bt.layout;

import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.onemoresecret.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public abstract class KeyboardLayout {
    protected final Map<Character, Stroke> layout = new TreeMap<>();
    public static final Class<?>[] knownSubclasses = {USLayout.class, GermanLayout.class, SwissLayout.class};

    public abstract Stroke forKey(char c);

    /**
     * Convert a {@link String} to key strokes
     *
     * @param s text to be converted into key strokes
     * @return key strokes
     */
    public List<Stroke> forString(String s) {
        var cArr = s.toCharArray();
        List<Stroke> list = new ArrayList<>();

        for (char c : cArr) {
            list.add(forKey(c));
        }

        return list;
    }

    /**
     * Remove {@link Stroke}s associated with the parent layout, but not present in the current one.
     * @param cArr characters to remove
     */
    protected void remove(char... cArr) {
        List<Character> cList = new ArrayList<>();
        for(var c : cArr) {
            cList.add(c);
        }
        var toBeRemoved = cList.stream().filter(layout::containsKey).collect(Collectors.toSet());
        toBeRemoved.forEach(layout::remove);
    }

    public void logLayout() {
        layout.entrySet().forEach(entry -> {
            try {
                Log.d(getClass().getName(), Util.JACKSON_MAPPER.writeValueAsString(entry));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });
    }
}
