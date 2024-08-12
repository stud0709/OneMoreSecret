package com.onemoresecret.bt;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@JsonSerialize(using = KeyboardReportSerializer.class)
public class KeyboardReport {
    private final Set<KeyModifier> modifiers = new HashSet<>();
    private final KeyboardUsage usage;

    public static final int
            NUM_LOCK = 1,
            CAPS_LOCK = 1 << 1,
            SCROLL_LOCK = 1 << 2,
            COMPOSE = 1 << 3,
            KANA = 1 << 4;

    /**
     * Report data.
     *
     * @param modifiers Use {@link KeyModifier#value} combined with boolean AND, e.g. {@code LEFT_CONTROL.value & LEFT_SHIFT.value }
     * @param usage       Physical key according to USB HID Usage Tables.
     */
    public KeyboardReport(KeyboardUsage usage, KeyModifier... modifiers) {
        this.modifiers.addAll(Arrays.asList(modifiers));
        this.usage = usage;
    }

    public KeyboardReport(KeyboardUsage usage) {
        this.usage = usage;
    }

    /**
     * Keyboard report. byte[0] = modifiers, byte[1] = keyboard usage (i.e. key which is currently pressed)
     * @return Keyboard report
     */
    public byte[] getReport() {
        byte mByte = 0;
        for(var m : modifiers) {
            mByte |= m.value;
        }
        return new byte[] {mByte, usage.value};
    }

    public Set<KeyModifier> getModifiers() {
        return modifiers;
    }

    public KeyboardUsage getUsage() {
        return usage;
    }
}
